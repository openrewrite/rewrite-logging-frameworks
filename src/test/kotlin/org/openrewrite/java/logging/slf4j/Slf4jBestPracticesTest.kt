/*
 * Copyright 2022 the original author or authors.
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
import org.openrewrite.config.Environment
import org.openrewrite.java.JavaParser
import org.openrewrite.java.JavaRecipeTest

class Slf4jBestPracticesTest : JavaRecipeTest {
    override val parser: JavaParser
        get() = JavaParser.fromJavaVersion().classpath("slf4j-api").build()

    override val recipe: Recipe = Environment.builder()
            .scanRuntimeClasspath("org.openrewrite.java.logging.slf4j")
            .build()
            .activateRecipes("org.openrewrite.java.logging.slf4j.Slf4jBestPractices")

    @Test
    fun applyBestPractices() = assertChanged(
        before = """
            import org.slf4j.Logger;
            import org.slf4j.LoggerFactory;
            class Test {
                Logger logger = LoggerFactory.getLogger(String.class);
                void test() {
                    Object obj1 = new Object();
                    Object obj2 = new Object();
                    logger.info("Hello " + obj1 + ", " + obj2);
                    Exception e = new Exception();
                    logger.warn(String.valueOf(e));
                    Exception e2 = new Exception("message2");
                    logger.error(e2.getMessage());
                    Exception e3 = new Exception("message3");
                    logger.error(e3.getLocalizedMessage());
                }
            }
        """,
        after = """
            import org.slf4j.Logger;
            import org.slf4j.LoggerFactory;
            class Test {
                Logger logger = LoggerFactory.getLogger(Test.class);
                void test() {
                    Object obj1 = new Object();
                    Object obj2 = new Object();
                    logger.info("Hello {}, {}", obj1, obj2);
                    Exception e = new Exception();
                    logger.warn("Exception", e);
                    Exception e2 = new Exception("message2");
                    logger.error("", e2);
                    Exception e3 = new Exception("message3");
                    logger.error("", e3);
                }
            }
        """
    )
}
