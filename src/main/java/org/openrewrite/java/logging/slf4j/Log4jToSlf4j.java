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
package org.openrewrite.java.logging.slf4j;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.*;
import org.openrewrite.java.search.FindFields;
import org.openrewrite.java.search.FindInheritedFields;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Stack;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Use of the traditional Log4J to SLF4J bridge can result in some
 * loss of performance as the Log4j 2 Messages must be formatted
 * before they can be passed to SLF4J.
 * <p>
 * todo
 */
public class Log4jToSlf4j extends Recipe {
    private static final MethodMatcher GET_LOGGER_MATCHER = new MethodMatcher("org.apache.log4j.Logger getLogger(..)");
    private static final MethodMatcher GET_LOGGER_WITH_MANAGER_MATCHER = new MethodMatcher("org.apache.log4j.LogManager getLogger(..)");

    @Override // todo
    public String getDisplayName() {
        return "Log4jToSlf4j";
    }

    @Override // todo
    public String getDescription() {
        return "TODO.";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new Log4jToSlf4jVisitor();
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new UsesType<>("org.apache.log4j.Logger");
    }

    private class Log4jToSlf4jVisitor extends JavaIsoVisitor<ExecutionContext> {
        private final List<MethodMatcher> logLevelMatchers = Stream.of("trace", "debug", "info", "warn", "error", "fatal")
                .map(level -> "org.apache.log4j." + (level.equals("trace") ? "Logger" : "Category") +
                        " " + level + "(..)")
                .map(MethodMatcher::new)
                .collect(Collectors.toList());

        private final Stack<String> loggerField = new Stack<>();

        @Override
        public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
            J.CompilationUnit c = super.visitCompilationUnit(cu, ctx);
            doAfterVisit(new ChangeType("org.apache.log4j.Logger", "org.slf4j.Logger"));
            doAfterVisit(new ChangeType("org.apache.log4j.Category", "org.slf4j.Logger"));
            doAfterVisit(new ParameterizedLogging());
            return c;
        }

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
            String loggerFieldName = FindFields.find(classDecl, "org.apache.log4j.Logger")
                    .stream()
                    .findAny()
                    .map(field -> field.getVariables().iterator().next().getSimpleName())
                    .orElse(FindInheritedFields.find(classDecl, "org.apache.log4j.Logger")
                            .stream()
                            .findAny()
                            .map(JavaType.Variable::getName)
                            .orElse(null)
                    );

            Optional.ofNullable(loggerFieldName).ifPresent(loggerField::push);
            J.ClassDeclaration c = super.visitClassDeclaration(classDecl, ctx);
            Optional.ofNullable(loggerFieldName).ifPresent(field -> loggerField.pop());
            return c;
        }

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            J.MethodInvocation m = super.visitMethodInvocation(method, ctx);

            if (GET_LOGGER_MATCHER.matches(m) || GET_LOGGER_WITH_MANAGER_MATCHER.matches(m)) {
                String pattern = GET_LOGGER_MATCHER.matches(m) ? "org.apache.log4j.Logger getLogger(..)" : "org.apache.log4j.LogManager getLogger(..)";
                doAfterVisit(new ChangeMethodTargetToStatic(pattern, "org.slf4j.LoggerFactory", null));
            }

            if (!loggerField.isEmpty()) {
                for (MethodMatcher matcher : logLevelMatchers) {
                    if (matcher.matches(m)) {
                        if (m.getSimpleName().equals("fatal")) {
                            doAfterVisit(new ChangeMethodName("org.apache.log4j.Category fatal(..)", "error"));
                        }

                        List<Expression> args = method.getArguments();
                        if (!args.isEmpty()) {
                            Expression message = args.iterator().next();
                            if (!TypeUtils.isOfType(message.getType(), JavaType.Primitive.String)) {
                                if (message.getType() instanceof JavaType.Class) {
                                    m = m.withTemplate(
                                            template("#{any(Object)}.toString()").build(),
                                            m.getCoordinates().replaceArguments(),
                                            message
                                    );
                                }
                            }
                        }
                    }
                }
            }
            return m;

        }

    }

}
