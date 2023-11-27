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
import org.openrewrite.*;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.*;
import org.openrewrite.java.search.FindFieldsOfType;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

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
            valid = {"SLF4J", "Log4J1", "Log4J2", "JUL", "COMMONS"},
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
        return "Use logger instead of `System.out` print statements";
    }

    @Override
    public String getDescription() {
        return "Replace `System.out` print statements with a logger.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        LoggingFramework framework = LoggingFramework.fromOption(loggingFramework);
        AnnotationMatcher lombokLogAnnotationMatcher = new AnnotationMatcher("@lombok.extern..*");

        return Preconditions.check(new UsesMethod<>(systemOutPrint), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                if (systemOutPrint.matches((Expression) method)) {
                    if (m.getSelect() != null && m.getSelect() instanceof J.FieldAccess) {
                        JavaType.Variable field = ((J.FieldAccess) m.getSelect()).getName().getFieldType();
                        if (field != null && "out".equals(field.getName()) && TypeUtils.isOfClassType(field.getOwner(), "java.lang.System")) {
                            return logInsteadOfPrint(new Cursor(getCursor().getParent(), m), ctx);
                        }
                    }
                }
                return m;
            }

            private J.MethodInvocation logInsteadOfPrint(Cursor printCursor, ExecutionContext ctx) {
                J.MethodInvocation print = printCursor.getValue();
                J.ClassDeclaration clazz = getCursor().firstEnclosingOrThrow(J.ClassDeclaration.class);
                Set<J.VariableDeclarations> loggers = FindFieldsOfType.find(clazz, framework.getLoggerType());
                if (!loggers.isEmpty()) {
                    J.Identifier computedLoggerName = loggers.iterator().next().getVariables().get(0).getName();
                    print = replaceMethodInvocation(printCursor, ctx, print, computedLoggerName);
                } else if (clazz.getAllAnnotations().stream().anyMatch(lombokLogAnnotationMatcher::matches)) {
                    String fieldName = loggerName == null ? "log" : loggerName;
                    J.Identifier logField = new J.Identifier(UUID.randomUUID(), Space.SINGLE_SPACE, Markers.EMPTY, Collections.emptyList(), fieldName, null, null);
                    print = replaceMethodInvocation(printCursor, ctx, print, logField);
                } else if (addLogger != null && addLogger) {
                    doAfterVisit(AddLogger.addLogger(clazz, framework, loggerName == null ? "logger" : loggerName));
                }
                return print;
            }

            private J.MethodInvocation replaceMethodInvocation(Cursor printCursor, ExecutionContext ctx, J.MethodInvocation print, J.Identifier computedLoggerName) {
                print = getInfoTemplate().apply(
                        printCursor,
                        print.getCoordinates().replace(),
                        computedLoggerName,
                        print.getArguments().get(0));

                print = (J.MethodInvocation) new ParameterizedLogging(framework.getLoggerType() + " " + getLevel() + "(..)", false)
                        .getVisitor()
                        .visitNonNull(print, ctx, printCursor);

                if (framework == LoggingFramework.JUL) {
                    maybeAddImport("java.util.logging.Level");
                }
                return print;
            }

            private JavaTemplate getInfoTemplate() {
                String levelOrDefault = getLevel();
                switch (framework) {
                    case SLF4J:
                        return JavaTemplate
                                .builder("#{any(org.slf4j.Logger)}." + levelOrDefault + "(#{any(String)})")
                                .javaParser(JavaParser.fromJavaVersion().classpath("slf4j-api"))
                                .build();
                    case Log4J1:
                        return JavaTemplate
                                .builder("#{any(org.apache.log4j.Category)}." + levelOrDefault + "(#{any(String)})")
                                .javaParser(JavaParser.fromJavaVersion().classpath("log4j"))
                                .build();

                    case Log4J2:
                        return JavaTemplate
                                .builder("#{any(org.apache.logging.log4j.Logger)}." + levelOrDefault + "(#{any(String)})")
                                .javaParser(JavaParser.fromJavaVersion().classpath("log4j-api"))
                                .build();
                    case JUL:
                    default:

                        return JavaTemplate
                                .builder("#{any(java.util.logging.Logger)}.log(Level." + levelOrDefault + ", #{any(String)})")
                                .imports("java.util.logging.Level")
                                .build();
                }
            }

            private String getLevel() {
                String levelOrDefault = level == null ? "info" : level;
                if (framework == LoggingFramework.JUL) {
                    String julLevel = levelOrDefault.toUpperCase();
                    if ("debug".equals(levelOrDefault)) {
                        julLevel = "FINE";
                    } else if ("trace".equals(levelOrDefault)) {
                        julLevel = "FINER";
                    }
                    return julLevel;
                }
                return levelOrDefault;
            }
        });
    }
}
