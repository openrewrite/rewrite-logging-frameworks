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
package org.openrewrite.java.logging.slf4j;

import com.google.errorprone.refaster.annotation.AfterTemplate;
import com.google.errorprone.refaster.annotation.BeforeTemplate;
import org.openrewrite.java.template.RecipeDescriptor;

import java.util.logging.Level;
import java.util.logging.Logger;

@RecipeDescriptor(
        name = "Replace JUL active Level check with corresponding SLF4J method calls",
        description = "Replace calls to `Logger.isLoggable(Level)` with the corresponding SLF4J method calls."
)
public class JulToSlf4jSimpleCallsWithThrowable {
    @RecipeDescriptor(
            name = "Replace JUL `logger.log(Level.FINEST, String message, Throwable e)` with SLF4J's `Logger.trace(message, e)`",
            description = "Replace calls to `java.util.logging.Logger.log(Level.FINEST, String message, Throwable e)` with `org.slf4j.Logger.trace(message, e)`."
    )
    public static class JulToSlf4jSupplierFinest {
        @BeforeTemplate
        void before(Logger logger, String message, Throwable e) {
            logger.log(Level.FINEST, message, e);
        }

        @AfterTemplate
        void after(org.slf4j.Logger logger, String message, Throwable e) {
            logger.trace(message, e);
        }
    }

    @RecipeDescriptor(
            name = "Replace JUL `logger.log(Level.FINER, String message, Throwable e)` with SLF4J's `Logger.trace(message, e)`",
            description = "Replace calls to `java.util.logging.Logger.log(Level.FINER, String message, Throwable e)` with `org.slf4j.Logger.trace(message, e)`."
    )
    public static class JulToSlf4jSupplierFiner {
        @BeforeTemplate
        void before(Logger logger, String message, Throwable e) {
            logger.log(Level.FINER, message, e);
        }

        @AfterTemplate
        void after(org.slf4j.Logger logger, String message, Throwable e) {
            logger.trace(message, e);
        }
    }

    @RecipeDescriptor(
            name = "Replace JUL `logger.log(Level.FINE, String message, Throwable e)` with SLF4J's `Logger.debug(message, e)`",
            description = "Replace calls to `java.util.logging.Logger.log(Level.FINE, String message, Throwable e)` with `org.slf4j.Logger.debug(message, e)`."
    )
    public static class JulToSlf4jSupplierFine {
        @BeforeTemplate
        void before(Logger logger, String message, Throwable e) {
            logger.log(Level.FINE, message, e);
        }

        @AfterTemplate
        void after(org.slf4j.Logger logger, String message, Throwable e) {
            logger.debug(message, e);
        }
    }

    @RecipeDescriptor(
            name = "Replace JUL `logger.log(Level.CONFIG, String message, Throwable e)` with SLF4J's `Logger.info(message, e)`",
            description = "Replace calls to `java.util.logging.Logger.log(Level.CONFIG, String message, Throwable e)` with `org.slf4j.Logger.info(message, e)`."
    )
    public static class JulToSlf4jSupplierConfig {
        @BeforeTemplate
        void before(Logger logger, String message, Throwable e) {
            logger.log(Level.CONFIG, message, e);
        }

        @AfterTemplate
        void after(org.slf4j.Logger logger, String message, Throwable e) {
            logger.info(message, e);
        }
    }

    @RecipeDescriptor(
            name = "Replace JUL `logger.log(Level.INFO, String message, Throwable e)` with SLF4J's `Logger.info(message, e)`",
            description = "Replace calls to `java.util.logging.Logger.log(Level.INFO, String message, Throwable e)` with `org.slf4j.Logger.info(message, e)`."
    )
    public static class JulToSlf4jSupplierInfo {
        @BeforeTemplate
        void before(Logger logger, String message, Throwable e) {
            logger.log(Level.INFO, message, e);
        }

        @AfterTemplate
        void after(org.slf4j.Logger logger, String message, Throwable e) {
            logger.info(message, e);
        }
    }

    @RecipeDescriptor(
            name = "Replace JUL `logger.log(Level.WARNING, String message, Throwable e)` with SLF4J's `Logger.warn(message, e)`",
            description = "Replace calls to `java.util.logging.Logger.log(Level.WARNING, String message, Throwable e)` with `org.slf4j.Logger.warn(message, e)`."
    )
    public static class JulToSlf4jSupplierWarning {
        @BeforeTemplate
        void before(Logger logger, String message, Throwable e) {
            logger.log(Level.WARNING, message, e);
        }

        @AfterTemplate
        void after(org.slf4j.Logger logger, String message, Throwable e) {
            logger.warn(message, e);
        }
    }

    @RecipeDescriptor(
            name = "Replace JUL `logger.log(Level.SEVERE, String message, Throwable e)` with SLF4J's `Logger.error(message, e)`",
            description = "Replace calls to `java.util.logging.Logger.log(Level.SEVERE, String message, Throwable e)` with `org.slf4j.Logger.error(message, e)`."
    )
    public static class JulToSlf4jSupplierSevere {
        @BeforeTemplate
        void before(Logger logger, String message, Throwable e) {
            logger.log(Level.SEVERE, message, e);
        }

        @AfterTemplate
        void after(org.slf4j.Logger logger, String message, Throwable e) {
            logger.error(message, e);
        }
    }

    @RecipeDescriptor(
            name = "Replace JUL `logger.log(Level.ALL, String message, Throwable e)` with SLF4J's `Logger.trace(message, e)`",
            description = "Replace calls to `java.util.logging.Logger.log(Level.ALL, String message, Throwable e)` with `org.slf4j.Logger.trace(message, e)`."
    )
    public static class JulToSlf4jSupplierAll {
        @BeforeTemplate
        void before(Logger logger, String message, Throwable e) {
            logger.log(Level.ALL, message, e);
        }

        @AfterTemplate
        void after(org.slf4j.Logger logger, String message, Throwable e) {
            logger.trace(message, e);
        }
    }
}
