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
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.concurrent.atomic.AtomicBoolean;

@EqualsAndHashCode(callSuper = false)
@Value
public class CatchBlockLogLevel extends Recipe {

    @Override
    public String getDisplayName() {
        return "Catch block log level";
    }

    @Override
    public String getDescription() {
        return "Sometimes exceptions are caught and logged at the wrong log level. This will set the log level of " +
               "logging statements within a catch block not containing an exception to \"warn\", and the log level of " +
               "logging statements containing an exception to \"error\". " +
               "This supports SLF4J, Log4J1, Log4j2, and Logback.";
    }

    private static final MethodMatcher SLF4J_MATCHER = new MethodMatcher("org.slf4j.Logger *(..)");
    private static final MethodMatcher LOG4J1_MATCHER = new MethodMatcher("org.apache.log4j.Category *(..)");
    private static final MethodMatcher LOG4J2_MATCHER = new MethodMatcher("org.apache.logging.log4j.Logger *(..)");
    private static final MethodMatcher LOGBACK_MATCHER = new MethodMatcher("ch.qos.logback.classic.Logger *(..)");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        TreeVisitor<?, ExecutionContext> check = Preconditions.or(
                new UsesMethod<>(SLF4J_MATCHER),
                new UsesMethod<>(LOG4J1_MATCHER),
                new UsesMethod<>(LOG4J2_MATCHER),
                new UsesMethod<>(LOGBACK_MATCHER));

        return Preconditions.check(check, new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                if (!(SLF4J_MATCHER.matches(m) || LOG4J1_MATCHER.matches(m) || LOG4J2_MATCHER.matches(m) || LOGBACK_MATCHER.matches(m))) {
                    return m;
                }
                Object maybeCatch = getCursor().dropParentUntil(it ->
                        it == Cursor.ROOT_VALUE ||
                        it instanceof J.Try.Catch || it instanceof J.Try ||
                        it instanceof J.MethodDeclaration || it instanceof J.Lambda || it instanceof J.ClassDeclaration).getValue();
                if (!(maybeCatch instanceof J.Try.Catch)) {
                    return m;
                }
                if (referencesException(m, ctx)) {
                    if ("info".equals(m.getSimpleName()) || "debug".equals(m.getSimpleName()) || "trace".equals(m.getSimpleName()) || "warn".equals(m.getSimpleName())) {
                        JavaType.Method mt = m.getMethodType() == null ? null : m.getMethodType().withName("error");
                        m = m.withName(m.getName().withSimpleName("error").withType(mt))
                                .withMethodType(mt);
                    }
                } else {
                    if ("info".equals(m.getSimpleName()) || "debug".equals(m.getSimpleName()) || "trace".equals(m.getSimpleName())) {
                        JavaType.Method mt = m.getMethodType() == null ? null : m.getMethodType().withName("warn");
                        m = m.withName(m.getName().withSimpleName("warn").withType(mt))
                                .withMethodType(mt);
                    }
                }
                return m;
            }

            private boolean referencesException(J j, ExecutionContext ctx) {
                AtomicBoolean found = new AtomicBoolean(false);
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                        J.Identifier i = super.visitIdentifier(identifier, ctx);
                        found.set(found.get() || TypeUtils.isAssignableTo("java.lang.Throwable", i.getType()));
                        return i;
                    }
                }.visit(j, ctx);
                return found.get();
            }
        });
    }
}
