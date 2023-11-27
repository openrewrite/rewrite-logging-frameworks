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
import org.openrewrite.internal.ListUtils;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Value
@EqualsAndHashCode(callSuper = false)
public class SystemErrToLogging extends Recipe {
    private static final MethodMatcher systemErrPrint = new MethodMatcher("java.io.PrintStream print*(String)");

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(5);
    }

    private static final MethodMatcher printStackTrace = new MethodMatcher("java.lang.Throwable printStackTrace(..)");

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

    @Override
    public String getDisplayName() {
        return "Use logger instead of `System.err` print statements";
    }

    @Override
    public String getDescription() {
        return "Replace `System.err` print statements with a logger.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        LoggingFramework framework = LoggingFramework.fromOption(loggingFramework);
        AnnotationMatcher lombokLogAnnotationMatcher = new AnnotationMatcher("@lombok.extern..*");

        return Preconditions.check(new UsesMethod<>(systemErrPrint), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
                J.Block b = super.visitBlock(block, ctx);
                Cursor blockCursor = new Cursor(getCursor().getParent(), b);

                AtomicBoolean addedLogger = new AtomicBoolean(false);
                AtomicInteger skip = new AtomicInteger(-1);

                b = b.withStatements(ListUtils.map(b.getStatements(), (i, stat) -> {
                    if (skip.get() == i) {
                        return null;
                    } else if (stat instanceof J.MethodInvocation) {
                        J.MethodInvocation m = (J.MethodInvocation) stat;
                        if (systemErrPrint.matches((Expression) stat)) {
                            if (m.getSelect() != null && m.getSelect() instanceof J.FieldAccess) {
                                JavaType.Variable field = ((J.FieldAccess) m.getSelect()).getName().getFieldType();
                                if (field != null && "err".equals(field.getName()) && TypeUtils.isOfClassType(field.getOwner(), "java.lang.System")) {
                                    Expression exceptionPrintStackTrace = null;
                                    if (block.getStatements().size() > i + 1) {
                                        J next = block.getStatements().get(i + 1);
                                        if (next instanceof J.MethodInvocation && printStackTrace.matches((Expression) next)) {
                                            exceptionPrintStackTrace = ((J.MethodInvocation) next).getSelect();
                                            skip.set(i + 1);
                                        }
                                    }

                                    Cursor printCursor = new Cursor(blockCursor, m);
                                    J.MethodInvocation unchangedIfAddedLogger = logInsteadOfPrint(printCursor, ctx, exceptionPrintStackTrace);
                                    addedLogger.set(unchangedIfAddedLogger == m);
                                    return unchangedIfAddedLogger;
                                }
                            }
                        }
                    }
                    return stat;
                }));

                return addedLogger.get() ? block : b;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                if (systemErrPrint.matches((Expression) method)) {
                    if (getCursor().getParentOrThrow().getValue() instanceof J.Lambda) {
                        if (m.getSelect() != null && m.getSelect() instanceof J.FieldAccess) {
                            JavaType.Variable field = ((J.FieldAccess) m.getSelect()).getName().getFieldType();
                            if (field != null && "err".equals(field.getName()) && TypeUtils.isOfClassType(field.getOwner(), "java.lang.System")) {
                                Cursor printCursor = new Cursor(getCursor().getParent(), m);
                                return logInsteadOfPrint(printCursor, ctx, null);
                            }
                        }
                    }
                }
                return m;
            }

            private J.MethodInvocation logInsteadOfPrint(Cursor printCursor, ExecutionContext ctx, @Nullable Expression exceptionPrintStackTrace) {
                J.MethodInvocation print = printCursor.getValue();
                J.ClassDeclaration clazz = getCursor().firstEnclosingOrThrow(J.ClassDeclaration.class);
                Set<J.VariableDeclarations> loggers = FindFieldsOfType.find(clazz, framework.getLoggerType());
                if (!loggers.isEmpty()) {
                    J.Identifier computedLoggerName = loggers.iterator().next().getVariables().get(0).getName();
                    print = replaceMethodInvocation(printCursor, ctx, exceptionPrintStackTrace, print, computedLoggerName);
                } else if (clazz.getAllAnnotations().stream().anyMatch(lombokLogAnnotationMatcher::matches)) {
                    String fieldName = loggerName == null ? "log" : loggerName;
                    J.Identifier logField = new J.Identifier(UUID.randomUUID(), Space.SINGLE_SPACE, Markers.EMPTY, Collections.emptyList(), fieldName, null, null);
                    print = replaceMethodInvocation(printCursor, ctx, exceptionPrintStackTrace, print, logField);
                } else if (addLogger != null && addLogger) {
                    doAfterVisit(AddLogger.addLogger(clazz, framework, loggerName == null ? "logger" : loggerName));
                }
                return print;
            }

            private J.MethodInvocation replaceMethodInvocation(Cursor printCursor, ExecutionContext ctx, @Nullable Expression exceptionPrintStackTrace, J.MethodInvocation print, J.Identifier computedLoggerName) {
                if (exceptionPrintStackTrace == null) {
                    print = getErrorTemplateNoException()
                            .apply(
                                    printCursor,
                                    print.getCoordinates().replace(),
                                    computedLoggerName,
                                    print.getArguments().get(0));
                } else {
                    print = framework.getErrorTemplate(this, "#{any(String)}")
                            .apply(
                                    printCursor,
                                    print.getCoordinates().replace(),
                                    computedLoggerName,
                                    print.getArguments().get(0),
                                    exceptionPrintStackTrace);
                }

                if (framework == LoggingFramework.JUL) {
                    maybeAddImport("java.util.logging.Level");
                }

                return (J.MethodInvocation) new ParameterizedLogging(framework.getLoggerType() + " error(..)", false)
                        .getVisitor()
                        .visitNonNull(print, ctx, printCursor);
            }

            public JavaTemplate getErrorTemplateNoException() {
                switch (framework) {
                    case SLF4J:
                        return JavaTemplate
                                .builder("#{any(org.slf4j.Logger)}.error(#{any(String)})")
                                .contextSensitive()
                                .javaParser(JavaParser.fromJavaVersion().classpath("slf4j-api"))
                                .build();
                    case Log4J1:
                        return JavaTemplate
                                .builder("#{any(org.apache.log4j.Category)}.error(#{any(String)})")
                                .contextSensitive()
                                .javaParser(JavaParser.fromJavaVersion().classpath("log4j"))
                                .build();

                    case Log4J2:
                        return JavaTemplate
                                .builder("#{any(org.apache.logging.log4j.Logger)}.error(#{any(String)})")
                                .contextSensitive()
                                .javaParser(JavaParser.fromJavaVersion().classpath("log4j-api"))
                                .build();
                    case JUL:
                    default:
                        return JavaTemplate
                                .builder("#{any(java.util.logging.Logger)}.log(Level.SEVERE, #{any(String)})")
                                .contextSensitive()
                                .imports("java.util.logging.Level")
                                .build();
                }
            }
        });
    }
}
