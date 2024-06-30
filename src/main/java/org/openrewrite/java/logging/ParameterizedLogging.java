/*
 * Copyright 2021 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.logging;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.lang.NonNull;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.*;

@Value
@EqualsAndHashCode(callSuper = false)
public class ParameterizedLogging extends Recipe {
    @Option(displayName = "Method pattern",
            description = "A method used to find matching statements to parameterize.",
            example = "org.slf4j.Logger info(..)")
    String methodPattern;

    @Option(displayName = "Remove `Object#toString()` invocations from logging parameters",
            description = "Optionally remove `toString(`) method invocations from Object parameters.",
            required = false
    )
    @Nullable
    Boolean removeToString;

    @Override
    public String getDisplayName() {
        return "Parameterize logging statements";
    }

    @Override
    public String getDescription() {
        return "Transform logging statements using concatenation for messages and variables into a parameterized format. " +
               "For example, `logger.info(\"hi \" + userName)` becomes `logger.info(\"hi {}\", userName)`. This can " +
               "significantly boost performance for messages that otherwise would be assembled with String concatenation. " +
               "Particularly impactful when the log level is not enabled, as no work is done to assemble the message.";
    }

    @Override
    public Set<String> getTags() {
        return new HashSet<>(Arrays.asList("RSPEC-S2629", "RSPEC-S3457"));
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(methodPattern, true), new JavaIsoVisitor<ExecutionContext>() {
            private final MethodMatcher matcher = new MethodMatcher(methodPattern, true);
            private final RemoveToStringVisitor removeToStringVisitor = new RemoveToStringVisitor();

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                if (matcher.matches(m) && !m.getArguments().isEmpty() && !(m.getArguments().get(0) instanceof J.Empty)) {
                    Expression logMsg = m.getArguments().get(0);
                    List<Expression> newArgList = new ArrayList<>();
                    boolean hasMarker = m.getArguments().size() > 1 
                            && m.getArguments().get(1) instanceof J.Identifier 
                            && TypeUtils.isOfType(m.getArguments().get(1).getType(), Marker.class);
                    
                    if (logMsg instanceof J.Binary) {
                        StringBuilder messageBuilder = new StringBuilder("\"");
                        ListUtils.map(m.getArguments(), (index, message) -> {
                            if (index == 0 && message instanceof J.Binary) {
                                MessageAndArguments literalAndArgs = concatenationToLiteral(message, new MessageAndArguments("", new ArrayList<>()));
                                messageBuilder.append(literalAndArgs.message);
                                newArgList.addAll(literalAndArgs.arguments);
                            } else {
                                newArgList.add(message);
                            }
                            return message;
                        });
                        messageBuilder.append("\"");
                        newArgList.forEach(arg -> messageBuilder.append(", #{any()}"));
                        m = JavaTemplate.builder(escapeDollarSign(messageBuilder.toString()))
                                .contextSensitive()
                                .build()
                                .apply(new Cursor(getCursor().getParent(), m), m.getCoordinates().replaceArguments(), newArgList.toArray());
                    } else if (logMsg instanceof J.Identifier && TypeUtils.isAssignableTo("java.lang.Throwable", logMsg.getType()) ) {
                        return m;
                    } else if (!TypeUtils.isString(logMsg.getType()) && logMsg.getType() instanceof JavaType.Class) {
                        StringBuilder messageBuilder = new StringBuilder("\"{}\"");
                        m.getArguments().forEach(arg -> messageBuilder.append(", #{any()}"));
                        m = JavaTemplate.builder(escapeDollarSign(messageBuilder.toString()))
                                .contextSensitive()
                                .build()
                                .apply(new Cursor(getCursor().getParent(), m), m.getCoordinates().replaceArguments(), m.getArguments().toArray());
                    }
                    if (Boolean.TRUE.equals(removeToString)) {
                        m = m.withArguments(ListUtils.map(m.getArguments(), arg -> (Expression) removeToStringVisitor.visitNonNull(arg, ctx, getCursor())));
                    }

                    // Reordering arguments if Marker is present
                    if (hasMarker) {
                        Expression marker = m.getArguments().get(1);
                        newArgList.add(0, marker);
                    }
                }

                // Avoid changing reference if the templating didn't actually change the contents of the method
                if(m != method && m.print(getCursor()).equals(method.print(getCursor()))) {
                    return method;
                }
                return m;
            }

            class RemoveToStringVisitor extends JavaVisitor<ExecutionContext> {
                private final JavaTemplate t = JavaTemplate.builder("#{any(java.lang.String)}").build();
                private final MethodMatcher TO_STRING = new MethodMatcher("java.lang.Object toString()");
                @Override
                public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                    if (getCursor().getNearestMessage("DO_NOT_REMOVE", Boolean.FALSE)) {
                        return method;
                    }
                    if (TO_STRING.matches(method.getSelect())) {
                        getCursor().putMessage("DO_NOT_REMOVE", Boolean.TRUE);
                    } else if (TO_STRING.matches(method)) {
                        return t.apply(getCursor(), method.getCoordinates().replace(), method.getSelect());
                    }
                    return super.visitMethodInvocation(method, ctx);
                }
            }
        });
    }

    private static final class MessageAndArguments {
        private final List<Expression> arguments;
        private String message;

        boolean previousMessageWasStringLiteral;

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
        if (concat.getLeft() instanceof J.Binary && ((J.Binary) concat.getLeft()).getOperator() == J.Binary.Type.Addition) {
            concatenationToLiteral(concat.getLeft(), result);
        } else if (concat.getLeft() instanceof J.Literal) {
            J.Literal left = (J.Literal) concat.getLeft();
            result.message = getLiteralValue(left) + result.message;
            result.previousMessageWasStringLiteral = left.getType() == JavaType.Primitive.String;
        } else {
            result.message = "{}" + result.message;
            result.arguments.add(concat.getLeft());
            result.previousMessageWasStringLiteral = false;
        }

        if (concat.getRight() instanceof J.Binary && ((J.Binary) concat.getRight()).getOperator() == J.Binary.Type.Addition) {
            concatenationToLiteral(concat.getRight(), result);
        } else if (concat.getRight() instanceof J.Literal) {
            J.Literal right = (J.Literal) concat.getRight();
            boolean rightIsStringLiteral = right.getType() == JavaType.Primitive.String;
            if(result.previousMessageWasStringLiteral && rightIsStringLiteral) {
                result.message += "\" +" + right.getPrefix().getWhitespace() + "\"" + getLiteralValue(right);
            } else {
                result.message += getLiteralValue(right);
            }
            result.previousMessageWasStringLiteral = rightIsStringLiteral;
        } else {
            // prevent inadvertently appending {} to # to create #{}, which creates an additional JavaTemplate argument
            if (result.message.endsWith("#")) {
                result.message += "\\";
            }
            result.message += "{}";
            result.arguments.add(concat.getRight());
            result.previousMessageWasStringLiteral = false;
        }

        return result;
    }

    @Nullable
    private static Object getLiteralValue(J.Literal literal) {
        if (literal.getValueSource() == null || literal.getType() != JavaType.Primitive.String) {
            return literal.getValue();
        }
        return literal
                .getValueSource()
                .substring(1, literal.getValueSource().length() - 1)
                .replace("\\", "\\\\");
    }

    private static String escapeDollarSign(@NonNull String value) {
        return value.replaceAll("\\$", "\\\\\\$");
    }
}
