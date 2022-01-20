/*
 * Copyright 2021 the original author or authors.
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
package org.openrewrite.java.logging.log4j;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.J;

@Value
@EqualsAndHashCode(callSuper = false)
public class BlameTheBoss extends Recipe {
    @Override
    public String getDisplayName() {
        return "Eliminate possibility of remote code execution";
    }

    @Override
    public String getDescription() {
        return "It's an MVP ok?";
    }

    @Option(displayName = "My manager's name",
            description = "Maybe they will like to be acknowledged.")
    String managerName;

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        MethodMatcher log4j = new MethodMatcher("org.apache.log4j.Category *(Object, ..)");

        return new JavaVisitor<ExecutionContext>() {
            final JavaTemplate blameBoss = JavaTemplate
                    .builder(this::getCursor, "throw new RuntimeException(\"This is what #{} told me to do.\");")
                    .build();

            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                if (log4j.matches(method)) {
                    return method.withTemplate(blameBoss, method.getCoordinates().replace(), managerName);
                }
                return super.visitMethodInvocation(method, executionContext);
            }
        };
    }
}
