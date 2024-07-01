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
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.openrewrite.Tree.randomId;

public class JulParameterizedArguments extends Recipe {
    private static final MethodMatcher METHOD_MATCHER_PARAM = new MethodMatcher("java.util.logging.Logger log(java.util.logging.Level, java.lang.String, java.lang.Object)");
    private static final MethodMatcher METHOD_MATCHER_ARRAY = new MethodMatcher("java.util.logging.Logger log(java.util.logging.Level, java.lang.String, java.lang.Object[])");

    public static boolean isStringLiteral(Expression expression) {
        return expression instanceof J.Literal && TypeUtils.isString(((J.Literal) expression).getType());
    }

    private static Optional<String> getMethodIdentifier(String name) {
        String newMethodName = null;
        switch (name) {
            case "ALL":
            case "FINEST":
            case "FINER":
                newMethodName = "trace";
                break;
            case "FINE":
                newMethodName = "debug";
                break;
            case "CONFIG":
            case "INFO":
                newMethodName = "info";
                break;
            case "WARNING":
                newMethodName = "warn";
                break;
            case "SEVERE":
                newMethodName = "error";
                break;
        }

        return Optional.ofNullable(newMethodName);
    }

    private static J.Literal buildString(String string) {
        return new J.Literal(randomId(), Space.EMPTY, Markers.EMPTY, string, String.format("\"%s\"", string), null, JavaType.Primitive.String);
    }

    @Override
    public String getDisplayName() {
        return "Replace parameterized JUL leval call with corresponding slf4j method calls";
    }

    @Override
    public String getDescription() {
        return "Replace calls to parameterized `Logger.log(Level,String,…)` call with the corresponding slf4j method calls transforming the formatter and parameter lists.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.or(new UsesMethod<>(METHOD_MATCHER_PARAM), new UsesMethod<>(METHOD_MATCHER_ARRAY)), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                if (METHOD_MATCHER_ARRAY.matches(method) || METHOD_MATCHER_PARAM.matches(method)) {
                    List<Expression> originalArguments = method.getArguments();

                    Expression levelName = originalArguments.get(0);
                    String simpleName = ((J.FieldAccess) levelName).getName().getSimpleName();
                    Optional<String> newName = getMethodIdentifier(simpleName);
                    if (!newName.isPresent()) {
                        return method;
                    }
                    J.Identifier newMethodName = method.getName().withSimpleName(newName.get());

                    List<Expression> targetArguments = new ArrayList<>(2);

                    J.Literal stringFormat = (J.Literal) originalArguments.get(1);
                    if (!isStringLiteral(stringFormat)) {
                        return method;
                    }
                    String strFormat = Objects.requireNonNull((stringFormat).getValue()).toString();
                    strFormat = strFormat.replaceAll("\\{\\d*}", "{}");
                    J.Literal element = buildString(strFormat);
                    targetArguments.add(element);

                    Expression logParameters = originalArguments.get(2);
                    if (logParameters instanceof J.NewArray) {
                        final List<Expression> initializer = ((J.NewArray) logParameters).getInitializer();
                        if (initializer != null && !initializer.isEmpty()) {
                            targetArguments.addAll(initializer);
                        }
                    } else {
                        targetArguments.add(logParameters);
                    }

                    maybeRemoveImport("java.util.logging.Level");

                    List<String> targetArgumentsStrings = new ArrayList<>();
                    targetArguments.forEach(targetArgument -> targetArgumentsStrings.add("#{any()}"));
                    String templateStr = newMethodName + "(" + String.join(",", targetArgumentsStrings) + ")";
                    return JavaTemplate.builder(templateStr)
                            .contextSensitive()
                            .javaParser(JavaParser.fromJavaVersion()
                                    .classpathFromResources(ctx, "slf4j-api-2.1"))
                            .build()
                            .apply(getCursor(), method.getCoordinates().replaceMethod(), targetArguments.toArray());
                }
                return super.visitMethodInvocation(method, ctx);
            }
        });
    }
}
