/*
 * Copyright 2022 the original author or authors.
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
package org.openrewrite.java.logging.slf4j;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CompleteExceptionLogging extends Recipe {
    private static final MethodMatcher LOGGER_DEBUG = new MethodMatcher("org.slf4j.Logger debug(..)");
    private static final MethodMatcher LOGGER_ERROR = new MethodMatcher("org.slf4j.Logger error(..)");
    private static final MethodMatcher LOGGER_INFO = new MethodMatcher("org.slf4j.Logger info(..)");
    private static final MethodMatcher LOGGER_TRACE = new MethodMatcher("org.slf4j.Logger trace(..)");
    private static final MethodMatcher LOGGER_WARN = new MethodMatcher("org.slf4j.Logger warn(..)");
    private static final MethodMatcher THROWABLE_GET_MESSAGE = new MethodMatcher("java.lang.Throwable getMessage()");

    @Override
    public String getDisplayName() {
        return "Enhances logging of exceptions by including the full stack trace in addition to the exception message";
    }

    @Override
    public String getDescription() {
        return "It is a common mistake to call Exception.getMessage() when passing an exception into a log method. " +
               "Not all exception types have useful messages, and even if the message is useful this omits the stack " +
               "trace. Including a complete stack trace of the error along with the exception message in the log " +
               "allows developers to better understand the context of the exception and identify the source of the " +
               "error more quickly and accurately.";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method,
                                                            ExecutionContext executionContext) {
                method = super.visitMethodInvocation(method, executionContext);

                if (LOGGER_DEBUG.matches(method) ||
                    LOGGER_ERROR.matches(method) ||
                    LOGGER_INFO.matches(method) ||
                    LOGGER_TRACE.matches(method) ||
                    LOGGER_WARN.matches(method)
                    ) {
                    // Logic:
                    // If there are multiple parameters in a log method, and the last one is `exception.getMessage()`
                    // 1. String contains no format specifiers, replace `exception.getMessage()` with `exception.getMessage()`
                    // 2. String contains format specifiers, count parameters, if the count matches placeholder counts,
                    // append `exception` as a new parameter. otherwise. replace `exception.getMessage()` with `exception`
                    if (method.getArguments().size() <= 1) {
                        return method;
                    }

                    Expression lastParameter = method.getArguments().get(method.getArguments().size() - 1);

                    if (lastParameter instanceof J.MethodInvocation && THROWABLE_GET_MESSAGE.matches(lastParameter)) {
                        J.MethodInvocation getMessageCall = (J.MethodInvocation) lastParameter;

                        Expression firstParameter = method.getArguments().get(0);
                        if (!isStringLiteral(firstParameter)) {
                            return method;
                        }

                        String content = ((J.Literal) firstParameter).getValue().toString();
                        int placeholderCount = countPlaceholders(content);

                        if (placeholderCount >= (method.getArguments().size() - 1)) {
                            // it means the last `Throwable#getMessage()` call is counted for placeholder intentionally,
                            // so we add the exception as a new parameter at the end
                            List<Expression> args = method.getArguments();
                            args.add(getMessageCall.getSelect().withPrefix(getMessageCall.getPrefix()));
                            return method.withArguments(args);
                        } else {
                            // replace `e.getMessage` with `e`.
                            List<Expression> args = method.getArguments();
                            args.set(args.size() - 1,
                                getMessageCall.getSelect().withPrefix(getMessageCall.getPrefix()));
                            return method.withArguments(args);
                        }
                    }
                }

                return method;
            }
        };
    }

    private static int countPlaceholders(String message) {
        int count = 0;
        Pattern pattern = Pattern.compile("\\{}");
        Matcher matcher = pattern.matcher(message);
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private static boolean isStringLiteral(Expression expression) {
        return expression instanceof J.Literal && TypeUtils.isString(((J.Literal) expression).getType());
    }
}
