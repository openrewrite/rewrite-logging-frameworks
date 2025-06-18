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
package org.openrewrite.java.logging.slf4j;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

public class StripToStringFromArguments extends Recipe {
    private static final MethodMatcher TRACE_MATCHER = new MethodMatcher("org.slf4j.Logger trace(..)");
    private static final MethodMatcher DEBUG_MATCHER = new MethodMatcher("org.slf4j.Logger debug(..)");
    private static final MethodMatcher INFO_MATCHER = new MethodMatcher("org.slf4j.Logger info(..)");
    private static final MethodMatcher WARN_MATCHER = new MethodMatcher("org.slf4j.Logger warn(..)");
    private static final MethodMatcher ERROR_MATCHER = new MethodMatcher("org.slf4j.Logger error(..)");

    private static final MethodMatcher TO_STRING_MATCHER = new MethodMatcher("java.lang.Object toString()");

    @Override
    public String getDisplayName() {
        return "Strip `toString()` from arguments";
    }

    @Override
    public String getDescription() {
        return "Remove `.toString()` from logger call arguments; SLF4J will automatically call `toString()` on an argument when not a string, and do so only if the log level is enabled.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.or(
                        new UsesMethod<>(TRACE_MATCHER),
                        new UsesMethod<>(DEBUG_MATCHER),
                        new UsesMethod<>(INFO_MATCHER),
                        new UsesMethod<>(WARN_MATCHER),
                        new UsesMethod<>(ERROR_MATCHER)
                ),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation mi, ExecutionContext ctx) {
                        mi = super.visitMethodInvocation(mi, ctx);

                        if (!(TRACE_MATCHER.matches(mi) ||
                              DEBUG_MATCHER.matches(mi) ||
                              INFO_MATCHER.matches(mi) ||
                              WARN_MATCHER.matches(mi) ||
                              ERROR_MATCHER.matches(mi))) {
                            return mi;
                        }

                        int firstFormatArgIndex = TypeUtils.isOfClassType(mi.getArguments().get(0).getType(), "org.slf4j.Marker") ? 2 : 1;

                        return mi.withArguments(
                                ListUtils.map(mi.getArguments(), (index, arg) -> {
                                    if (index < firstFormatArgIndex) {
                                        return arg;
                                    }
                                    if (arg instanceof J.MethodInvocation) {
                                        J.MethodInvocation toStringInvocation = (J.MethodInvocation) arg;
                                        if (TO_STRING_MATCHER.matches(toStringInvocation) &&
                                            toStringInvocation.getSelect() != null &&
                                            !TypeUtils.isAssignableTo("java.lang.Throwable", toStringInvocation.getSelect().getType())) {
                                            return toStringInvocation.getSelect().withPrefix(toStringInvocation.getPrefix());
                                        }
                                    }
                                    return arg;
                                }));
                    }
                });
    }
}
