/*
 * Copyright 2024 the original author or authors.
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
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.java.*;
import org.openrewrite.java.search.FindFieldsOfType;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.service.AnnotationService;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.Set;

import static java.util.Collections.emptyList;

@EqualsAndHashCode(callSuper = false)
@Value
public class SystemOutToLogging extends Recipe {
    private static final MethodMatcher systemOutPrint = new MethodMatcher("java.io.PrintStream print*(String)");

    @Option(displayName = "Add logger",
            description = "Add a logger field to the class if it isn't already present.",
            required = false)
    @Nullable
    Boolean addLogger;

    @Option(displayName = "Logger name",
            description = "The name of the logger to use when generating a field.",
            required = false,
            example = "log")
    @Nullable
    String loggerName;

    @Option(displayName = "Logging framework",
            description = "The logging framework to use.",
            valid = {"SLF4J", "Log4J1", "Log4J2", "JUL", "COMMONS", "SYSTEM"},
            required = false)
    @Nullable
    String loggingFramework;

    @Option(displayName = "Level",
            description = "The logging level to turn `System.out` print statements into.",
            valid = {"trace", "debug", "info"},
            required = false)
    @Nullable
    String level;

    String displayName = "Use logger instead of `System.out` print statements";

    String description = "Replace `System.out` print statements with a logger.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        LoggingFramework framework = LoggingFramework.fromOption(loggingFramework);
        AnnotationMatcher lombokLogAnnotationMatcher = new AnnotationMatcher("@lombok.extern..*");

        return Preconditions.check(new UsesMethod<>(systemOutPrint), Repeat.repeatUntilStable(new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                Cursor cursor = updateCursor(m);
                if (systemOutPrint.matches((Expression) method)) {
                    if (m.getSelect() instanceof J.FieldAccess) {
                        JavaType.Variable field = ((J.FieldAccess) m.getSelect()).getName().getFieldType();
                        if (field != null && "out".equals(field.getName()) && TypeUtils.isOfClassType(field.getOwner(), "java.lang.System")) {
                            return logInsteadOfPrint(cursor, ctx);
                        }
                    }
                }
                return m;
            }

            private J.MethodInvocation logInsteadOfPrint(Cursor printCursor, ExecutionContext ctx) {
                J.MethodInvocation print = printCursor.getValue();
                Cursor classCursor = getCursor().dropParentUntil(J.ClassDeclaration.class::isInstance);
                AnnotationService annotationService = service(AnnotationService.class);
                Set<J.VariableDeclarations> loggers = FindFieldsOfType.find(classCursor.getValue(), framework.getLoggerType());
                if (!loggers.isEmpty()) {
                    J.Identifier computedLoggerName = loggers.iterator().next().getVariables().get(0).getName();
                    print = replaceMethodInvocation(printCursor, ctx, print, computedLoggerName);
                } else if (annotationService.matches(classCursor, lombokLogAnnotationMatcher)) {
                    String fieldName = loggerName == null ? "log" : loggerName;
                    J.Identifier logField = new J.Identifier(Tree.randomId(), Space.SINGLE_SPACE, Markers.EMPTY, emptyList(), fieldName, null, null);
                    print = replaceMethodInvocation(printCursor, ctx, print, logField);
                } else if (addLogger != null && addLogger) {
                    doAfterVisit(AddLogger.addLogger(classCursor.getValue(), framework, loggerName == null ? "logger" : loggerName, ctx));
                }
                return print;
            }

            private J.MethodInvocation replaceMethodInvocation(Cursor printCursor, ExecutionContext ctx, J.MethodInvocation print, J.Identifier computedLoggerName) {
                print = getInfoTemplate(ctx).apply(
                        printCursor,
                        print.getCoordinates().replace(),
                        computedLoggerName,
                        print.getArguments().get(0));

                String methodPattern;
                if (framework == LoggingFramework.SYSTEM) {
                    methodPattern = framework.getLoggerType() + " log(..)";
                } else {
                    methodPattern = framework.getLoggerType() + " " + getLevel() + "(..)";
                }
                print = (J.MethodInvocation) new ParameterizedLogging(methodPattern, false)
                        .getVisitor()
                        .visitNonNull(print, ctx, printCursor);

                if (framework == LoggingFramework.JUL) {
                    maybeAddImport("java.util.logging.Level");
                }

                if (framework == LoggingFramework.SYSTEM) {
                    maybeAddImport("java.lang.System.Logger.Level");
                }

                return print;
            }

            private JavaTemplate getInfoTemplate(ExecutionContext ctx) {
                String levelOrDefault = getLevel();
                switch (framework) {
                    case SLF4J:
                        return JavaTemplate
                                .builder("#{any(org.slf4j.Logger)}." + levelOrDefault + "(#{any(String)})")
                                .javaParser(JavaParser.fromJavaVersion()
                                        .classpathFromResources(ctx, "slf4j-api-2"))
                                .build();
                    case Log4J1:
                        return JavaTemplate
                                .builder("#{any(org.apache.log4j.Category)}." + levelOrDefault + "(#{any(String)})")
                                .javaParser(JavaParser.fromJavaVersion()
                                        .classpathFromResources(ctx, "log4j-1.2.+"))
                                .build();

                    case Log4J2:
                        return JavaTemplate
                                .builder("#{any(org.apache.logging.log4j.Logger)}." + levelOrDefault + "(#{any(String)})")
                                .javaParser(JavaParser.fromJavaVersion()
                                        .classpathFromResources(ctx, "log4j-api-2.+"))
                                .build();
                    case SYSTEM:
                        return JavaTemplate
                                .builder("#{any(java.lang.System.Logger)}.log(Level." + levelOrDefault.toUpperCase() + ", #{any(String)})")
                                .imports("java.lang.System.Logger.Level")
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
        }));
    }
}
