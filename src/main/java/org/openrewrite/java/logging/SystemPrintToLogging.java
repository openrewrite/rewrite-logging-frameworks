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
import lombok.Getter;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Option;
import org.openrewrite.Recipe;

import java.util.Arrays;
import java.util.List;

@EqualsAndHashCode(callSuper = false, exclude = "recipeList")
@Value
public class SystemPrintToLogging extends Recipe {
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

    @Option(displayName = "Level",
            description = "The logging level to turn `System.out` print statements into.",
            valid = {"trace", "debug", "info"},
            required = false)
    @Nullable
    String level;

    @Getter(lazy = true)
    List<Recipe> recipeList = Arrays.asList(
            new SystemErrToLogging(addLogger, loggerName, loggingFramework),
            new SystemOutToLogging(addLogger, loggerName, loggingFramework, level),
            new PrintStackTraceToLogError(addLogger, loggerName, loggingFramework)
    );

    @Override
    public String getDisplayName() {
        return "Use logger instead of system print statements";
    }

    @Override
    public String getDescription() {
        return "Replace `System.out` and `System.err` print statements with a logger.";
    }
}
