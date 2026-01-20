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

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;

import java.util.concurrent.atomic.AtomicReference;

import static java.util.Comparator.comparing;
import static java.util.Comparator.nullsFirst;
import static java.util.Objects.requireNonNull;
import static java.util.function.BinaryOperator.maxBy;

public class MatchIsLogLevelEnabledWithLogStatements extends Recipe {

    private static final MethodMatcher ENABLED_MATCHER = new MethodMatcher("org.slf4j.Logger is*Enabled()");
    private static final MethodMatcher LOG_MATCHER = new MethodMatcher("org.slf4j.Logger *(..)");

    @Getter
    final String displayName = "Match `if (is*Enabled())` with logging statements";

    @Getter
    final String description = "Change any `if (is*Enabled())` statements that do not match the maximum log level used in the `then` " +
            "part to use the matching `is*Enabled()` method for that log level. " +
            "This ensures that the logging condition is consistent with the actual logging statements.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesMethod<>(ENABLED_MATCHER),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.If visitIf(J.If iff, ExecutionContext ctx) {
                        J.If if_ = super.visitIf(iff, ctx);
                        if (if_.getIfCondition().getTree() instanceof J.MethodInvocation && if_.getElsePart() == null) {
                            J.MethodInvocation mi = (J.MethodInvocation) if_.getIfCondition().getTree();
                            LogLevel conditionLogLevel = LogLevel.extractEnabledLogLevel(mi);
                            if (conditionLogLevel != null) {
                                LogLevel maxUsedLogLevel = findMaxUsedLogLevel(if_.getThenPart());
                                if (maxUsedLogLevel != null && conditionLogLevel != maxUsedLogLevel) {
                                    return if_.withIfCondition(if_.getIfCondition()
                                            .withTree(maxUsedLogLevel.toEnabledInvocation(mi)));
                                }
                            }
                        }
                        return if_;
                    }

                    private @Nullable LogLevel findMaxUsedLogLevel(Statement statement) {
                        return new JavaIsoVisitor<AtomicReference<@Nullable LogLevel>>() {
                            @Override
                            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, AtomicReference<@Nullable LogLevel> reference) {
                                J.MethodInvocation mi = super.visitMethodInvocation(method, reference);
                                reference.accumulateAndGet(LogLevel.extractUsedLogLevel(mi), maxBy(nullsFirst(comparing(LogLevel::ordinal))));
                                return mi;
                            }

                            @Override
                            public J.Try.Catch visitCatch(J.Try.Catch catch_, AtomicReference<@Nullable LogLevel> logLevelAtomicReference) {
                                return catch_;
                            }

                            @Override
                            public J.MultiCatch visitMultiCatch(J.MultiCatch multiCatch, AtomicReference<@Nullable LogLevel> logLevelAtomicReference) {
                                return multiCatch;
                            }
                        }.reduce(statement, new AtomicReference<>(null)).get();
                    }
                }
        );
    }

    private enum LogLevel {
        trace,
        debug,
        info,
        warn,
        error;

        public static @Nullable LogLevel extractEnabledLogLevel(J.MethodInvocation methodInvocation) {
            if (ENABLED_MATCHER.matches(methodInvocation)) {
                String methodName = methodInvocation.getSimpleName();
                String logLevel = methodName.substring(2, methodName.length() - 7);
                for (LogLevel level : values()) {
                    if (level.name().equalsIgnoreCase(logLevel)) {
                        return level;
                    }
                }
            }
            return null;
        }

        public static @Nullable LogLevel extractUsedLogLevel(J.MethodInvocation methodInvocation) {
            if (LOG_MATCHER.matches(methodInvocation)) {
                for (LogLevel level : values()) {
                    if (level.name().equals(methodInvocation.getSimpleName())) {
                        return level;
                    }
                }
            }
            return null;
        }

        public J.MethodInvocation toEnabledInvocation(J.MethodInvocation methodInvocation) {
            String enabledMethodName = String.format("is%s%sEnabled",
                    name().substring(0, 1).toUpperCase(),
                    name().substring(1));
            JavaType.Method type = requireNonNull(methodInvocation.getMethodType())
                    .withName(enabledMethodName);
            return methodInvocation
                    .withName(methodInvocation.getName()
                            .withSimpleName(enabledMethodName)
                            .withType(type))
                    .withMethodType(type);
        }
    }
}
