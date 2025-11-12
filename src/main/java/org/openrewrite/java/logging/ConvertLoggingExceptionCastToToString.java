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
package org.openrewrite.java.logging;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@EqualsAndHashCode(callSuper = false)
@Value
public class ConvertLoggingExceptionCastToToString extends Recipe {

    @Option(displayName = "Method pattern",
            description = "A method pattern to find matching logging statements to update.",
            example = "org.slf4j.Logger debug(..)")
    String methodPattern;

    @Override
    public String getDisplayName() {
        return "Convert Logging exception cast to toString() call";
    }

    @Override
    public String getDescription() {
        //language=markdown
        return "Converts `(Object) exception` casts in logging statements to `exception.toString()` calls. " +
               "This is more explicit about the intent to log the string representation of the exception " +
               "rather than relying on implicit toString() conversion through Object casting.";
    }

    @Override
    public Set<String> getTags() {
        return new HashSet<>(Arrays.asList("Logging", "RSPEC-S1905"));
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            private final MethodMatcher methodMatcher = new MethodMatcher(methodPattern, true);

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);

                // Check if this matches our target method pattern
                if (!methodMatcher.matches(m)) {
                    return m;
                }

                // Check if there are any Object casts of Throwables to replace
                boolean hasObjectCastsToReplace = m.getArguments().stream().anyMatch(arg -> {
                    if (arg instanceof J.TypeCast) {
                        J.TypeCast cast = (J.TypeCast) arg;
                        return cast.getType() != null &&
                               TypeUtils.isOfClassType(cast.getType(), "java.lang.Object") &&
                               cast.getExpression() != null &&
                               TypeUtils.isAssignableTo("java.lang.Throwable", cast.getExpression().getType());
                    }
                    return false;
                });

                // If there are no casts to replace, return the method as-is
                if (!hasObjectCastsToReplace) {
                    return m;
                }

                // Use a JavaTemplate to properly replace the arguments
                JavaTemplate template = JavaTemplate.builder(m.getArguments().stream()
                        .map(arg -> {
                            if (arg instanceof J.TypeCast) {
                                J.TypeCast cast = (J.TypeCast) arg;
                                if (cast.getType() != null &&
                                    TypeUtils.isOfClassType(cast.getType(), "java.lang.Object") &&
                                    cast.getExpression() != null &&
                                    TypeUtils.isAssignableTo("java.lang.Throwable", cast.getExpression().getType())) {
                                    return "#{any(java.lang.Throwable)}.toString()";
                                }
                            }
                            return "#{any()}";
                        })
                        .reduce((a, b) -> a + ", " + b)
                        .orElse(""))
                        .build();

                // Prepare the arguments for the template
                Object[] templateArgs = m.getArguments().stream()
                        .map(arg -> {
                            if (arg instanceof J.TypeCast) {
                                J.TypeCast cast = (J.TypeCast) arg;
                                if (cast.getType() != null &&
                                    TypeUtils.isOfClassType(cast.getType(), "java.lang.Object") &&
                                    cast.getExpression() != null &&
                                    TypeUtils.isAssignableTo("java.lang.Throwable", cast.getExpression().getType())) {
                                    return cast.getExpression();
                                }
                            }
                            return arg;
                        })
                        .toArray();

                // Apply the template
                return (J.MethodInvocation) template.apply(
                                new Cursor(getCursor().getParent(), m),
                                m.getCoordinates().replaceArguments(),
                                templateArgs);
            }
        };
    }
}
