/*
 * Copyright 2025 the original author or authors.
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
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Arrays;
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
               "rather than relying on implicit toString() conversion through Object casting." +
               "Run this after ParameterizedLogging is applied to reduce RSPEC-S1905 findings.";
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

                JavaTemplate toStringTemplate = JavaTemplate.builder("#{any(java.lang.Throwable)}.toString()")
                        .build();

                m = m.withArguments(ListUtils.map(m.getArguments(), arg -> {
                    if (arg instanceof J.TypeCast) {
                        J.TypeCast cast = (J.TypeCast) arg;
                        if (cast.getType() != null &&
                            TypeUtils.isOfClassType(cast.getType(), "java.lang.Object") &&
                            TypeUtils.isAssignableTo("java.lang.Throwable", cast.getExpression().getType())) {
                            return toStringTemplate.apply(
                                    new Cursor(getCursor(), arg),
                                    arg.getCoordinates().replace(),
                                    cast.getExpression());
                        }
                    }
                    return arg;
                }));

                return m.equals(method) ? method : m;
            }
        };
    }
}
