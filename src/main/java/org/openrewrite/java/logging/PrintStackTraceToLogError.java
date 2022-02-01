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
package org.openrewrite.java.logging;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.FindFieldsOfType;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;

import java.util.Set;

public class PrintStackTraceToLogError extends Recipe {
    @Override
    public String getDisplayName() {
        return "Use logger instead of `printStackTrace()`";
    }

    @Override
    public String getDescription() {
        return "When a logger is present, log exceptions rather than calling `printStackTrace()`.";
    }

    @Override
    protected JavaVisitor<ExecutionContext> getSingleSourceApplicableTest() {
        return new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitCompilationUnit(J.CompilationUnit cu, ExecutionContext executionContext) {
                doAfterVisit(new UsesType<>("org.slf4j.Logger"));
                doAfterVisit(new UsesType<>("org.apache.log4j.Category"));
                return cu;
            }
        };
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        MethodMatcher printStackTrace = new MethodMatcher("java.lang.Throwable printStackTrace()");
        return new JavaVisitor<ExecutionContext>() {
            final JavaTemplate slf4jError = JavaTemplate
                    .builder(this::getCursor, "#{any(org.slf4j.Logger)}.error(\"Exception\", #{any(java.lang.Throwable)}")
                    .javaParser(() -> JavaParser.fromJavaVersion()
                                    .dependsOn("" +
                                            "package org.slf4j;" +
                                            "public interface Logger {" +
                                            "    void error(String msg, Throwable t);" +
                                            "}")
                                    .build()
                    )
                    .build();

            final JavaTemplate log4jError = JavaTemplate
                    .builder(this::getCursor, "#{any(org.apache.log4j.Category)}.error(\"Exception\", #{any(java.lang.Throwable)}")
                    .javaParser(() -> JavaParser.fromJavaVersion()
                                    .dependsOn("" +
                                            "package org.apache.log4j;" +
                                            "public interface Category {" +
                                            "    void error(Object msg, Throwable t);" +
                                            "}")
                                    .build()
                    )
                    .build();

            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                if (printStackTrace.matches(method)) {
                    J.ClassDeclaration clazz = getCursor().firstEnclosingOrThrow(J.ClassDeclaration.class);

                    Set<J.VariableDeclarations> slf4jLoggers = FindFieldsOfType.find(clazz, "org.slf4j.Logger");
                    if (!slf4jLoggers.isEmpty()) {
                        return method.withTemplate(slf4jError,
                                method.getCoordinates().replace(),
                                slf4jLoggers.iterator().next().getVariables().get(0).getName(),
                                method.getSelect());
                    }

                    Set<J.VariableDeclarations> log4jLoggers = FindFieldsOfType.find(clazz, "org.apache.log4j.Category");
                    if (!log4jLoggers.isEmpty()) {
                        return method.withTemplate(log4jError,
                                method.getCoordinates().replace(),
                                log4jLoggers.iterator().next().getVariables().get(0).getName(),
                                method.getSelect());
                    }
                }

                return super.visitMethodInvocation(method, executionContext);
            }
        };
    }
}
