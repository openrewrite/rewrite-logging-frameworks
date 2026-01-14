/*
 * Copyright 2024 the original author or authors.
 * <p>
 * Licensed under the Moderne Source Available License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://docs.moderne.io/licensing/moderne-source-available-license
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.logging.slf4j;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.openrewrite.Tree.randomId;

public class CompleteExceptionLogging extends Recipe {
    private static final MethodMatcher LOGGER_DEBUG = new MethodMatcher("org.slf4j.Logger debug(..)");
    private static final MethodMatcher LOGGER_ERROR = new MethodMatcher("org.slf4j.Logger error(..)");
    private static final MethodMatcher LOGGER_INFO = new MethodMatcher("org.slf4j.Logger info(..)");
    private static final MethodMatcher LOGGER_TRACE = new MethodMatcher("org.slf4j.Logger trace(..)");
    private static final MethodMatcher LOGGER_WARN = new MethodMatcher("org.slf4j.Logger warn(..)");
    private static final MethodMatcher THROWABLE_GET_MESSAGE = new MethodMatcher("java.lang.Throwable getMessage()");
    private static final MethodMatcher THROWABLE_GET_LOCALIZED_MESSAGE = new MethodMatcher("java.lang.Throwable getLocalizedMessage()");


    @Getter
    final String displayName = "Enhances logging of exceptions by including the full stack trace in addition to the exception message";

    @Getter
    final String description = "It is a common mistake to call `Exception.getMessage()` when passing an exception into a log method. " +
            "Not all exception types have useful messages, and even if the message is useful this omits the stack " +
            "trace. Including a complete stack trace of the error along with the exception message in the log " +
            "allows developers to better understand the context of the exception and identify the source of the " +
            "error more quickly and accurately. \n" +
            "If the method invocation includes any call to `Exception.getMessage()` or `Exception.getLocalizedMessage()` " +
            "and not an exception is already passed as the last parameter to the log method, then we will append " +
            "the exception as the last parameter in the log method.";

    @Override
    public Set<String> getTags() {
        return new HashSet<>(Arrays.asList("logging", "slf4j"));
    }

    @Override
    public @Nullable Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(2);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>("org.slf4j.Logger", null), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method,
                                                            ExecutionContext ctx) {
                method = super.visitMethodInvocation(method, ctx);

                if (LOGGER_DEBUG.matches(method) ||
                    LOGGER_ERROR.matches(method) ||
                    LOGGER_INFO.matches(method) ||
                    LOGGER_TRACE.matches(method) ||
                    LOGGER_WARN.matches(method)) {
                    // If the method invocation includes any call to `exception.getMessage()` or `exception
                    // .getLocalizedMessage()` and not an exception is already passed as the last parameter to the
                    // log method, then we will append the exception as the last parameter in the log method.

                    List<Expression> args = method.getArguments();
                    if (args.isEmpty()) {
                        return method;
                    }

                    Expression lastParameter = args.get(args.size() - 1);

                    boolean isLastParameterAnException = lastParameter instanceof J.Identifier &&
                                                         TypeUtils.isAssignableTo("java.lang.Throwable", lastParameter.getType());
                    if (isLastParameterAnException) {
                        return method;
                    }

                    // convert `logger.error(e.getMessage());` to `logger.error("", e);`
                    if (method.getArguments().size() == 1 &&
                        lastParameter instanceof J.MethodInvocation &&
                        (THROWABLE_GET_MESSAGE.matches(lastParameter) ||
                         THROWABLE_GET_LOCALIZED_MESSAGE.matches(lastParameter))
                    ) {
                        J.MethodInvocation getMessageCall = (J.MethodInvocation) lastParameter;
                        args = ListUtils.insert(args, buildEmptyString(), 0);
                        args = ListUtils.mapLast(args, a -> getMessageCall.getSelect());
                        return autoFormat(method.withArguments(args), ctx);
                    }

                    Optional<Expression> maybeException = new JavaIsoVisitor<List<Expression>>(){
                        @Override
                        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation m,
                                                                        List<Expression> exceptions) {
                            if (THROWABLE_GET_MESSAGE.matches(m) ||
                                THROWABLE_GET_LOCALIZED_MESSAGE.matches(m)) {
                                exceptions.add(m.getSelect());
                                return m;
                            }
                            return super.visitMethodInvocation(m, exceptions);
                        }
                    }.reduce(method, new ArrayList<>()).stream().findFirst();

                    if (!maybeException.isPresent()) {
                        return method;
                    }

                    // try to move the unnecessary trailing `exception.getMessage()` call.
                    if (lastParameter instanceof J.MethodInvocation &&
                        (THROWABLE_GET_MESSAGE.matches(lastParameter) ||
                         THROWABLE_GET_LOCALIZED_MESSAGE.matches(lastParameter))) {

                        Expression firstParameter = method.getArguments().get(0);
                        if (isStringLiteral(firstParameter)) {
                            String content = ((J.Literal) firstParameter).getValue().toString();
                            int placeholderCount = countPlaceholders(content);
                            if (placeholderCount >= (args.size() - 1)) {
                                // it means the last `Throwable#getMessage()` call is counted for placeholder intentionally,
                            } else {
                                // remove the last arg
                                args.remove(args.size() - 1);
                            }
                        }
                    }

                    args = ListUtils.concat(args, maybeException.get());
                    return autoFormat(method.withArguments(args), ctx);
                }

                return method;
            }
        });
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

    public static boolean isStringLiteral(Expression expression) {
        return expression instanceof J.Literal && TypeUtils.isString(((J.Literal) expression).getType());
    }

    private static J.Literal buildEmptyString() {
        return new J.Literal(randomId(), Space.EMPTY, Markers.EMPTY, "",
            "\"\"", null, JavaType.Primitive.String);
    }
}
