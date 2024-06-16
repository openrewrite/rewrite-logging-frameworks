/*
 * Copyright 2024 the original author or authors.
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
package org.openrewrite.java.logging.jul;

import com.google.errorprone.refaster.annotation.AfterTemplate;
import com.google.errorprone.refaster.annotation.BeforeTemplate;
import org.openrewrite.java.template.RecipeDescriptor;

import java.util.logging.Level;
import java.util.logging.Logger;

@RecipeDescriptor(
        name = "Replace JUL active Level check with corresponding slf4j method calls",
        description = "Replace calls to `Logger.isLoggable(Level)` with the corresponding slf4j method calls."
)
public class LoggerIsLoggable {
    @RecipeDescriptor(
            name = "Replace JUL `Logger.isLoggable(Level.ALL)` with slf4j's `Logger.isTraceEnabled`",
            description = "Replace calls to `java.util.logging.Logger.isLoggable(Level.ALL)` with `org.slf4j.Logger.isTraceEnabled()`."
    )
    public static class LoggerIsLoggableLevelAll {
        @BeforeTemplate
        boolean before(Logger logger) {
            return logger.isLoggable(Level.ALL);
        }

        @AfterTemplate
        boolean after(org.slf4j.Logger logger) {
            return logger.isTraceEnabled();
        }
    }

    @RecipeDescriptor(
            name = "Replace JUL `Logger.isLoggable(Level.FINEST)` with slf4j's `Logger.isTraceEnabled`",
            description = "Replace calls to `java.util.logging.Logger.isLoggable(Level.FINEST)` with `org.slf4j.Logger.isTraceEnabled()`."
    )
    public static class LoggerIsLoggableLevelFinest {
        @BeforeTemplate
        boolean before(Logger logger) {
            return logger.isLoggable(Level.FINEST);
        }

        @AfterTemplate
        boolean after(org.slf4j.Logger logger) {
            return logger.isTraceEnabled();
        }
    }

    @RecipeDescriptor(
            name = "Replace JUL `Logger.isLoggable(Level.FINER)` with slf4j's `Logger.isTraceEnabled()`",
            description = "Replace calls to `java.util.logging.Logger.isLoggable(Level.FINER)` with `org.slf4j.Logger.isTraceEnabled()`."
    )
    public static class LoggerIsLoggableLevelFiner {
        @BeforeTemplate
        boolean before(Logger logger) {
            return logger.isLoggable(Level.FINER);
        }

        @AfterTemplate
        boolean after(org.slf4j.Logger logger) {
            return logger.isTraceEnabled();
        }
    }

    @RecipeDescriptor(
            name = "Replace JUL `Logger.isLoggable(Level.FINE)` with slf4j's `Logger.isDebugEnabled()`",
            description = "Replace calls to `java.util.logging.Logger.isLoggable(Level.FINE)` with `org.slf4j.Logger.isDebugEnabled()`."
    )
    public static class LoggerIsLoggableLevelFine {
        @BeforeTemplate
        boolean before(Logger logger) {
            return logger.isLoggable(Level.FINE);
        }

        @AfterTemplate
        boolean after(org.slf4j.Logger logger) {
            return logger.isDebugEnabled();
        }
    }

    @RecipeDescriptor(
            name = "Replace JUL `Logger.isLoggable(Level.CONFIG)` with slf4j's `Logger.isInfoEnabled()`",
            description = "Replace calls to `java.util.logging.Logger.isLoggable(Level.CONFIG)` with `org.slf4j.Logger.isInfoEnabled()`."
    )
    public static class LoggerIsLoggableLevelConfig {
        @BeforeTemplate
        boolean before(Logger logger) {
            return logger.isLoggable(Level.CONFIG);
        }

        @AfterTemplate
        boolean after(org.slf4j.Logger logger) {
            return logger.isInfoEnabled();
        }
    }

    @RecipeDescriptor(
            name = "Replace JUL `Logger.isLoggable(Level.INFO)` with slf4j's `Logger.isInfoEnabled()`",
            description = "Replace calls to `java.util.logging.Logger.isLoggable(Level.INFO)` with `org.slf4j.Logger.isInfoEnabled()`."
    )
    public static class LoggerIsLoggableLevelInfo {
        @BeforeTemplate
        boolean before(Logger logger) {
            return logger.isLoggable(Level.INFO);
        }

        @AfterTemplate
        boolean after(org.slf4j.Logger logger) {
            return logger.isInfoEnabled();
        }
    }

    @RecipeDescriptor(
            name = "Replace JUL `Logger.isLoggable(Level.WARNING)` with slf4j's `Logger.isWarnEnabled()`",
            description = "Replace calls to `java.util.logging.Logger.isLoggable(Level.WARNING)` with `org.slf4j.Logger.isWarnEnabled()`."
    )
    public static class LoggerIsLoggableLevelWarning {
        @BeforeTemplate
        boolean before(Logger logger) {
            return logger.isLoggable(Level.WARNING);
        }

        @AfterTemplate
        boolean after(org.slf4j.Logger logger) {
            return logger.isWarnEnabled();
        }
    }

    @RecipeDescriptor(
            name = "Replace JUL `Logger.isLoggable(Level.SEVERE)` with slf4j's `Logger.isErrorEnabled()`",
            description = "Replace calls to `java.util.logging.Logger.isLoggable(Level.SEVERE)` with `org.slf4j.Logger.isErrorEnabled()`."
    )
    public static class LoggerIsLoggableLevelSevere {
        @BeforeTemplate
        boolean before(Logger logger) {
            return logger.isLoggable(Level.SEVERE);
        }

        @AfterTemplate
        boolean after(org.slf4j.Logger logger) {
            return logger.isErrorEnabled();
        }
    }
}
