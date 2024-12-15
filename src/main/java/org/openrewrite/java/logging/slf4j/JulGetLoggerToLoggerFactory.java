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
package org.openrewrite.java.logging.slf4j;

import com.google.errorprone.refaster.annotation.AfterTemplate;
import com.google.errorprone.refaster.annotation.BeforeTemplate;
import org.openrewrite.java.template.RecipeDescriptor;
import org.slf4j.LoggerFactory;

import java.util.logging.Logger;

@RecipeDescriptor(
        name = "Replace JUL Logger creation with SLF4J LoggerFactory",
        description = "Replace calls to `Logger.getLogger` with `LoggerFactory.getLogger`."
)
public class JulGetLoggerToLoggerFactory {
    @RecipeDescriptor(
            name = "Replace JUL `Logger.getLogger(Some.class.getName())` with SLF4J's `LoggerFactory.getLogger(Some.class)`",
            description = "Replace calls to `java.util.logging.Logger.getLogger(Some.class.getName())` with `org.slf4j.LoggerFactory.getLogger(Some.class)`."
    )
    public static class GetLoggerClassNameToLoggerFactory {
        @BeforeTemplate
        Logger before(Class<?> clazz) {
            return Logger.getLogger(clazz.getName());
        }

        @AfterTemplate
        org.slf4j.Logger after(Class<?> clazz) {
            return LoggerFactory.getLogger(clazz);
        }
    }

    @RecipeDescriptor(
            name = "Replace JUL `Logger.getLogger(Some.class.getCanonicalName())` with SLF4J's `LoggerFactory.getLogger(Some.class)`",
            description = "Replace calls to `java.util.logging.Logger.getLogger(Some.class.getCanonicalName())` with `org.slf4j.LoggerFactory.getLogger(Some.class)`."
    )
    public static class GetLoggerClassCanonicalNameToLoggerFactory {
        @BeforeTemplate
        Logger before(Class<?> clazz) {
            return Logger.getLogger(clazz.getCanonicalName());
        }

        @AfterTemplate
        org.slf4j.Logger after(Class<?> clazz) {
            return LoggerFactory.getLogger(clazz);
        }
    }
}
