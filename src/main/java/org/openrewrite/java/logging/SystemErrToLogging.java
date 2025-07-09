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
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.*;
import org.openrewrite.java.search.FindFieldsOfType;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.service.AnnotationService;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@EqualsAndHashCode(callSuper = false)
@Value
public class SystemErrToLogging extends Recipe {
    private static final MethodMatcher systemErrPrint = new MethodMatcher("java.io.PrintStream print*(String)");
    private static final MethodMatcher printStackTrace = new MethodMatcher("java.lang.Throwable printStackTrace(..)");

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

        return Preconditions.check(new UsesMethod<>(systemErrPrint), Repeat.repeatUntilStable(new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
                J.Block b = super.visitBlock(block, ctx);
                Cursor blockCursor = new Cursor(getCursor().getParent(), b);

                AtomicBoolean addedLogger = new AtomicBoolean(false);
                b = b.withStatements(collapseNextThrowablePrintStackTrace(b.getStatements(), ctx, blockCursor, addedLogger));
                return addedLogger.get() ? block : b;
            }

            @Override
            public J.Case visitCase(J.Case _case, ExecutionContext ctx) {
                J.Case c = super.visitCase(_case, ctx);
                Cursor caseCursor = new Cursor(getCursor().getParent(), c);

                AtomicBoolean addedLogger = new AtomicBoolean(false);
                c = c.withStatements(collapseNextThrowablePrintStackTrace(c.getStatements(), ctx, caseCursor, addedLogger));
                return addedLogger.get() ? _case : c;
            }

            private List<Statement> collapseNextThrowablePrintStackTrace(List<Statement> statements, ExecutionContext ctx, Cursor cursor, AtomicBoolean addedLogger) {
                AtomicInteger skip = new AtomicInteger(-1);
                return ListUtils.map(statements, (i, stat) -> {
                    if (skip.get() == i) {
                        return null;
                    } else if (stat instanceof J.MethodInvocation) {
                        J.MethodInvocation m = (J.MethodInvocation) stat;
                        if (systemErrPrint.matches((Expression) stat)) {
                            if (m.getSelect() instanceof J.FieldAccess) {
                                JavaType.Variable field = ((J.FieldAccess) m.getSelect()).getName().getFieldType();
                                if (field != null && "err".equals(field.getName()) && TypeUtils.isOfClassType(field.getOwner(), "java.lang.System")) {
                                    Expression exceptionPrintStackTrace = null;
                                    if (statements.size() > i + 1) {
                                        J next = statements.get(i + 1);
                                        if (next instanceof J.MethodInvocation && printStackTrace.matches((Expression) next)) {
                                            exceptionPrintStackTrace = ((J.MethodInvocation) next).getSelect();
                                            skip.set(i + 1);
                                        }
                                    }

                                    Cursor printCursor = new Cursor(cursor, m);
                                    J.MethodInvocation unchangedIfAddedLogger = logInsteadOfPrint(printCursor, ctx, exceptionPrintStackTrace);
                                    addedLogger.set(unchangedIfAddedLogger == m);
                                    return unchangedIfAddedLogger;
                                }
                            }
                        }
                    }
                    return stat;
                });
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                if (systemErrPrint.matches((Expression) method)) {
                    if (getCursor().getParentOrThrow().getValue() instanceof J.Lambda) {
                        if (m.getSelect() instanceof J.FieldAccess) {
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
                Cursor classCursor = getCursor().dropParentUntil(J.ClassDeclaration.class::isInstance);
                AnnotationService annotationService = service(AnnotationService.class);
                Set<J.VariableDeclarations> loggers = FindFieldsOfType.find(classCursor.getValue(), framework.getLoggerType());
                if (!loggers.isEmpty()) {
                    J.Identifier computedLoggerName = loggers.iterator().next().getVariables().get(0).getName();
                    print = replaceMethodInvocation(printCursor, ctx, exceptionPrintStackTrace, print, computedLoggerName);
                } else if (annotationService.matches(classCursor, lombokLogAnnotationMatcher)) {
                    String fieldName = loggerName == null ? "log" : loggerName;
                    J.Identifier logField = new J.Identifier(Tree.randomId(), Space.SINGLE_SPACE, Markers.EMPTY, Collections.emptyList(), fieldName, null, null);
                    print = replaceMethodInvocation(printCursor, ctx, exceptionPrintStackTrace, print, logField);
                } else if (addLogger != null && addLogger) {
                    doAfterVisit(AddLogger.addLogger(classCursor.getValue(), framework, loggerName == null ? "logger" : loggerName, ctx));
                }
                return print;
            }

            private J.MethodInvocation replaceMethodInvocation(Cursor printCursor, ExecutionContext ctx, @Nullable Expression exceptionPrintStackTrace, J.MethodInvocation print, J.Identifier computedLoggerName) {
                if (exceptionPrintStackTrace == null) {
                    print = getErrorTemplateNoException(ctx)
                            .apply(
                                    printCursor,
                                    print.getCoordinates().replace(),
                                    computedLoggerName,
                                    print.getArguments().get(0));
                } else {
                    print = framework.getErrorTemplate("#{any(String)}", ctx)
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

            public JavaTemplate getErrorTemplateNoException(ExecutionContext ctx) {
                switch (framework) {
                    case SLF4J:
                        return JavaTemplate
                                .builder("#{any(org.slf4j.Logger)}.error(#{any(String)});")
                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "slf4j-api-2.1.+"))
                                .build();
                    case Log4J1:
                        return JavaTemplate
                                .builder("#{any(org.apache.log4j.Category)}.error(#{any(String)});")
                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "log4j-1.2.+"))
                                .build();

                    case Log4J2:
                        return JavaTemplate
                                .builder("#{any(org.apache.logging.log4j.Logger)}.error(#{any(String)});")
                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "log4j-api-2.+"))
                                .build();
                    case JUL:
                    default:
                        return JavaTemplate
                                .builder("#{any(java.util.logging.Logger)}.log(Level.SEVERE, #{any(String)});")
                                .imports("java.util.logging.Level")
                                .build();
                }
            }
        }));
    }
}
