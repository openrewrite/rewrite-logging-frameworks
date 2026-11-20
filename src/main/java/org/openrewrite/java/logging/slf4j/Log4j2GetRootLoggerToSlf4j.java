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
import org.openrewrite.java.ShortenFullyQualifiedTypeReferences;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;

@Value
@EqualsAndHashCode(callSuper = false)
public class Log4j2GetRootLoggerToSlf4j extends Recipe {

    private static final MethodMatcher GET_ROOT_LOGGER = new MethodMatcher("org.apache.logging.log4j.LogManager getRootLogger()");

    String displayName = "Replace Log4j 2.x `LogManager.getRootLogger()` with SLF4J's root logger";

    String description = "Replace `org.apache.logging.log4j.LogManager.getRootLogger()` with " +
            "`org.slf4j.LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)`, since SLF4J's `LoggerFactory` has no " +
            "`getRootLogger()`.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(GET_ROOT_LOGGER), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                if (!GET_ROOT_LOGGER.matches(mi)) {
                    return mi;
                }
                maybeRemoveImport("org.apache.logging.log4j.LogManager");
                maybeAddImport("org.slf4j.LoggerFactory");
                J.MethodInvocation rootLogger = JavaTemplate.builder("LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)")
                        .imports("org.slf4j.LoggerFactory")
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "slf4j-api-2.+"))
                        .build()
                        .apply(getCursor(), mi.getCoordinates().replace());
                // Where `Logger` still names Log4j's, SLF4J's stays qualified
                doAfterVisit(ShortenFullyQualifiedTypeReferences.modifyOnly(rootLogger));
                return rootLogger;
            }
        });
    }
}
