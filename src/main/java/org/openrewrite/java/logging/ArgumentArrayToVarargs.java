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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.*;

import java.time.Duration;
import java.util.List;

public class ArgumentArrayToVarargs extends Recipe {
    // Match logger methods that end with Object[] - but we'll verify if it's varargs later
    private static final MethodMatcher LOGGER_METHOD = new MethodMatcher("*..*Log* *(.., Object[])");

    @Override
    public String getDisplayName() {
        return "Convert argument array to varargs";
    }

    @Override
    public String getDescription() {
        return "Converts method calls that use an array of arguments to use varargs instead.";
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(2);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(LOGGER_METHOD), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                if (LOGGER_METHOD.matches(mi)) {
                    return mi.withArguments(ListUtils.flatMap(mi.getArguments(), (index, lastArg) -> {
                        // Check if the last argument is a new Object[] array
                        if (index == mi.getArguments().size() - 1 && lastArg instanceof J.NewArray) {
                            // Verify it's an Object[] array
                            J.NewArray arrayArg = (J.NewArray) lastArg;
                            if (arrayArg.getType() instanceof JavaType.Array &&
                                    TypeUtils.isObject(((JavaType.Array) arrayArg.getType()).getElemType())) {
                                // Only make changes if the method has a varargs parameter
                                if (mi.getMethodType() == null || mi.getMethodType().hasFlags(Flag.Varargs)) {
                                    List<Expression> arrayElements = arrayArg.getInitializer();
                                    if (arrayElements == null || arrayElements.isEmpty() || arrayElements.get(0) instanceof J.Empty) {
                                        return null; // Remove empty array argument
                                    }
                                    return ListUtils.mapFirst(arrayElements, first -> first.withPrefix(lastArg.getPrefix()));
                                }
                            }
                        }
                        return lastArg;
                    }));
                }
                return mi;
            }
        });
    }
}
