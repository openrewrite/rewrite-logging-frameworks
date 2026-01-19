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
import org.openrewrite.java.search.FindFieldsOfType;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.service.AnnotationService;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;
import org.openrewrite.marker.Markers;

import java.util.Set;

import static java.util.Collections.emptyList;

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
                if (printStackTrace.matches(m)) {
                    Cursor classCursor = getCursor().dropParentUntil(J.ClassDeclaration.class::isInstance);
                    AnnotationService annotationService = service(AnnotationService.class);
                    Set<J.VariableDeclarations> loggers = FindFieldsOfType.find(classCursor.getValue(), framework.getLoggerType());
                    if (!loggers.isEmpty()) {
                        J.Identifier logField = loggers.iterator().next().getVariables().get(0).getName();
                        m = replaceMethodInvocation(m, logField, ctx);
                    } else if (annotationService.matches(classCursor, lombokLogAnnotationMatcher)) {
                        String fieldName = loggerName == null ? "log" : loggerName;
                        J.Identifier logField = new J.Identifier(Tree.randomId(), Space.SINGLE_SPACE, Markers.EMPTY, emptyList(), fieldName, null, null);
                        m = replaceMethodInvocation(m, logField, ctx);
                    } else if (addLogger != null && addLogger) {
                        doAfterVisit(AddLogger.addLogger(classCursor.getValue(), framework, loggerName == null ? "logger" : loggerName, ctx));
                    }
                }
                return m;
            }

            private J.MethodInvocation replaceMethodInvocation(J.MethodInvocation m, J.Identifier logField, ExecutionContext ctx) {
                if (framework == LoggingFramework.JUL) {
                    maybeAddImport("java.util.logging.Level");
                }
                if (framework == LoggingFramework.SYSTEM) {
                    maybeAddImport("java.lang.System.Logger.Level");
                }
                
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
