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
    fun migratesLoggerToLoggerFactory() = assertChanged(
        before = """
            import org.apache.log4j.Logger;

            class Test {
                Logger logger = Logger.getLogger(Test.class);
            }
        """,
        after = """
            import org.slf4j.Logger;
            import org.slf4j.LoggerFactory;

            class Test {
                Logger logger = LoggerFactory.getLogger(Test.class);
            }
        """
    )

    @Test
    fun migratesFatalToError() = assertChanged(
        before = """
            import org.apache.log4j.Logger;

            class Test {
                Logger logger = Logger.getLogger(Test.class);

                void method() {
                    logger.fatal("uh oh");
                }
            }
        """,
        after = """
            import org.slf4j.Logger;
            import org.slf4j.LoggerFactory;

            class Test {
                Logger logger = LoggerFactory.getLogger(Test.class);

                void method() {
                    logger.error("uh oh");
                }
            }
        """
    )

    @Test
    fun migratesExceptions() = assertChanged(
        before = """
            import org.apache.log4j.Logger;

            class Test {
                Logger logger = Logger.getLogger(Test.class);

                void method(String numberString) {
                    try {
                        Integer i = Integer.valueOf(numberString);
                    } catch (Exception e) {
                        logger.error(e.getMessage(), e);
                    }
                }
            }
        """,
        after = """
            import org.slf4j.Logger;
            import org.slf4j.LoggerFactory;

            class Test {
                Logger logger = LoggerFactory.getLogger(Test.class);

                void method(String numberString) {
                    try {
                        Integer i = Integer.valueOf(numberString);
                    } catch (Exception e) {
                        logger.error("{}", e.getMessage(), e);
                    }
                }
            }
        """
    )

    @Test
    fun objectParameters() = assertChanged(
        before = """
            import org.apache.log4j.Logger;

            class Test {
                Logger logger = Logger.getLogger(Test.class);

                void method(Test test) {
                    logger.info(test);
                    logger.info(new Object());
                }
            }
        """,
        after = """
            import org.slf4j.Logger;
            import org.slf4j.LoggerFactory;

            class Test {
                Logger logger = LoggerFactory.getLogger(Test.class);

                void method(Test test) {
                    logger.info("{}", test);
                    logger.info("{}", new Object());
                }
            }
        """
    )

    @Test
    fun methodInvocationParameters() = assertChanged(
        before = """
            import org.apache.log4j.Logger;

            class Test {
                Logger logger = Logger.getLogger(Test.class);

                void method(StringBuilder sb) {
                    logger.info(new StringBuilder("append0").append("append1").append(sb));
                }
            }
        """,
        after = """
            import org.slf4j.Logger;
            import org.slf4j.LoggerFactory;

            class Test {
                Logger logger = LoggerFactory.getLogger(Test.class);

                void method(StringBuilder sb) {
                    logger.info("{}", new StringBuilder("append0").append("append1").append(sb));
                }
            }
        """
    )

    @Test
    fun usesParameterizedLogging() = assertChanged(
        before = """
            import org.apache.log4j.Logger;

            class Test {
                Logger logger = Logger.getLogger(Test.class);

                void method() {
                    String name = "Jon";
                    logger.info("Hello " + name + ", nice to meet you " + name);
                }
            }
        """,
        after = """
            import org.slf4j.Logger;
            import org.slf4j.LoggerFactory;

            class Test {
                Logger logger = LoggerFactory.getLogger(Test.class);

                void method() {
                    String name = "Jon";
                    logger.info("Hello {}, nice to meet you {}", name, name);
                }
            }
        """
    )

}
