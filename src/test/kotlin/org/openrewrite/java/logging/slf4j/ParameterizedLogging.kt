/*
 * Copyright 2021 the original author or authors.
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
package org.openrewrite.java.logging.slf4j

import org.junit.jupiter.api.Test
import org.openrewrite.Recipe
import org.openrewrite.java.JavaParser
import org.openrewrite.java.JavaRecipeTest

class ParameterizedLoggingTest : JavaRecipeTest {
    override val parser: JavaParser = JavaParser.fromJavaVersion()
        .classpath("slf4j")
        .build()

    override val recipe: Recipe
        get() = ParameterizedLogging()

    @Test
    fun noChangeRequired() = assertUnchanged(
        before = """
            import org.slf4j.Logger;
            import org.slf4j.LoggerFactory;

            class A {
                Logger logger = LoggerFactory.getLogger(A.class);

                void asInteger(String numberString) {
                    try {
                        Integer i = Integer.valueOf(numberString);
                    } catch (NumberFormatException ex) {
                        logger.warn("Invalid parameter: {}", ex.getMessage(), ex);
                    }
                }
            }
        """
    )
    @Test
    fun loggingStatements() = assertChanged(
        before = """
            import org.slf4j.Logger;
            import org.slf4j.LoggerFactory;

            class A {
                Logger logger = LoggerFactory.getLogger(A.class);

                void asInteger(String numberString) {
                    String name = "Jon";
                    logger.error("uh oh");
                    try {
                        Integer i = Integer.valueOf(numberString);
                    } catch (NumberFormatException ex) {
                        logger.warn("some big error: " + ex.getMessage(), ex);
                    }
                    logger.info("Hello " + name + ", nice to meet you " + name);
                }
            }
        """,
        after = """
            import org.slf4j.Logger;
            import org.slf4j.LoggerFactory;

            class A {
                Logger logger = LoggerFactory.getLogger(A.class);

                void asInteger(String numberString) {
                    String name = "Jon";
                    logger.error("uh oh");
                    try {
                        Integer i = Integer.valueOf(numberString);
                    } catch (NumberFormatException ex) {
                        logger.warn("some big error: {}", ex.getMessage(), ex);
                    }
                    logger.info("Hello {}, nice to meet you {}", name, name);
                }
            }
        """
    )

}