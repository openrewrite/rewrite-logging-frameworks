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

import java.util.logging.Level;
import java.util.logging.Logger;

@RecipeDescriptor(
        name = "Replace JUL `Level.ALL` logging with SLF4J's trace level",
        description = "Replace `java.util.logging.Logger#log(Level.ALL, String)` with `org.slf4j.Logger#trace(String)`."
)
public class JulLevelAllToTrace {
    @BeforeTemplate
    void before(Logger logger, String message) {
        logger.log(Level.ALL, message);
    }

    @AfterTemplate
    void after(org.slf4j.Logger logger, String message) {
        logger.trace(message);
    }
}
