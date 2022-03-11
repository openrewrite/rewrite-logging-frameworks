/*
 * Copyright 2021 the original author or authors.
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
package org.openrewrite.java.logging.slf4j;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;

import java.time.Duration;

@Value
@EqualsAndHashCode(callSuper = true)
public class ConvertLogMessageMessageOnlyToLogMessageAndThrowable extends Recipe {

    @Option(displayName = "Log message",
            description = "The message accompanying the exception.",
            required = false)
    @Nullable
    String logMessage;

    @Override
    public String getDisplayName() {
        return "Convert Logger#error|warn(Throwable#message) to Logger#error|warn(<log-message>, e)";
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(5);
    }

    @Override
    public String getDescription() {
        return "Convert `Logger#error|warn(throwable#message)` to `Logger#error|warn(<log-message>, e)` invocations having only the error's message as the parameter, to a log statement with message and throwable";
    }

    @Override
    protected UsesType<ExecutionContext> getSingleSourceApplicableTest() {
        return new UsesType<>("org.slf4j.Logger");
    }

    @Override
    protected JavaIsoVisitor<ExecutionContext> getVisitor() {
        final MethodMatcher logErrorMatcher = new MethodMatcher("org.slf4j.Logger error(String)");
        final MethodMatcher logWarningMatcher = new MethodMatcher("org.slf4j.Logger warn(String)");
        final MethodMatcher getMessageMatcher = new MethodMatcher("java.lang.Throwable getMessage()");
        final MethodMatcher getLocalizedMessageMatcher = new MethodMatcher("java.lang.Throwable getLocalizedMessage()");
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, executionContext);
                if ((logErrorMatcher.matches(mi) || logWarningMatcher.matches(mi))
                        && (mi.getArguments().get(0) instanceof J.MethodInvocation
                        && (getMessageMatcher.matches(mi.getArguments().get(0)) || getLocalizedMessageMatcher.matches(mi.getArguments().get(0))))) {
                    J throwableMessage = ((J.MethodInvocation) mi.getArguments().get(0)).getSelect();
                    String type = mi.getSimpleName();
                    String message = logMessage == null ? "" : logMessage;
                    mi = mi.withTemplate(
                            JavaTemplate.builder(this::getCursor, "#{any(org.slf4j.Logger)}.#{}(\"#{}\", #{any(java.lang.Throwable)}")
                                    .javaParser(() -> JavaParser.fromJavaVersion().classpath("slf4j-api").build()).build(), mi.getCoordinates().replace(),
                            mi.getSelect(), type, message, throwableMessage);
                }
                return mi;
            }
        };
    }
}
