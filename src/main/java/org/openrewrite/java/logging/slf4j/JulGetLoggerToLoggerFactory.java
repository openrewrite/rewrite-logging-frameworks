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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

@Value
@EqualsAndHashCode(callSuper = false)
public class JulGetLoggerToLoggerFactory extends Recipe {

    private static final MethodMatcher GET_LOGGER = new MethodMatcher("java.util.logging.Logger getLogger(java.lang.String)");
    private static final MethodMatcher CLASS_GET_NAME = new MethodMatcher("java.lang.Class getName()");
    private static final MethodMatcher CLASS_GET_CANONICAL_NAME = new MethodMatcher("java.lang.Class getCanonicalName()");

    String displayName = "Replace JUL Logger creation with SLF4J LoggerFactory";

    String description = "Replace calls to `Logger.getLogger(Some.class.getName())` and " +
            "`Logger.getLogger(Some.class.getCanonicalName())` with `LoggerFactory.getLogger(Some.class)`.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(GET_LOGGER), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                if (!GET_LOGGER.matches(mi)) {
                    return mi;
                }

                Expression arg = mi.getArguments().get(0);
                if (!(arg instanceof J.MethodInvocation)) {
                    return mi;
                }

                J.MethodInvocation argMethod = (J.MethodInvocation) arg;
                if (!CLASS_GET_NAME.matches(argMethod) && !CLASS_GET_CANONICAL_NAME.matches(argMethod)) {
                    return mi;
                }

                Expression classExpr = argMethod.getSelect();
                if (classExpr == null) {
                    return mi;
                }

                maybeRemoveImport("java.util.logging.Logger");
                maybeAddImport("org.slf4j.LoggerFactory");

                return JavaTemplate.builder("LoggerFactory.getLogger(#{any(java.lang.Class)})")
                        .imports("org.slf4j.LoggerFactory")
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "slf4j-api-2.+"))
                        .build()
                        .apply(getCursor(), mi.getCoordinates().replace(), classExpr);
            }
        });
    }
}
