/*
 * Copyright 2026 the original author or authors.
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
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class WrapLog4j1MdcPutValueInStringValueOf extends Recipe {

    private static final MethodMatcher PUT = new MethodMatcher("org.apache.log4j.MDC put(String, Object)");
    private static final MethodMatcher STRING_VALUE_OF = new MethodMatcher("java.lang.String valueOf(..)");

    @Getter
    final Set<String> tags = new HashSet<>(Arrays.asList("logging", "slf4j", "log4j"));

    @Getter
    final Duration estimatedEffortPerOccurrence = Duration.ofSeconds(10);

    @Getter
    final String displayName = "Wrap Log4j 1.x `MDC.put` values in `String.valueOf(...)`";

    @Getter
    final String description = "SLF4J `MDC.put(String, String)` requires a `String` value, but Log4j 1.x " +
                               "`MDC.put(String, Object)` accepts any object. Wrap non-`String` values in " +
                               "`String.valueOf(...)`, skipping values already typed `String`, `null` literals, and " +
                               "existing `String.valueOf(...)` calls. Does not change the `org.apache.log4j.MDC` type; " +
                               "compose with a `ChangeType` to complete the migration to `org.slf4j.MDC`.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(PUT), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                if (PUT.matches(m)) {
                    Expression value = m.getArguments().get(1);
                    boolean alreadyOk =
                            TypeUtils.isString(value.getType()) ||
                            J.Literal.isLiteralValue(value, null) ||
                            (value instanceof J.MethodInvocation && STRING_VALUE_OF.matches((J.MethodInvocation) value));
                    if (!alreadyOk) {
                        Expression wrapped = JavaTemplate.builder("String.valueOf(#{any()})")
                                .build()
                                .<Expression>apply(new Cursor(getCursor(), value), value.getCoordinates().replace(), value)
                                .withPrefix(value.getPrefix());
                        return m.withArguments(ListUtils.mapLast(m.getArguments(), arg -> wrapped));
                    }
                }
                return m;
            }
        });
    }
}
