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

import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.logging.ArgumentArrayToVarargs;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Collections.emptyList;
import static java.util.Collections.nCopies;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

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

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            if (METHOD_MATCHER_ARRAY.matches(method) || METHOD_MATCHER_PARAM.matches(method)) {
                List<Expression> originalArguments = method.getArguments();

                Expression levelArgument = originalArguments.get(0);
                Expression messageArgument = originalArguments.get(1);
                Expression stringFormatArgument = originalArguments.get(2);

                if (stringFormatArgument.getType() instanceof JavaType.Array &&
                        !(stringFormatArgument instanceof J.NewArray)) {
                    return method;
                }

                if (!(levelArgument instanceof J.FieldAccess || levelArgument instanceof J.Identifier) ||
                        !isStringLiteral(messageArgument)) {
                    return method;
                }
                String newName = getMethodIdentifier(levelArgument);
                if (newName == null) {
                    return method;
                }
                maybeRemoveImport("java.util.logging.Level");

                String originalFormatString = requireNonNull((String) ((J.Literal) messageArgument).getValue());
                List<Integer> originalIndices = originalLoggedArgumentIndices(originalFormatString);

                Expression updatedStringFormatArgument = stringFormatArgument;
                if (stringFormatArgument instanceof J.NewArray) {
                    J.NewArray newArray = (J.NewArray) stringFormatArgument;
                    List<Expression> arrayContent = newArray.getInitializer() == null ? emptyList() : newArray.getInitializer();
                    updatedStringFormatArgument = newArray
                            .withInitializer(originalIndices.stream().map(arrayContent::get).collect(toList()))
                            // Also unpack `new String[]{ ... }`, as `ArgumentArrayToVarargs` requires `Object[]`
                            .withType(((JavaType.Array) requireNonNull(newArray.getType())).withElemType(JavaType.ShallowClass.build("java.lang.Object")));
                }

                J.MethodInvocation updatedMi = JavaTemplate.builder(newName + "(\"#{}\",#{anyArray(Object)})")
                        .build()
                        .apply(
                                getCursor(),
                                method.getCoordinates().replaceMethod(),
                                originalFormatString.replaceAll("\\{\\d*}", "{}"),
                                updatedStringFormatArgument
                        );

                // In case of logger.log(Level.INFO, "Hello {0}, {0}", "foo")
                if (!(stringFormatArgument instanceof J.NewArray) && originalIndices.size() > 1) {
                    return updatedMi.withArguments(ListUtils.concatAll(updatedMi.getArguments(), nCopies(originalIndices.size() - 1, updatedStringFormatArgument)));
                }
                // Delegate to ArgumentArrayToVarargs to convert the array argument to varargs
                doAfterVisit(new ArgumentArrayToVarargs().getVisitor());
                Set<Flag> flags = new HashSet<>(requireNonNull(updatedMi.getMethodType()).getFlags());
                flags.add(Flag.Varargs);
                return updatedMi.withMethodType(updatedMi.getMethodType().withFlags(flags));
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
    }
}
