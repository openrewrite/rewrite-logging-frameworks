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
package org.openrewrite.java.logging;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.J;

public class AddLogLevelGuard extends Recipe {
    private static final String LOGGER_NAME = "logger";

    public String getDisplayName() {
        return "Add Log Level Guards";
    }

    public String getDescription() {
        return "Add Guards";
    }

    @Override
    public JavaVisitor<ExecutionContext> getVisitor() {
        JavaTemplate logLevelGuardTemplate = JavaTemplate.builder("${loggerName}.isDebugEnabled() ? ${cursor} : null")
                .imports("org.slf4j.Logger")
                .build();

        return new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                if (method.getSelect() instanceof J.Identifier &&
                    ((J.Identifier) method.getSelect()).getSimpleName().equals(LOGGER_NAME) &&
                    method.getSimpleName().equals("debug")) {
                    return logLevelGuardTemplate.apply(getCursor(), method.getCoordinates().replace(), method.getArguments().get(0));
//                            .withTemplateParameter("loggerName", method.getSelect())
//                            .withCursor(method);
                }
                return super.visitMethodInvocation(method, ctx);
            }
        };
    }


}
