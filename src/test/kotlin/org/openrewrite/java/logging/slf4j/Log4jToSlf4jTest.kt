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

@Suppress("RedundantSlf4jDefinition")
class Log4jToSlf4jTest : JavaRecipeTest {
    override val parser: JavaParser = JavaParser.fromJavaVersion()
        .logCompilationWarningsAndErrors(true)
        .classpath("log4j")
        .build()

    override val recipe: Recipe
        get() = Log4jToSlf4j()

    @Test
    fun useLoggerFactory() = assertChanged(
        before = """
            import org.apache.log4j.Logger;
            import org.apache.log4j.LogManager;

            class Test {
                Logger logger0 = Logger.getLogger(Test.class);
                Logger logger1 = LogManager.getLogger(Test.class);
            }
        """,
        after = """
            import org.slf4j.Logger;
            import org.slf4j.LoggerFactory;

            class Test {
                Logger logger0 = LoggerFactory.getLogger(Test.class);
                Logger logger1 = LoggerFactory.getLogger(Test.class);
            }
        """
    )

    @Test
    fun logLevelFatalToError() = assertChanged(
        before = """
            import org.apache.log4j.Logger;

            class Test {
                static void method(Logger logger) {
                    logger.fatal("uh oh");
                }
            }
        """,
        after = """
            import org.slf4j.Logger;

            class Test {
                static void method(Logger logger) {
                    logger.error("uh oh");
                }
            }
        """
    )

}
