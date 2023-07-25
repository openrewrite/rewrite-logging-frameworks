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
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;

public class AddLogLevelGuard extends Recipe {
    private static final String LOGGER_NAME = "logger";

    public String getDisplayName() {
        return "Add Log Level Guards";
    }

    public String getDescription() {
        return "Add guard statements around debug log statements.";
    }

    private static final MethodMatcher DEBUG_MATCHER = new MethodMatcher("org.slf4j.Logger debug(..)");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(DEBUG_MATCHER), new JavaVisitor<ExecutionContext>() {
            JavaTemplate logLevelGuardTemplate = JavaTemplate.builder("if(#{any(org.slf4j.Logger)}.isDebugEnabled()) {\n#{}\n}")
                    .imports("org.slf4j.Logger")
                    .build();

            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
                if (!DEBUG_MATCHER.matches(method) || method.getSelect() == null) {
                    return mi;
                }
                // Prevent nested if statements
                if (getCursor().firstEnclosing(J.If.class) != null) {
                    return mi;
                }
                return logLevelGuardTemplate.apply(getCursor(), method.getCoordinates().replace(), method.getSelect(), mi);
            }
        });
    }


}
