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
package org.openrewrite.java.logging.log4j;

import com.google.errorprone.refaster.annotation.AfterTemplate;
import com.google.errorprone.refaster.annotation.BeforeTemplate;
import org.apache.logging.log4j.core.config.Configurator;
import org.openrewrite.java.template.RecipeDescriptor;

@RecipeDescriptor(
        name = "Convert Log4j `Logger.setLevel` to Log4j2 `Configurator.setLevel`",
        description = "Converts `org.apache.log4j.Logger.setLevel` to `org.apache.logging.log4j.core.config.Configurator.setLevel`.")
public class LoggerSetLevelToConfigurator {

    @BeforeTemplate
    void before(org.apache.log4j.Logger logger, org.apache.log4j.Level level) {
        logger.setLevel(level);
    }

    @AfterTemplate
    void after(org.apache.logging.log4j.Logger logger, org.apache.logging.log4j.Level level) {
        Configurator.setLevel(logger, level);
    }
}
