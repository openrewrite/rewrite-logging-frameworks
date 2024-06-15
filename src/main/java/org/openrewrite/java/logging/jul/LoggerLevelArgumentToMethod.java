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

import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

@RecipeDescriptor(
        name = "Replace JUL Level arguments with the corresponding method calls",
        description = "Replace calls to `Logger.log(Level, String)` with the corresponding method calls."
)
public class LoggerLevelArgumentToMethod {
    @RecipeDescriptor(
            name = "Replace JUL `Logger.log(Level.FINEST, String)` with `Logger.finest(String)`",
            description = "Replace calls to `java.util.logging.Logger.log(Level.FINEST, String)` with `Logger.finest(String)`."
    )
    public static class LogLevelFinestToMethod {
        @BeforeTemplate
        void before(Logger logger, String message) {
            logger.log(Level.FINEST, message);
        }

        @AfterTemplate
        void after(Logger logger, String message) {
            logger.finest(message);
        }
    }

    @RecipeDescriptor(
            name = "Replace JUL `Logger.log(Level.FINEST, Supplier<String>)` with `Logger.finest(Supplier<String>)`",
            description = "Replace calls to `java.util.logging.Logger.log(Level.FINEST, Supplier<String>)` with `Logger.finest(Supplier<String>)`."
    )
    public static class LogLevelFinestSupplierToMethod {
        @BeforeTemplate
        void before(Logger logger, Supplier<String> message) {
            logger.log(Level.FINEST, message);
        }

        @AfterTemplate
        void after(Logger logger, Supplier<String> message) {
            logger.finest(message);
        }
    }

    @RecipeDescriptor(
            name = "Replace JUL `Logger.log(Level.FINER, String)` with `Logger.finer(String)`",
            description = "Replace calls to `java.util.logging.Logger.log(Level.FINER, String)` with `Logger.finer(String)`."
    )
    public static class LogLevelFinerToMethod {
        @BeforeTemplate
        void before(Logger logger, String message) {
            logger.log(Level.FINER, message);
        }

        @AfterTemplate
        void after(Logger logger, String message) {
            logger.finer(message);
        }
    }


    @RecipeDescriptor(
            name = "Replace JUL `Logger.log(Level.FINER, Supplier<String>)` with `Logger.finer(Supplier<String>)`",
            description = "Replace calls to `java.util.logging.Logger.log(Level.FINER, Supplier<String>)` with `Logger.finer(Supplier<String>)`."
    )
    public static class LogLevelFinerSupplierToMethod {
        @BeforeTemplate
        void before(Logger logger, Supplier<String> message) {
            logger.log(Level.FINER, message);
        }

        @AfterTemplate
        void after(Logger logger, Supplier<String> message) {
            logger.finer(message);
        }
    }

    @RecipeDescriptor(
            name = "Replace JUL `Logger.log(Level.FINE, String)` with `Logger.fine(String)`",
            description = "Replace calls to `java.util.logging.Logger.log(Level.FINE, String)` with `Logger.fine(String)`."
    )
    public static class LogLevelFineToMethod {
        @BeforeTemplate
        void before(Logger logger, String message) {
            logger.log(Level.FINE, message);
        }

        @AfterTemplate
        void after(Logger logger, String message) {
            logger.fine(message);
        }
    }

    @RecipeDescriptor(
            name = "Replace JUL `Logger.log(Level.FINE, Supplier<String>)` with `Logger.fine(Supplier<String>)`",
            description = "Replace calls to `java.util.logging.Logger.log(Level.FINE, Supplier<String>)` with `Logger.fine(Supplier<String>)`."
    )
    public static class LogLevelFineSupplierToMethod {
        @BeforeTemplate
        void before(Logger logger, Supplier<String> message) {
            logger.log(Level.FINE, message);
        }

        @AfterTemplate
        void after(Logger logger, Supplier<String> message) {
            logger.fine(message);
        }
    }

    @RecipeDescriptor(
            name = "Replace JUL `Logger.log(Level.INFO, String)` with `Logger.info(String)`",
            description = "Replace calls to `java.util.logging.Logger.log(Level.INFO, String)` with `Logger.info(String)`."
    )
    public static class LogLevelInfoToMethod {
        @BeforeTemplate
        void before(Logger logger, String message) {
            logger.log(Level.INFO, message);
        }

        @AfterTemplate
        void after(Logger logger, String message) {
            logger.info(message);
        }
    }

    @RecipeDescriptor(
            name = "Replace JUL `Logger.log(Level.INFO, Supplier<String>)` with `Logger.info(Supplier<String>)`",
            description = "Replace calls to `java.util.logging.Logger.log(Level.INFO, Supplier<String>)` with `Logger.info(Supplier<String>)`."
    )
    public static class LogLevelInfoSupplierToMethod {
        @BeforeTemplate
        void before(Logger logger, Supplier<String> message) {
            logger.log(Level.INFO, message);
        }

        @AfterTemplate
        void after(Logger logger, Supplier<String> message) {
            logger.info(message);
        }
    }

    @RecipeDescriptor(
            name = "Replace JUL `Logger.log(Level.WARNING, String)` with `Logger.warning(String)`",
            description = "Replace calls to `java.util.logging.Logger.log(Level.WARNING, String)` with `Logger.warning(String)`."
    )
    public static class LogLevelWarningToMethod {
        @BeforeTemplate
        void before(Logger logger, String message) {
            logger.log(Level.WARNING, message);
        }

        @AfterTemplate
        void after(Logger logger, String message) {
            logger.warning(message);
        }
    }

    @RecipeDescriptor(
            name = "Replace JUL `Logger.log(Level.WARNING, Supplier<String>)` with `Logger.warning(Supplier<String>)`",
            description = "Replace calls to `java.util.logging.Logger.log(Level.WARNING, Supplier<String>)` with `Logger.warning(Supplier<String>)`."
    )
    public static class LogLevelWarningSupplierToMethod {
        @BeforeTemplate
        void before(Logger logger, Supplier<String> message) {
            logger.log(Level.WARNING, message);
        }

        @AfterTemplate
        void after(Logger logger, Supplier<String> message) {
            logger.warning(message);
        }
    }

    @RecipeDescriptor(
            name = "Replace JUL `Logger.log(Level.SEVERE, String)` with `Logger.severe(String)`",
            description = "Replace calls to `java.util.logging.Logger.log(Level.SEVERE, String)` with `Logger.severe(String)`."
    )
    public static class LogLevelSevereToMethod {
        @BeforeTemplate
        void before(Logger logger, String message) {
            logger.log(Level.SEVERE, message);
        }

        @AfterTemplate
        void after(Logger logger, String message) {
            logger.severe(message);
        }
    }

    @RecipeDescriptor(
            name = "Replace JUL `Logger.log(Level.SEVERE, Supplier<String>)` with `Logger.severe(Supplier<String>)`",
            description = "Replace calls to `java.util.logging.Logger.log(Level.SEVERE, Supplier<String>)` with `Logger.severe(Supplier<String>)`."
    )
    public static class LogLevelSevereSupplierToMethod {
        @BeforeTemplate
        void before(Logger logger, Supplier<String> message) {
            logger.log(Level.SEVERE, message);
        }

        @AfterTemplate
        void after(Logger logger, Supplier<String> message) {
            logger.severe(message);
        }
    }

    @RecipeDescriptor(
            name = "Replace JUL `Logger.log(Level.CONFIG, String)` with `Logger.config(String)`",
            description = "Replace calls to `java.util.logging.Logger.log(Level.CONFIG, String)` with `Logger.config(String)`."
    )
    public static class LogLevelConfigToMethod {
        @BeforeTemplate
        void before(Logger logger, String message) {
            logger.log(Level.CONFIG, message);
        }

        @AfterTemplate
        void after(Logger logger, String message) {
            logger.config(message);
        }
    }

    @RecipeDescriptor(
            name = "Replace JUL `Logger.log(Level.CONFIG, Supplier<String>)` with `Logger.config(Supplier<String>)`",
            description = "Replace calls to `java.util.logging.Logger.log(Level.CONFIG, Supplier<String>)` with `Logger.config(Supplier<String>)`."
    )
    public static class LogLevelConfigSupplierToMethod {
        @BeforeTemplate
        void before(Logger logger, Supplier<String> message) {
            logger.log(Level.CONFIG, message);
        }

        @AfterTemplate
        void after(Logger logger, Supplier<String> message) {
            logger.config(message);
        }
    }
}
