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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.*;
import org.openrewrite.java.search.FindFieldsOfType;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.time.Duration;
import java.util.Set;

@Value
@EqualsAndHashCode(callSuper = false)
public class SystemOutToLogging extends Recipe {
    private static final MethodMatcher systemOutPrint = new MethodMatcher("java.io.PrintStream print*(String)");

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
            valid = {"SLF4J", "Log4J", "Log4J2", "JUL"},
            required = false)
    @Nullable
    String loggingFramework;

    @Option(displayName = "Level",
            description = "The logging level to turn `System.out` print statements into.",
            valid = {"trace", "debug", "info"},
            required = false)
    @Nullable
    String level;

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(5);
    }

    @Override
    public String getDisplayName() {
        return "Use logger instead of system print statements";
    }

    @Override
    public String getDescription() {
        return "Replace `System.out` print statements with a logger.";
    }

    @Override
    protected JavaVisitor<ExecutionContext> getSingleSourceApplicableTest() {
        if (addLogger != null && addLogger) {
            return null;
        }

        return new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitCompilationUnit(J.CompilationUnit cu, ExecutionContext executionContext) {
                doAfterVisit(new UsesMethod<>(systemOutPrint));
                return cu;
            }
        };
    }

    @Override
    public JavaVisitor<ExecutionContext> getVisitor() {
        LoggingFramework framework = LoggingFramework.fromOption(loggingFramework);

        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                if (systemOutPrint.matches((Expression) method)) {
                    if (m.getSelect() != null && m.getSelect() instanceof J.FieldAccess) {
                        JavaType.Variable field = ((J.FieldAccess) m.getSelect()).getName().getFieldType();
                        if (field != null && field.getName().equals("out") && TypeUtils.isOfClassType(field.getOwner(), "java.lang.System")) {
                            return logInsteadOfPrint(m, ctx);
                        }
                    }
                }
                return m;
            }

            private J.MethodInvocation logInsteadOfPrint(J.MethodInvocation print, ExecutionContext ctx) {
                J.ClassDeclaration clazz = getCursor().firstEnclosingOrThrow(J.ClassDeclaration.class);
                Set<J.VariableDeclarations> loggers = FindFieldsOfType.find(clazz, framework.getLoggerType());
                if (!loggers.isEmpty()) {
                    J.Identifier computedLoggerName = loggers.iterator().next().getVariables().get(0).getName();
                    print = print.withTemplate(getInfoTemplate(this),
                            print.getCoordinates().replace(),
                            computedLoggerName,
                            print.getArguments().get(0));

                    print = (J.MethodInvocation) new ParameterizedLogging(framework.getLoggerType() + " " + getLevel() + "(..)")
                            .getVisitor()
                            .visitNonNull(print, ctx, getCursor());

                    if (framework == LoggingFramework.JUL) {
                        maybeAddImport("java.util.logging.Level");
                    }
                } else if (addLogger != null && addLogger) {
                    doAfterVisit(AddLogger.addLogger(clazz, framework, loggerName == null ? "logger" : loggerName));

                    // the print statement will be replaced on the subsequent pass
                    doAfterVisit(this);
                }
                return print;
            }

            private <P> JavaTemplate getInfoTemplate(JavaVisitor<P> visitor) {
                String levelOrDefault = getLevel();
                switch (framework) {
                    case SLF4J:
                        return JavaTemplate
                                .builder(visitor::getCursor, "#{any(org.slf4j.Logger)}." + levelOrDefault + "(#{any(String)})")
                                .javaParser(() -> JavaParser.fromJavaVersion()
                                        .classpath("slf4j-api")
                                        .build()
                                )
                                .build();
                    case Log4J1:
                        return JavaTemplate
                                .builder(visitor::getCursor, "#{any(org.apache.log4j.Category)}." + levelOrDefault + "(#{any(String)})")
                                .javaParser(() -> JavaParser.fromJavaVersion()
                                        .classpath("log4j")
                                        .build()
                                )
                                .build();

                    case Log4J2:
                        return JavaTemplate
                                .builder(visitor::getCursor, "#{any(org.apache.logging.log4j.Logger)}." + levelOrDefault + "(#{any(String)})")
                                .javaParser(() -> JavaParser.fromJavaVersion()
                                        .classpath("log4j-api")
                                        .build()
                                )
                                .build();
                    case JUL:
                    default:

                        return JavaTemplate
                                .builder(visitor::getCursor, "#{any(java.util.logging.Logger)}.log(Level." + levelOrDefault + ", #{any(String)})")
                                .imports("java.util.logging.Level")
                                .build();
                }
            }

            private String getLevel() {
                String levelOrDefault = level == null ? "info" : level;
                if(framework == LoggingFramework.JUL) {
                    String julLevel = levelOrDefault.toUpperCase();
                    if (levelOrDefault.equals("debug")) {
                        julLevel = "FINE";
                    } else if (levelOrDefault.equals("trace")) {
                        julLevel = "FINER";
                    }
                    return julLevel;
                }
                return levelOrDefault;
            }
        };
    }
}
