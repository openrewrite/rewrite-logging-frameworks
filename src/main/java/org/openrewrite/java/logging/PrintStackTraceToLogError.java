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
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.FindFieldsOfType;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;

import java.time.Duration;
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
            valid = {"SLF4J", "Log4J", "Log4J2", "JUL"},
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
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(5);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        MethodMatcher printStackTrace = new MethodMatcher("java.lang.Throwable printStackTrace(..)");
        LoggingFramework framework = LoggingFramework.fromOption(loggingFramework);

        JavaIsoVisitor<ExecutionContext> visitor = new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                if (printStackTrace.matches(m)) {
                    J.ClassDeclaration clazz = getCursor().firstEnclosingOrThrow(J.ClassDeclaration.class);
                    Set<J.VariableDeclarations> loggers = FindFieldsOfType.find(clazz, framework.getLoggerType());
                    if (!loggers.isEmpty()) {
                        m = m.withTemplate(framework.getErrorTemplate(this, "\"Exception\""),
                                getCursor(),
                                m.getCoordinates().replace(),
                                loggers.iterator().next().getVariables().get(0).getName(),
                                m.getSelect());
                        if (framework == LoggingFramework.JUL) {
                            maybeAddImport("java.util.logging.Level");
                        }
                    } else if (addLogger != null && addLogger) {
                        doAfterVisit(AddLogger.addLogger(clazz, framework, loggerName == null ? "logger" : loggerName));
                    }
                }
                return m;
            }
        };

        return addLogger != null && addLogger ? visitor : Preconditions.check(new UsesType<>(framework.getLoggerType(), null), visitor);
    }
}
