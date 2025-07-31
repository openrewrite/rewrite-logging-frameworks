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
package org.openrewrite.java.logging.jboss;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;

import static java.util.Objects.requireNonNull;

public class LoggerLevelArgumentToMethod extends Recipe {
    private static final MethodMatcher LOG_MATCHER = new MethodMatcher("org.jboss.logging.Logger log(*,*,..)", true);
    private static final MethodMatcher LOGF_MATCHER = new MethodMatcher("org.jboss.logging.Logger logf(*,*,..)", true);
    private static final MethodMatcher LOGV_MATCHER = new MethodMatcher("org.jboss.logging.Logger logv(*,*,..)", true);

    @Override
    public String getDisplayName() {
        return "Replace JBoss Logging Level arguments with the corresponding eponymous level method calls";
    }

    @Override
    public String getDescription() {
        return "Replace calls to `Logger.log(Level, ...)` with the corresponding eponymous level method calls. For example `Logger.log(Level.INFO, ...)` to `Logger.info(...)`.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        TreeVisitor<?, ExecutionContext> preconditions = Preconditions.and(
                new UsesType<>("org.jboss.logging.Logger", true),
                new UsesType<>("org.jboss.logging.Logger.Level", true),
                Preconditions.or(
                        new UsesMethod<>(LOG_MATCHER),
                        new UsesMethod<>(LOGF_MATCHER),
                        new UsesMethod<>(LOGV_MATCHER)
                )
        );
        return Preconditions.check(preconditions, new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation mi, ExecutionContext ctx) {
                        J.MethodInvocation m = super.visitMethodInvocation(mi, ctx);
                        if (!(LOG_MATCHER.matches(m) || LOGF_MATCHER.matches(m) || LOGV_MATCHER.matches(m))) {
                            return m;
                        }
                        List<Expression> args = m.getArguments();
                        Expression firstArgument = args.get(0);
                        Expression secondArgument = args.get(1);

                        String formatted = "";
                        if (LOGF_MATCHER.matches(m) || LOGV_MATCHER.matches(m)) {
                            if (TypeUtils.isAssignableTo("java.lang.String", firstArgument.getType())) {
                                // `logf(String, ..)` and `logv(String, ..)` don't have a logger.level() equivalent
                                return m;
                            }
                            formatted = m.getSimpleName().substring(m.getSimpleName().length() - 1);
                        }

                        String logLevelName;
                        List<Expression> updatedArguments;
                        if (TypeUtils.isAssignableTo("org.jboss.logging.Logger.Level", firstArgument.getType())) {
                            // `log(Logger.Level, ..)`, `logf(Logger.Level, ..)`, `logv(Logger.Level, ..)`
                            logLevelName = extractLogLevelName(firstArgument) + formatted;
                            updatedArguments = ListUtils.concat(
                                    (Expression) secondArgument.withPrefix(firstArgument.getPrefix()),
                                    args.subList(2, args.size()));
                        } else if (TypeUtils.isAssignableTo("java.lang.String", firstArgument.getType()) &&
                                   TypeUtils.isAssignableTo("org.jboss.logging.Logger.Level", secondArgument.getType())) {
                            // `log(String, Logger.Level, ..)`
                            logLevelName = extractLogLevelName(secondArgument);
                            updatedArguments = ListUtils.filter(args, it -> it != secondArgument);
                        } else {
                            return m;
                        }

                        JavaType.Method updatedMethodType = requireNonNull(m.getMethodType())
                                .withParameterTypes(ListUtils.filter(m.getMethodType().getParameterTypes(), it -> !TypeUtils.isAssignableTo("org.jboss.logging.Logger.Level", it)))
                                .withParameterNames(ListUtils.filter(m.getMethodType().getParameterNames(), it -> !"level".equals(it)))
                                .withName(logLevelName.toLowerCase());

                        return m
                                .withArguments(updatedArguments)
                                .withMethodType(updatedMethodType)
                                .withName(m.getName().withSimpleName(logLevelName.toLowerCase()));
                    }

                    String extractLogLevelName(Expression expression) {
                        if (expression instanceof J.Identifier) {
                            return ((J.Identifier) expression).getSimpleName();
                        }
                        return ((J.FieldAccess) expression).getSimpleName();
                    }
                }
        );
    }
}
