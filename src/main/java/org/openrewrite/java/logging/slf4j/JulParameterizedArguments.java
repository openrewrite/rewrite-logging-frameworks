/*
 * Copyright 2024 the original author or authors.
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

import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.openrewrite.Tree.randomId;

public class JulParameterizedArguments extends Recipe {
    private static final MethodMatcher METHOD_MATCHER_PARAM = new MethodMatcher("java.util.logging.Logger log(java.util.logging.Level, java.lang.String, java.lang.Object)");
    private static final MethodMatcher METHOD_MATCHER_ARRAY = new MethodMatcher("java.util.logging.Logger log(java.util.logging.Level, java.lang.String, java.lang.Object[])");

    @Override
    public String getDisplayName() {
        return "Replace parameterized JUL level call with corresponding SLF4J method calls";
    }

    @Override
    public String getDescription() {
        return "Replace calls to parameterized `Logger.log(Level,String,…)` call with the corresponding slf4j method calls transforming the formatter and parameter lists.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.or(new UsesMethod<>(METHOD_MATCHER_PARAM), new UsesMethod<>(METHOD_MATCHER_ARRAY)), new JulParameterizedToSlf4jVisitor());
    }

    private static class JulParameterizedToSlf4jVisitor extends JavaIsoVisitor<ExecutionContext> {

        public static boolean isStringLiteral(Expression expression) {
            return expression instanceof J.Literal && TypeUtils.isString(((J.Literal) expression).getType());
        }

        private static @Nullable String getMethodIdentifier(Expression levelArgument) {
            String levelSimpleName = levelArgument instanceof J.FieldAccess ?
                    (((J.FieldAccess) levelArgument).getName().getSimpleName()) :
                    (((J.Identifier) levelArgument).getSimpleName());
            switch (levelSimpleName) {
                case "ALL":
                case "FINEST":
                case "FINER":
                    return "trace";
                case "FINE":
                    return "debug";
                case "CONFIG":
                case "INFO":
                    return "info";
                case "WARNING":
                    return "warn";
                case "SEVERE":
                    return "error";
            }
            return null;
        }

        private static J.Literal buildStringLiteral(String string) {
            return new J.Literal(randomId(), Space.EMPTY, Markers.EMPTY, string, String.format("\"%s\"", string), null, JavaType.Primitive.String);
        }

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            if (METHOD_MATCHER_ARRAY.matches(method) || METHOD_MATCHER_PARAM.matches(method)) {
                List<Expression> originalArguments = method.getArguments();

                Expression levelArgument = originalArguments.get(0);
                Expression messageArgument = originalArguments.get(1);

                if (!(levelArgument instanceof J.FieldAccess || levelArgument instanceof J.Identifier) ||
                    !isStringLiteral(messageArgument)) {
                    return method;
                }
                String newName = getMethodIdentifier(levelArgument);
                if(newName == null) {
                    return method;
                }
                maybeRemoveImport("java.util.logging.Level");

                String originalFormatString = Objects.requireNonNull((String) ((J.Literal) messageArgument).getValue());
                List<Integer> originalIndices = originalLoggedArgumentIndices(originalFormatString);
                List<Expression> originalParameters = originalParameters(originalArguments.get(2));

                List<Expression> targetArguments = new ArrayList<>(2);
                targetArguments.add(buildStringLiteral(originalFormatString.replaceAll("\\{\\d*}", "{}")));
                originalIndices.forEach(i -> targetArguments.add(originalParameters.get(i)));
                return JavaTemplate.builder(createTemplateString(newName, targetArguments))
                        .contextSensitive()
                        .javaParser(JavaParser.fromJavaVersion()
                                .classpathFromResources(ctx, "slf4j-api-2.1"))
                        .build()
                        .apply(getCursor(), method.getCoordinates().replaceMethod(), targetArguments.toArray());
            }
            return super.visitMethodInvocation(method, ctx);
        }

        private List<Integer> originalLoggedArgumentIndices(String strFormat) {
            // A string format like "Hello {0} {1} {1}" should be transformed to 0, 1, 1
            Matcher matcher = Pattern.compile("\\{(\\d+)}").matcher(strFormat);
            List<Integer> loggedArgumentIndices = new ArrayList<>(2);
            while (matcher.find()) {
                loggedArgumentIndices.add(Integer.valueOf(matcher.group(1)));
            }
            return loggedArgumentIndices;
        }

        private static List<Expression> originalParameters(Expression logParameters) {
            if (logParameters instanceof J.NewArray) {
                final List<Expression> initializer = ((J.NewArray) logParameters).getInitializer();
                if (initializer == null || initializer.isEmpty()) {
                    return Collections.emptyList();
                }
                return initializer;
            }
            return Collections.singletonList(logParameters);
        }

        private static String createTemplateString(String newName, List<Expression> targetArguments) {
            List<String> targetArgumentsStrings = new ArrayList<>();
            targetArguments.forEach(targetArgument -> targetArgumentsStrings.add("#{any()}"));
            return newName + '(' + String.join(",", targetArgumentsStrings) + ')';
        }
    }
}
