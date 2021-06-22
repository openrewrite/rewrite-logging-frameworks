package org.openrewrite.java.logging.slf4j;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ParameterizedLogging extends Recipe {

    private static final List<MethodMatcher> logLevelMatchers = Stream.of("trace", "debug", "info", "warn", "error", "fatal")
            .map(level -> "org.slf4j.Logger " + level + "(..)")
            .map(MethodMatcher::new)
            .collect(Collectors.toList());
    private static final ThreadLocal<JavaParser> TEMPLATE_PARSER = ThreadLocal.withInitial(() -> JavaParser.fromJavaVersion().build());

    @Override
    public String getDisplayName() {
        return "Parameterize SLF4J logging statements";
    }

    @Override
    public String getDescription() {
        return "SLF4J supports parameterized logging which can significantly boost logging performance for disabled logging statement.";
    }

    @Override
    protected @Nullable TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new UsesType<>("org.slf4j.Logger");
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                if (logLevelMatchers.stream().anyMatch(it -> it.matches(method)) && method.getArguments().stream().anyMatch(J.Binary.class::isInstance)) {
                    final StringBuilder messageBuilder = new StringBuilder("\"");
                    final List<Expression> newArgList = new ArrayList<>();
                    for (Expression message : method.getArguments()) {
                        if (message instanceof J.Binary) {
                            MessageAndArguments literalAndArgs = concatenationToLiteral(message,
                                    new MessageAndArguments("", new ArrayList<>()));
                            messageBuilder.append(literalAndArgs.message);
                            newArgList.addAll(literalAndArgs.arguments);
                        } else {
                            newArgList.add(message);
                        }
                    }
                    messageBuilder.append("\"");
                    newArgList.forEach(arg -> messageBuilder.append(", #{any()}"));
                    m = m.withTemplate(
                            template(messageBuilder.toString()).javaParser(TEMPLATE_PARSER::get).build(),
                            m.getCoordinates().replaceArguments(),
                            newArgList.toArray()
                    );
                }
                return m;
            }
        };
    }

    private MessageAndArguments concatenationToLiteral(Expression message, MessageAndArguments result) {
        if (!(message instanceof J.Binary)) {
            result.arguments.add(message);
            return result;
        }

        J.Binary concat = (J.Binary) message;
        if (concat.getLeft() instanceof J.Binary) {
            concatenationToLiteral(concat.getLeft(), result);
        } else if (concat.getLeft() instanceof J.Literal) {
            result.message = ((J.Literal) concat.getLeft()).getValue() + result.message;
        } else {
            result.message = "{}" + result.message;
            result.arguments.add(concat.getLeft());
        }

        if (concat.getRight() instanceof J.Binary) {
            concatenationToLiteral(concat.getRight(), result);
        } else if (concat.getRight() instanceof J.Literal) {
            result.message += ((J.Literal) concat.getRight()).getValue();
        } else {
            result.message += "{}";
            result.arguments.add(concat.getRight());
        }

        return result;
    }

    private class MessageAndArguments {
        private final List<Expression> arguments;
        private String message;

        private MessageAndArguments(String message, List<Expression> arguments) {
            this.message = message;
            this.arguments = arguments;
        }
    }
}
