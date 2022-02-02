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
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.*;
import org.openrewrite.java.search.FindFieldsOfType;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Value
@EqualsAndHashCode(callSuper = false)
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
            required = false)
    @Nullable
    String loggerName;

    @Option(displayName = "Logging framework",
            description = "The logging framework to use.",
            valid = {"SLF4J", "Log4J", "Log4J2", "JUL"},
            required = false)
    @Nullable
    String loggingFramework;

    @Override
    public String getDisplayName() {
        return "Use logger instead of system print statements";
    }

    @Override
    public String getDescription() {
        return "Replace `System.err` print statements with a logger.";
    }

    @Override
    protected JavaVisitor<ExecutionContext> getSingleSourceApplicableTest() {
        if (addLogger != null && addLogger) {
            return null;
        }

        return new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitCompilationUnit(J.CompilationUnit cu, ExecutionContext executionContext) {
                doAfterVisit(new UsesMethod<>(systemErrPrint));
                return cu;
            }
        };
    }

    @Override
    public JavaVisitor<ExecutionContext> getVisitor() {
        LoggingFramework framework = LoggingFramework.fromOption(loggingFramework);

        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
                J.Block b = super.visitBlock(block, ctx);

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
                                if (field != null && field.getName().equals("err") && TypeUtils.isOfClassType(field.getOwner(), "java.lang.System")) {
                                    Expression exceptionPrintStackTrace = null;
                                    if (block.getStatements().size() > i + 1) {
                                        J next = block.getStatements().get(i + 1);
                                        if (next instanceof J.MethodInvocation && printStackTrace.matches((Expression) next)) {
                                            exceptionPrintStackTrace = ((J.MethodInvocation) next).getSelect();
                                            skip.set(i + 1);
                                        }
                                    }

                                    J.MethodInvocation unchangedIfAddedLogger = logInsteadOfPrint(m, ctx, exceptionPrintStackTrace);
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
                            if (field != null && field.getName().equals("err") && TypeUtils.isOfClassType(field.getOwner(), "java.lang.System")) {
                                return logInsteadOfPrint(m, ctx, null);
                            }
                        }
                    }
                }
                return m;
            }

            private J.MethodInvocation logInsteadOfPrint(J.MethodInvocation print, ExecutionContext ctx, @Nullable Expression exceptionPrintStackTrace) {
                J.ClassDeclaration clazz = getCursor().firstEnclosingOrThrow(J.ClassDeclaration.class);
                Set<J.VariableDeclarations> loggers = FindFieldsOfType.find(clazz, framework.getLoggerType());
                if (!loggers.isEmpty()) {
                    J.Identifier computedLoggerName = loggers.iterator().next().getVariables().get(0).getName();
                    if (exceptionPrintStackTrace == null) {
                        print = print.withTemplate(getErrorTemplateNoException(this),
                                print.getCoordinates().replace(),
                                computedLoggerName,
                                print.getArguments().get(0));
                    } else {
                        print = print.withTemplate(framework.getErrorTemplate(this, "#{any(String)}"),
                                print.getCoordinates().replace(),
                                computedLoggerName,
                                print.getArguments().get(0),
                                exceptionPrintStackTrace);
                    }

                    print = (J.MethodInvocation) new ParameterizedLogging(framework.getLoggerType() + " error(..)")
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

            public <P> JavaTemplate getErrorTemplateNoException(JavaVisitor<P> visitor) {
                switch (framework) {
                    case SLF4J:
                        return JavaTemplate
                                .builder(visitor::getCursor, "#{any(org.slf4j.Logger)}.error(#{any(String)})")
                                .javaParser(() -> JavaParser.fromJavaVersion()
                                        .classpath("slf4j-api")
                                        .build()
                                )
                                .build();
                    case Log4J1:
                        return JavaTemplate
                                .builder(visitor::getCursor, "#{any(org.apache.log4j.Category)}.error(#{any(String)})")
                                .javaParser(() -> JavaParser.fromJavaVersion()
                                        .classpath("log4j")
                                        .build()
                                )
                                .build();

                    case Log4J2:
                        return JavaTemplate
                                .builder(visitor::getCursor, "#{any(org.apache.logging.log4j.Logger)}.error(#{any(String)})")
                                .javaParser(() -> JavaParser.fromJavaVersion()
                                        .classpath("log4j-api")
                                        .build()
                                )
                                .build();
                    case JUL:
                    default:
                        return JavaTemplate
                                .builder(visitor::getCursor, "#{any(java.util.logging.Logger)}.log(Level.SEVERE, #{any(String)})")
                                .imports("java.util.logging.Level")
                                .build();
                }
            }
        };
    }
}
