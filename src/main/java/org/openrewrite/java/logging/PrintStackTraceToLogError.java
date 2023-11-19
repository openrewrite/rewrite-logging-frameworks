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
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.FindFieldsOfType;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;
import org.openrewrite.marker.Markers;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

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
            valid = {"SLF4J", "Log4J1", "Log4J2", "JUL", "COMMONS"},
            required = false)
    @Nullable
    String loggingFramework;

    @Override
    public String getDisplayName() {
        return "Use logger instead of `printStackTrace()`";
    }

    @Override
    public String getDescription() {
        return "When a logger is present, log exceptions rather than calling `printStackTrace()`.";
    }

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
                    J.ClassDeclaration clazz = getCursor().firstEnclosingOrThrow(J.ClassDeclaration.class);
                    Set<J.VariableDeclarations> loggers = FindFieldsOfType.find(clazz, framework.getLoggerType());
                    if (!loggers.isEmpty()) {
                        J.Identifier logField = loggers.iterator().next().getVariables().get(0).getName();
                        m = replaceMethodInvocation(m, logField);
                    } else if (clazz.getAllAnnotations().stream().anyMatch(lombokLogAnnotationMatcher::matches)) {
                        String fieldName = loggerName == null ? "log" : loggerName;
                        J.Identifier logField = new J.Identifier(UUID.randomUUID(), Space.SINGLE_SPACE, Markers.EMPTY, Collections.emptyList(), fieldName, null, null);
                        m = replaceMethodInvocation(m, logField);
                    } else if (addLogger != null && addLogger) {
                        doAfterVisit(AddLogger.addLogger(clazz, framework, loggerName == null ? "logger" : loggerName));
                    }
                }
                return m;
            }

            private J.MethodInvocation replaceMethodInvocation(J.MethodInvocation m, J.Identifier logField) {
                if (framework == LoggingFramework.JUL) {
                    maybeAddImport("java.util.logging.Level");
                }
                return framework.getErrorTemplate("\"Exception\"").apply(
                        new Cursor(getCursor().getParent(), m),
                        m.getCoordinates().replace(),
                        logField,
                        m.getSelect());
            }
        };
        return addLogger != null && addLogger ? visitor : Preconditions.check(
                Preconditions.or(
                        new UsesType<>(framework.getLoggerType(), null),
                        new UsesType<>("lombok.extern..*", null))
                , visitor);
    }
}
