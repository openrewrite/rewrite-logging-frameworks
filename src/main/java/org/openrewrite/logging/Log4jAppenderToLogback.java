package org.openrewrite.logging;

import org.openrewrite.Formatting;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.refactor.ChangeType;
import org.openrewrite.java.refactor.ImplementInterface;
import org.openrewrite.java.refactor.JavaRefactorVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.util.Objects;
import java.util.Optional;

import static java.util.stream.Collectors.toList;

public class Log4jAppenderToLogback extends JavaRefactorVisitor {
    private final MethodMatcher format = new MethodMatcher(
            "org.apache.log4j.Layout format(..)");

    @Override
    public String getName() {
        return "logging.Log4jAppenderToLogback";
    }

    @Override
    public J visitClassDecl(J.ClassDecl classDecl) {
        J.ClassDecl c = refactor(classDecl, super::visitClassDecl);

        if (c.getExtends() != null && JavaType.Class.build("org.apache.log4j.AppenderSkeleton")
                .equals(c.getExtends().getFrom().getType())) {
            maybeRemoveImport("org.apache.log4j.AppenderSkeleton");
            maybeAddImport("ch.qos.logback.core.AppenderBase");
            maybeAddImport("ch.qos.logback.classic.spi.ILoggingEvent");

            andThen(new ChangeType("org.apache.log4j.spi.LoggingEvent", "ch.qos.logback.classic.spi.ILoggingEvent"));
            andThen(new ChangeType("org.apache.log4j.Layout", "ch.qos.logback.core.LayoutBase"));

            c = c.withExtends(c.getExtends().withFrom(J.ParameterizedType.build(
                    "ch.qos.logback.core.AppenderBase",
                    "ch.qos.logback.classic.spi.ILoggingEvent").withFormatting(Formatting.format(" "))));
        }

        Optional<J.MethodDecl> requiresLayout = c.getMethods().stream()
                .filter(m -> m.getSimpleName().equals("requiresLayout")).findAny();

        Optional<J.MethodDecl> close = c.getMethods().stream()
                .filter(m -> m.getSimpleName().equals("close")).findAny();

        if (requiresLayout.isPresent() || close.isPresent()) {
            c = c.withBody(c.getBody().withStatements(c.getBody().getStatements().stream()
                    .map(statement -> {
                        if (statement == requiresLayout.orElse(null)) {
                            return null;
                        } else if (statement == close.orElse(null)) {
                            J.MethodDecl closeMethod = (J.MethodDecl) statement;

                            if (closeMethod.getBody() != null && closeMethod.getBody().getStatements().isEmpty()) {
                                return null;
                            }

                            return closeMethod.withName(closeMethod.getName().withName("stop"));
                        }
                        return statement;
                    })
                    .filter(Objects::nonNull)
                    .collect(toList())));
        }

        return c;
    }

    @Override
    public J visitMethodInvocation(J.MethodInvocation method) {
        J.MethodInvocation m = refactor(method, super::visitMethodInvocation);
        if (format.matches(method)) {
            m = m.withName(m.getName().withName("doLayout"));
        }
        return m;
    }
}
