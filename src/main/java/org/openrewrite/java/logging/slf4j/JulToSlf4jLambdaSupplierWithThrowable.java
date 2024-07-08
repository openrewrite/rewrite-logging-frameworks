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
public class JulToSlf4jLambdaSupplierWithThrowable {
    @RecipeDescriptor(
            name = "Replace JUL `logger.log(Level.FINEST, e, Supplier<String>)` with SLF4J's `Logger.atTrace().log(Supplier<String>)`",
            description = "Replace calls to `java.util.logging.Logger.log(Level.FINEST, e, Supplier<String>)` with `org.slf4j.Logger.atTrace().log(Supplier<String>)`."
    )
    public static class JulToSlf4jSupplierFinest {
        @BeforeTemplate
        void before(Logger logger, Supplier<String> supplier, Throwable e) {
            logger.log(Level.FINEST, e, supplier);
        }

        @AfterTemplate
        void after(org.slf4j.Logger logger, Supplier<String> supplier, Throwable e) {
            logger.atTrace().setCause(e).log(supplier);
        }
    }

    @RecipeDescriptor(
            name = "Replace JUL `logger.log(Level.FINER, e, Supplier<String>)` with SLF4J's `Logger.atTrace().log(Supplier<String>)`",
            description = "Replace calls to `java.util.logging.Logger.log(Level.FINER, e, Supplier<String>)` with `org.slf4j.Logger.atTrace().log(Supplier<String>)`."
    )
    public static class JulToSlf4jSupplierFiner {
        @BeforeTemplate
        void before(Logger logger, Supplier<String> supplier, Throwable e) {
            logger.log(Level.FINER, e, supplier);
        }

        @AfterTemplate
        void after(org.slf4j.Logger logger, Supplier<String> supplier, Throwable e) {
            logger.atTrace().setCause(e).log(supplier);
        }
    }

    @RecipeDescriptor(
            name = "Replace JUL `logger.log(Level.FINE, e, Supplier<String>)` with SLF4J's `Logger.atDebug().log(Supplier<String>)`",
            description = "Replace calls to `java.util.logging.Logger.log(Level.FINE, e, Supplier<String>)` with `org.slf4j.Logger.atDebug().log(Supplier<String>)`."
    )
    public static class JulToSlf4jSupplierFine {
        @BeforeTemplate
        void before(Logger logger, Supplier<String> supplier, Throwable e) {
            logger.log(Level.FINE, e, supplier);
        }

        @AfterTemplate
        void after(org.slf4j.Logger logger, Supplier<String> supplier, Throwable e) {
            logger.atDebug().setCause(e).log(supplier);
        }
    }

    @RecipeDescriptor(
            name = "Replace JUL `logger.log(Level.CONFIG, e, Supplier<String>)` with SLF4J's `Logger.atInfo().log(Supplier<String>)`",
            description = "Replace calls to `java.util.logging.Logger.log(Level.CONFIG, e, Supplier<String>)` with `org.slf4j.Logger.atInfo().log(Supplier<String>)`."
    )
    public static class JulToSlf4jSupplierConfig {
        @BeforeTemplate
        void before(Logger logger, Supplier<String> supplier, Throwable e) {
            logger.log(Level.CONFIG, e, supplier);
        }

        @AfterTemplate
        void after(org.slf4j.Logger logger, Supplier<String> supplier, Throwable e) {
            logger.atInfo().setCause(e).log(supplier);
        }
    }

    @RecipeDescriptor(
            name = "Replace JUL `logger.log(Level.INFO, e, Supplier<String>)` with SLF4J's `Logger.atInfo().log(Supplier<String>)`",
            description = "Replace calls to `java.util.logging.Logger.log(Level.INFO, e, Supplier<String>)` with `org.slf4j.Logger.atInfo().log(Supplier<String>)`."
    )
    public static class JulToSlf4jSupplierInfo {
        @BeforeTemplate
        void before(Logger logger, Supplier<String> supplier, Throwable e) {
            logger.log(Level.INFO, e, supplier);
        }

        @AfterTemplate
        void after(org.slf4j.Logger logger, Supplier<String> supplier, Throwable e) {
            logger.atInfo().setCause(e).log(supplier);
        }
    }

    @RecipeDescriptor(
            name = "Replace JUL `logger.log(Level.WARNING, e, Supplier<String>)` with SLF4J's `Logger.atWarn().log(Supplier<String>)`",
            description = "Replace calls to `java.util.logging.Logger.log(Level.WARNING, e, Supplier<String>)` with `org.slf4j.Logger.atWarn().log(Supplier<String>)`."
    )
    public static class JulToSlf4jSupplierWarning {
        @BeforeTemplate
        void before(Logger logger, Supplier<String> supplier, Throwable e) {
            logger.log(Level.WARNING, e, supplier);
        }

        @AfterTemplate
        void after(org.slf4j.Logger logger, Supplier<String> supplier, Throwable e) {
            logger.atWarn().setCause(e).log(supplier);
        }
    }

    @RecipeDescriptor(
            name = "Replace JUL `logger.log(Level.SEVERE, e, Supplier<String>)` with SLF4J's `Logger.atError().log(Supplier<String>)`",
            description = "Replace calls to `java.util.logging.Logger.log(Level.SEVERE, e, Supplier<String>)` with `org.slf4j.Logger.atError().log(Supplier<String>)`."
    )
    public static class JulToSlf4jSupplierSevere {
        @BeforeTemplate
        void before(Logger logger, Supplier<String> supplier, Throwable e) {
            logger.log(Level.SEVERE, e, supplier);
        }

        @AfterTemplate
        void after(org.slf4j.Logger logger, Supplier<String> supplier, Throwable e) {
            logger.atError().setCause(e).log(supplier);
        }
    }

    @RecipeDescriptor(
            name = "Replace JUL `logger.log(Level.ALL, e, Supplier<String>)` with SLF4J's `Logger.atTrace().log(Supplier<String>)`",
            description = "Replace calls to `java.util.logging.Logger.log(Level.ALL, e, Supplier<String>)` with `org.slf4j.Logger.atTrace().log(Supplier<String>)`."
    )
    public static class JulToSlf4jSupplierAll {
        @BeforeTemplate
        void before(Logger logger, Supplier<String> supplier, Throwable e) {
            logger.log(Level.ALL, e, supplier);
        }

        @AfterTemplate
        void after(org.slf4j.Logger logger, Supplier<String> supplier, Throwable e) {
            logger.atTrace().setCause(e).log(supplier);
        }
    }

}
