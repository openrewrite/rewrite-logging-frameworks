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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.*;
import org.openrewrite.java.search.FindFieldsOfType;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;

import java.util.Set;

@Value
@EqualsAndHashCode(callSuper = false)
public class PrintStackTraceToLogError extends Recipe {
    @Option(displayName = "Add logger",
            description = "Add a logger field to the class if it isn't already present.",
            required = false)
    @Nullable
    Boolean addLogger;

    @Option(displayName = "Logger name",
            description = "The name of the logger to use when generating a field.",
            required = false)
    @Nullable
    String loggerName;

    @Option(displayName = "Logging framework",
            description = "The logging framework to use.",
            valid = {"SLF4J", "Log4J", "Log4J 2", "JUL"},
            required = false)
    @Nullable
    LoggingFramework loggingFramework;

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
        if (addLogger != null && addLogger) {
            return null;
        }

        LoggingFramework framework = loggingFramework == null ? LoggingFramework.SLF4J : loggingFramework;
        return new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitCompilationUnit(J.CompilationUnit cu, ExecutionContext executionContext) {
                doAfterVisit(new UsesType<>(framework.getLoggerType()));
                return cu;
            }
        };
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        MethodMatcher printStackTrace = new MethodMatcher("java.lang.Throwable printStackTrace(..)");
        LoggingFramework framework = loggingFramework == null ? LoggingFramework.SLF4J : loggingFramework;

        return new JavaIsoVisitor<ExecutionContext>() {
            @Nullable
            JavaTemplate errorTemplate;

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                J.MethodInvocation m = super.visitMethodInvocation(method, executionContext);
                if (printStackTrace.matches(m)) {
                    J.ClassDeclaration clazz = getCursor().firstEnclosingOrThrow(J.ClassDeclaration.class);
                    Set<J.VariableDeclarations> loggers = FindFieldsOfType.find(clazz, framework.getLoggerType());
                    if (!loggers.isEmpty()) {
                        m = m.withTemplate(getErrorTemplate(),
                                m.getCoordinates().replace(),
                                loggers.iterator().next().getVariables().get(0).getName(),
                                m.getSelect());
                        if (framework == LoggingFramework.JUL) {
                            maybeAddImport("java.util.logging.Level");
                        }
                    } else if (addLogger != null && addLogger) {
                        doAfterVisit(AddLogger.maybeAddLogger(getCursor(), framework, loggerName == null ? "logger" : loggerName));

                        // the print statement will be replaced on the subsequent pass
                        doAfterVisit(this);
                    }
                }
                return m;
            }

            private JavaTemplate getErrorTemplate() {
                if (errorTemplate == null) {
                    switch (framework) {
                        case SLF4J:
                            errorTemplate = JavaTemplate
                                    .builder(this::getCursor, "#{any(org.slf4j.Logger)}.error(\"Exception\", #{any(java.lang.Throwable)}")
                                    .javaParser(() -> JavaParser.fromJavaVersion()
                                            .classpath("slf4j-api")
                                            .build()
                                    )
                                    .build();
                            break;
                        case Log4J1:
                            errorTemplate = JavaTemplate
                                    .builder(this::getCursor, "#{any(org.apache.log4j.Category)}.error(\"Exception\", #{any(java.lang.Throwable)}")
                                    .javaParser(() -> JavaParser.fromJavaVersion()
                                            .classpath("log4j")
                                            .build()
                                    )
                                    .build();
                            break;
                        case Log4J2:
                            errorTemplate = JavaTemplate
                                    .builder(this::getCursor, "#{any(org.apache.logging.log4j.Logger)}.error(\"Exception\", #{any(java.lang.Throwable)}")
                                    .javaParser(() -> JavaParser.fromJavaVersion()
                                            .classpath("log4j-api")
                                            .build()
                                    )
                                    .build();
                            break;
                        case JUL:
                            errorTemplate = JavaTemplate
                                    .builder(this::getCursor, "#{any(java.util.logging.Logger)}.log(Level.SEVERE, \"Exception\", #{any(java.lang.Throwable)}")
                                    .imports("java.util.logging.Level")
                                    .build();
                            break;
                    }
                }
                return errorTemplate;
            }
        };
    }
}
