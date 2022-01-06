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
import org.openrewrite.java.logging.ParameterizedLogging;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Log4j2ToSlf4j extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate Log4j2 logging framework to SLF4J";
    }

    @Override
    public String getDescription() {
        return "Use of the traditional Log4j to SLF4J bridge can result in some loss of performance " +
                "as the Log4j2 messages must be formatted before they can be passed to SLF4J. " +
                "Note, this currently does not modify `log4j.properties` files.";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                doAfterVisit(new UsesType<>("org.apache.logging.log4j.LogManager"));
                doAfterVisit(new UsesType<>("org.apache.logging.log4j.Logger"));
                return cu;
            }
        };
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            private final List<MethodMatcher> logLevelMatchers = Stream.of("trace", "debug", "info", "warn", "error", "fatal")
                    .map(level -> "org.apache.logging.log4j.Logger " + level + "(..)")
                    .map(MethodMatcher::new)
                    .collect(Collectors.toList());

            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                J.CompilationUnit c = super.visitCompilationUnit(cu, ctx);
                doAfterVisit(new ChangeType(
                        "org.apache.logging.log4j.LogManager",
                        "org.slf4j.LoggerFactory"
                ));
                doAfterVisit(new ChangeMethodName(
                        "org.apache.logging.log4j.Logger fatal(..)",
                        "error",
                        null
                ));
                doAfterVisit(new ChangeType(
                        "org.apache.logging.log4j.Logger",
                        "org.slf4j.Logger"
                ));
                doAfterVisit(new ChangeType(
                        "org.apache.log4j.MDC",
                        "org.slf4j.MDC"
                ));
                // refactor as a declarative recipe in order to prevent this parameterized logging chain fixme
                doAfterVisit(new ParameterizedLogging("org.slf4j.Logger trace(..)"));
                doAfterVisit(new ParameterizedLogging("org.slf4j.Logger debug(..)"));
                doAfterVisit(new ParameterizedLogging("org.slf4j.Logger info(..)"));
                doAfterVisit(new ParameterizedLogging("org.slf4j.Logger warn(..)"));
                doAfterVisit(new ParameterizedLogging("org.slf4j.Logger error(..)"));
                return c;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);

                for (MethodMatcher matcher : logLevelMatchers) {
                    if (matcher.matches(m)) {
                        List<Expression> args = m.getArguments();
                        if (!args.isEmpty()) {
                            Expression message = args.iterator().next();
                            if (!TypeUtils.isString(message.getType()) || message instanceof J.MethodInvocation) {
                                if (message.getType() instanceof JavaType.Class) {
                                    final StringBuilder messageBuilder = new StringBuilder("\"{}\"");
                                    m.getArguments().forEach(arg -> messageBuilder.append(", #{any()}"));
                                    m = m.withTemplate(
                                            JavaTemplate.builder(this::getCursor, messageBuilder.toString())
                                                    .build(),
                                            m.getCoordinates().replaceArguments(),
                                            m.getArguments().toArray()
                                    );
                                }
                            }
                        }
                    }
                }

                return m;
            }

        };
    }

}
