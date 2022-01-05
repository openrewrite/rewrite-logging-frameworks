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
package org.openrewrite.java.logging.log4j

import org.junit.jupiter.api.Test
import org.openrewrite.Recipe
import org.openrewrite.config.Environment
import org.openrewrite.java.JavaParser
import org.openrewrite.java.JavaRecipeTest

class Log4j1ToLog4j2Test : JavaRecipeTest {
    override val parser: JavaParser = JavaParser.fromJavaVersion()
        .logCompilationWarningsAndErrors(true)
        .classpath("log4j")
        .build()

    override val recipe: Recipe = Environment.builder()
        .scanRuntimeClasspath("org.openrewrite.java.logging")
        .build()
        .activateRecipes("org.openrewrite.java.logging.log4j.Log4j1ToLog4j2")

    @Test
    fun loggerToLogManager() = assertChanged(
        before = """
            import org.apache.log4j.Logger;

            class Test {
                Logger logger = Logger.getLogger(Test.class);
            }
        """,
        after = """
            import org.apache.logging.log4j.LogManager;
            import org.apache.logging.log4j.Logger;

            class Test {
                Logger logger = LogManager.getLogger(Test.class);
            }
        """
    )

    @Test
    fun getRootLoggerToLogManager() = assertChanged(
        before = """
            import org.apache.log4j.Logger;
            import org.apache.log4j.LogManager;

            class Test {
                Logger logger0 = Logger.getRootLogger();
                Logger logger1 = LogManager.getRootLogger();
            }
        """,
        after = """
            import org.apache.logging.log4j.Logger;
            import org.apache.logging.log4j.LogManager;

            class Test {
                Logger logger0 = LogManager.getRootLogger();
                Logger logger1 = LogManager.getRootLogger();
            }
        """
    )

    @Test
    fun loggerGetEffectiveLevel() = assertChanged(
        before = """
            import org.apache.log4j.Logger;

            class Test {
                static void method(Logger logger) {
                    logger.getEffectiveLevel();
                }
            }
        """,
        after = """
            import org.apache.logging.log4j.Logger;

            class Test {
                static void method(Logger logger) {
                    logger.getLevel();
                }
            }
        """
    )

}
