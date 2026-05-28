/*
 * Copyright 2026 the original author or authors.
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
package org.openrewrite.java.logging.slf4j;

import com.google.errorprone.refaster.annotation.AfterTemplate;
import com.google.errorprone.refaster.annotation.BeforeTemplate;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.openrewrite.java.template.RecipeDescriptor;

@RecipeDescriptor(
        name = "Replace Log4j 2.x active Level check with corresponding SLF4J method calls",
        description = "Replace calls to `Logger.isEnabled(Level)` with the corresponding SLF4J method calls."
)
public class Log4j2IsEnabledToSlf4j {
    @RecipeDescriptor(
            name = "Replace Log4j 2.x `Logger.isEnabled(Level.TRACE)` with SLF4J's `Logger.isTraceEnabled()`",
            description = "Replace calls to `org.apache.logging.log4j.Logger.isEnabled(Level.TRACE)` (or `Level.ALL`) with `org.slf4j.Logger.isTraceEnabled()`, " +
                          "since SLF4J has no `ALL` level."
    )
    public static class LoggerIsEnabledLevelTrace {
        @BeforeTemplate
        boolean trace(Logger logger) {
            return logger.isEnabled(Level.TRACE);
        }

        @BeforeTemplate
        boolean all(Logger logger) {
            return logger.isEnabled(Level.ALL);
        }

        @AfterTemplate
        boolean after(org.slf4j.Logger logger) {
            return logger.isTraceEnabled();
        }
    }

    @RecipeDescriptor(
            name = "Replace Log4j 2.x `Logger.isEnabled(Level.DEBUG)` with SLF4J's `Logger.isDebugEnabled()`",
            description = "Replace calls to `org.apache.logging.log4j.Logger.isEnabled(Level.DEBUG)` with `org.slf4j.Logger.isDebugEnabled()`."
    )
    public static class LoggerIsEnabledLevelDebug {
        @BeforeTemplate
        boolean before(Logger logger) {
            return logger.isEnabled(Level.DEBUG);
        }

        @AfterTemplate
        boolean after(org.slf4j.Logger logger) {
            return logger.isDebugEnabled();
        }
    }

    @RecipeDescriptor(
            name = "Replace Log4j 2.x `Logger.isEnabled(Level.INFO)` with SLF4J's `Logger.isInfoEnabled()`",
            description = "Replace calls to `org.apache.logging.log4j.Logger.isEnabled(Level.INFO)` with `org.slf4j.Logger.isInfoEnabled()`."
    )
    public static class LoggerIsEnabledLevelInfo {
        @BeforeTemplate
        boolean before(Logger logger) {
            return logger.isEnabled(Level.INFO);
        }

        @AfterTemplate
        boolean after(org.slf4j.Logger logger) {
            return logger.isInfoEnabled();
        }
    }

    @RecipeDescriptor(
            name = "Replace Log4j 2.x `Logger.isEnabled(Level.WARN)` with SLF4J's `Logger.isWarnEnabled()`",
            description = "Replace calls to `org.apache.logging.log4j.Logger.isEnabled(Level.WARN)` with `org.slf4j.Logger.isWarnEnabled()`."
    )
    public static class LoggerIsEnabledLevelWarn {
        @BeforeTemplate
        boolean before(Logger logger) {
            return logger.isEnabled(Level.WARN);
        }

        @AfterTemplate
        boolean after(org.slf4j.Logger logger) {
            return logger.isWarnEnabled();
        }
    }

    @RecipeDescriptor(
            name = "Replace Log4j 2.x `Logger.isEnabled(Level.ERROR)` with SLF4J's `Logger.isErrorEnabled()`",
            description = "Replace calls to `org.apache.logging.log4j.Logger.isEnabled(Level.ERROR)` (or `Level.FATAL`) with `org.slf4j.Logger.isErrorEnabled()`, " +
                          "since SLF4J has no `FATAL` level."
    )
    public static class LoggerIsEnabledLevelError {
        @BeforeTemplate
        boolean error(Logger logger) {
            return logger.isEnabled(Level.ERROR);
        }

        @BeforeTemplate
        boolean fatal(Logger logger) {
            return logger.isEnabled(Level.FATAL);
        }

        @AfterTemplate
        boolean after(org.slf4j.Logger logger) {
            return logger.isErrorEnabled();
        }
    }
}
