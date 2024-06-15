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
import org.slf4j.LoggerFactory;

import java.util.logging.Logger;

//TODO Provide RecipeDescriptor
public class GetLoggerToLoggerFactory {
    //TODO Provide RecipeDescriptor
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

    //TODO Provide RecipeDescriptor
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
