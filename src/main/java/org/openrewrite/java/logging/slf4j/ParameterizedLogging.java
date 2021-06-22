package org.openrewrite.java.logging.slf4j;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaIsoVisitor;
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
    @Override
    public String getDisplayName() {
        return "TODO";
    }

    @Override
    protected @Nullable TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new UsesType<>("org.slf4j.Logger");
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext executionContext) {
                return super.visitCompilationUnit(cu, executionContext);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                if (logLevelMatchers.stream().anyMatch(it -> it.matches(method))) {
                    List<Expression> args = method.getArguments();
                    if (!args.isEmpty()) {
                        Expression message = args.iterator().next();
                        if (message instanceof J.Binary) {
                            MessageAndArguments literalAndArgs = concatenationToLiteral(message,
                                    new MessageAndArguments("", new ArrayList<>()));

                            StringBuilder template = new StringBuilder("\"" + literalAndArgs.message + "\"");
                            literalAndArgs.arguments.forEach(arg -> template.append(", #{any()}"));
                            m = m.withTemplate(
                                    template(template.toString())
                                            .build(),
                                    m.getCoordinates().replaceArguments(),
                                    literalAndArgs.arguments.toArray()
                            );
                        }
                    }
                }
                return m;
            }
        };
    }

    private static class MessageAndArguments {
        private String message;
        private final List<Expression> arguments;

        private MessageAndArguments(String message, List<Expression> arguments) {
            this.message = message;
            this.arguments = arguments;
        }
    }

    private static MessageAndArguments concatenationToLiteral(Expression message, MessageAndArguments result) {
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
}
