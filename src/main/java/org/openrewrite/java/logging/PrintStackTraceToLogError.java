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
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.service.AnnotationService;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

@EqualsAndHashCode(callSuper = false)
@Value
public class PrintStackTraceToLogError extends Recipe {
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

    String displayName = "Use logger instead of `printStackTrace()`";

    String description = "When a logger is present, log exceptions rather than calling `printStackTrace()`.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        MethodMatcher printStackTrace = new MethodMatcher("java.lang.Throwable printStackTrace(..)");
        LoggingFramework framework = LoggingFramework.fromOption(loggingFramework);
        AnnotationMatcher lombokLogAnnotationMatcher = new AnnotationMatcher("@lombok.extern..*");

        JavaIsoVisitor<ExecutionContext> visitor = new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                if (printStackTrace.matches(m) && m.getSelect() != null) {
                    // Only transform no-arg calls or calls with System.err/System.out
                    if (!m.getArguments().isEmpty() && !(m.getArguments().get(0) instanceof J.Empty)) {
                        Expression arg = m.getArguments().get(0);
                        if (!(arg instanceof J.FieldAccess)) {
                            return m;
                        }
                        J.FieldAccess fa = (J.FieldAccess) arg;
                        if (!TypeUtils.isOfClassType(fa.getTarget().getType(), "java.lang.System") ||
                            (!"err".equals(fa.getSimpleName()) && !"out".equals(fa.getSimpleName()))) {
                            return m;
                        }
                    }
                    Cursor classCursor = getCursor().dropParentUntil(J.ClassDeclaration.class::isInstance);
                    AnnotationService annotationService = service(AnnotationService.class);
                    boolean lombok = annotationService.matches(classCursor, lombokLogAnnotationMatcher);
                    J.Identifier logger = framework.loggerAt(getCursor(), lombok ? (loggerName == null ? "log" : loggerName) : null);
                    if (logger != null) {
                        m = replaceMethodInvocation(m, logger, ctx);
                    } else if (!lombok && addLogger != null && addLogger) {
                        doAfterVisit(AddLogger.addLogger(classCursor.getValue(), framework, loggerName == null ? "logger" : loggerName, ctx));
                    }
                }
                return m;
            }

            private J.MethodInvocation replaceMethodInvocation(J.MethodInvocation m, J.Identifier logField, ExecutionContext ctx) {
                maybeAddImport("java.util.logging.Level");
                maybeAddImport("java.lang.System.Logger.Level");

                return framework.getErrorTemplate("\"Exception\"", ctx).apply(
                        new Cursor(getCursor().getParent(), m),
                        m.getCoordinates().replace(),
                        logField,
                        m.getSelect());
            }
        };
        return Repeat.repeatUntilStable(addLogger != null && addLogger ? visitor : Preconditions.check(
                Preconditions.or(
                        new UsesType<>(framework.getLoggerType(), null),
                        new UsesType<>("lombok.extern..*", null))
                , visitor));
    }
}
