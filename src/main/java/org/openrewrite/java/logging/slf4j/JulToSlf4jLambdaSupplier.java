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

import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

@RecipeDescriptor(
        name = "Replace JUL active Level check with corresponding SLF4J method calls",
        description = "Replace calls to `Logger.isLoggable(Level)` with the corresponding SLF4J method calls."
)
public class JulToSlf4jLambdaSupplier {
    @RecipeDescriptor(
            name = "Replace JUL `Logger.finest(Supplier<String>)` with SLF4J's `Logger.atTrace().log(Supplier<String>)`",
            description = "Replace calls to `java.util.logging.Logger.finest(Supplier<String>)` with `org.slf4j.Logger.atTrace().log(Supplier<String>)`."
    )
    public static class JulToSlf4jSupplierFinest {
        @BeforeTemplate
        void before(Logger logger, Supplier<String> supplier) {
            logger.finest(supplier);
        }

        @AfterTemplate
        void after(org.slf4j.Logger logger, Supplier<String> supplier) {
            logger.atTrace().log(supplier);
        }
    }

    @RecipeDescriptor(
            name = "Replace JUL `Logger.finer(Supplier<String>)` with SLF4J's `Logger.atTrace().log(Supplier<String>)`",
            description = "Replace calls to `java.util.logging.Logger.finer(Supplier<String>)` with `org.slf4j.Logger.atTrace().log(Supplier<String>)`."
    )
    public static class JulToSlf4jSupplierFiner {
        @BeforeTemplate
        void before(Logger logger, Supplier<String> supplier) {
            logger.finer(supplier);
        }

        @AfterTemplate
        void after(org.slf4j.Logger logger, Supplier<String> supplier) {
            logger.atTrace().log(supplier);
        }
    }

    @RecipeDescriptor(
            name = "Replace JUL `Logger.fine(Supplier<String>)` with SLF4J's `Logger.atDebug().log(Supplier<String>)`",
            description = "Replace calls to `java.util.logging.Logger.fine(Supplier<String>)` with `org.slf4j.Logger.atDebug().log(Supplier<String>)`."
    )
    public static class JulToSlf4jSupplierFine {
        @BeforeTemplate
        void before(Logger logger, Supplier<String> supplier) {
            logger.fine(supplier);
        }

        @AfterTemplate
        void after(org.slf4j.Logger logger, Supplier<String> supplier) {
            logger.atDebug().log(supplier);
        }
    }

    @RecipeDescriptor(
            name = "Replace JUL `Logger.config(Supplier<String>)` with SLF4J's `Logger.atInfo().log(Supplier<String>)`",
            description = "Replace calls to `java.util.logging.Logger.config(Supplier<String>)` with `org.slf4j.Logger.atInfo().log(Supplier<String>)`."
    )
    public static class JulToSlf4jSupplierConfig {
        @BeforeTemplate
        void before(Logger logger, Supplier<String> supplier) {
            logger.config(supplier);
        }

        @AfterTemplate
        void after(org.slf4j.Logger logger, Supplier<String> supplier) {
            logger.atInfo().log(supplier);
        }
    }

    @RecipeDescriptor(
            name = "Replace JUL `Logger.info(Supplier<String>)` with SLF4J's `Logger.atInfo().log(Supplier<String>)`",
            description = "Replace calls to `java.util.logging.Logger.info(Supplier<String>)` with `org.slf4j.Logger.atInfo().log(Supplier<String>)`."
    )
    public static class JulToSlf4jSupplierInfo {
        @BeforeTemplate
        void before(Logger logger, Supplier<String> supplier) {
            logger.info(supplier);
        }

        @AfterTemplate
        void after(org.slf4j.Logger logger, Supplier<String> supplier) {
            logger.atInfo().log(supplier);
        }
    }

    @RecipeDescriptor(
            name = "Replace JUL `Logger.warning(Supplier<String>)` with SLF4J's `Logger.atWarn().log(Supplier<String>)`",
            description = "Replace calls to `java.util.logging.Logger.warning(Supplier<String>)` with `org.slf4j.Logger.atWarn().log(Supplier<String>)`."
    )
    public static class JulToSlf4jSupplierWarning {
        @BeforeTemplate
        void before(Logger logger, Supplier<String> supplier) {
            logger.warning(supplier);
        }

        @AfterTemplate
        void after(org.slf4j.Logger logger, Supplier<String> supplier) {
            logger.atWarn().log(supplier);
        }
    }

    @RecipeDescriptor(
            name = "Replace JUL `Logger.severe(Supplier<String>)` with SLF4J's `Logger.atError().log(Supplier<String>)`",
            description = "Replace calls to `java.util.logging.Logger.severe(Supplier<String>)` with `org.slf4j.Logger.atError().log(Supplier<String>)`."
    )
    public static class JulToSlf4jSupplierSevere {
        @BeforeTemplate
        void before(Logger logger, Supplier<String> supplier) {
            logger.severe(supplier);
        }

        @AfterTemplate
        void after(org.slf4j.Logger logger, Supplier<String> supplier) {
            logger.atError().log(supplier);
        }
    }

    @RecipeDescriptor(
            name = "Replace JUL `Logger.log(Level.FINEST, Supplier<String>)` with SLF4J's `Logger.atInfo().log(Supplier<String>)`",
            description = "Replace calls to `java.util.logging.Logger.log(Level.FINEST, Supplier<String>)` with `org.slf4j.Logger.atTrace().log(Supplier<String>)`."
    )
    public static class JulToSlf4jSupplierLogFinest {
        @BeforeTemplate
        void before(Logger logger, Supplier<String> supplier) {
            logger.log(Level.FINEST, supplier);
        }

        @AfterTemplate
        void after(org.slf4j.Logger logger, Supplier<String> supplier) {
            logger.atTrace().log(supplier);
        }
    }

    @RecipeDescriptor(
            name = "Replace JUL `Logger.log(Level.FINER, Supplier<String>)` with SLF4J's `Logger.atInfo().log(Supplier<String>)`",
            description = "Replace calls to `java.util.logging.Logger.log(Level.FINER, Supplier<String>)` with `org.slf4j.Logger.atTrace().log(Supplier<String>)`."
    )
    public static class JulToSlf4jSupplierLogFiner {
        @BeforeTemplate
        void before(Logger logger, Supplier<String> supplier) {
            logger.log(Level.FINER, supplier);
        }

        @AfterTemplate
        void after(org.slf4j.Logger logger, Supplier<String> supplier) {
            logger.atTrace().log(supplier);
        }
    }

    @RecipeDescriptor(
            name = "Replace JUL `Logger.log(Level.FINE, Supplier<String>)` with SLF4J's `Logger.atInfo().log(Supplier<String>)`",
            description = "Replace calls to `java.util.logging.Logger.log(Level.FINE, Supplier<String>)` with `org.slf4j.Logger.atDebug().log(Supplier<String>)`."
    )
    public static class JulToSlf4jSupplierLogFine {
        @BeforeTemplate
        void before(Logger logger, Supplier<String> supplier) {
            logger.log(Level.FINE, supplier);
        }

        @AfterTemplate
        void after(org.slf4j.Logger logger, Supplier<String> supplier) {
            logger.atDebug().log(supplier);
        }
    }

    @RecipeDescriptor(
            name = "Replace JUL `Logger.log(Level.CONFIG, Supplier<String>)` with SLF4J's `Logger.atInfo().log(Supplier<String>)`",
            description = "Replace calls to `java.util.logging.Logger.log(Level.CONFIG, Supplier<String>)` with `org.slf4j.Logger.atInfo().log(Supplier<String>)`."
    )
    public static class JulToSlf4jSupplierLogConfig {
        @BeforeTemplate
        void before(Logger logger, Supplier<String> supplier) {
            logger.log(Level.CONFIG, supplier);
        }

        @AfterTemplate
        void after(org.slf4j.Logger logger, Supplier<String> supplier) {
            logger.atInfo().log(supplier);
        }
    }

    @RecipeDescriptor(
            name = "Replace JUL `Logger.log(Level.INFO, Supplier<String>)` with SLF4J's `Logger.atInfo().log(Supplier<String>)`",
            description = "Replace calls to `java.util.logging.Logger.log(Level.INFO, Supplier<String>)` with `org.slf4j.Logger.atInfo().log(Supplier<String>)`."
    )
    public static class JulToSlf4jSupplierLogInfo {
        @BeforeTemplate
        void before(Logger logger, Supplier<String> supplier) {
            logger.log(Level.INFO, supplier);
        }

        @AfterTemplate
        void after(org.slf4j.Logger logger, Supplier<String> supplier) {
            logger.atInfo().log(supplier);
        }
    }

    @RecipeDescriptor(
            name = "Replace JUL `Logger.log(Level.WARNING, Supplier<String>)` with SLF4J's `Logger.atInfo().log(Supplier<String>)`",
            description = "Replace calls to `java.util.logging.Logger.log(Level.WARNING, Supplier<String>)` with `org.slf4j.Logger.atWarn().log(Supplier<String>)`."
    )
    public static class JulToSlf4jSupplierLogWarning {
        @BeforeTemplate
        void before(Logger logger, Supplier<String> supplier) {
            logger.log(Level.WARNING, supplier);
        }

        @AfterTemplate
        void after(org.slf4j.Logger logger, Supplier<String> supplier) {
            logger.atWarn().log(supplier);
        }
    }

    @RecipeDescriptor(
            name = "Replace JUL `Logger.log(Level.SEVERE, Supplier<String>)` with SLF4J's `Logger.atInfo().log(Supplier<String>)`",
            description = "Replace calls to `java.util.logging.Logger.log(Level.SEVERE, Supplier<String>)` with `org.slf4j.Logger.atError().log(Supplier<String>)`."
    )
    public static class JulToSlf4jSupplierLogSevere {
        @BeforeTemplate
        void before(Logger logger, Supplier<String> supplier) {
            logger.log(Level.SEVERE, supplier);
        }

        @AfterTemplate
        void after(org.slf4j.Logger logger, Supplier<String> supplier) {
            logger.atError().log(supplier);
        }
    }

    @RecipeDescriptor(
            name = "Replace JUL `Logger.log(Level.ALL, Supplier<String>)` with SLF4J's `Logger.atInfo().log(Supplier<String>)`",
            description = "Replace calls to `java.util.logging.Logger.log(Level.ALL, Supplier<String>)` with `org.slf4j.Logger.atTrace().log(Supplier<String>)`."
    )
    public static class JulToSlf4jSupplierLogAll {
        @BeforeTemplate
        void before(Logger logger, Supplier<String> supplier) {
            logger.log(Level.ALL, supplier);
        }

        @AfterTemplate
        void after(org.slf4j.Logger logger, Supplier<String> supplier) {
            logger.atTrace().log(supplier);
        }
    }
}
