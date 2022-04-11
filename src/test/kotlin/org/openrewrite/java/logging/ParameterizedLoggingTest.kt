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
package org.openrewrite.java.logging

import org.junit.jupiter.api.Test
import org.openrewrite.Issue
import org.openrewrite.java.JavaParser
import org.openrewrite.java.JavaRecipeTest

@Suppress(
    "RedundantSlf4jDefinition",
    "EmptyTryBlock",
    "SimplifyStreamApiCallChains",
    "PlaceholderCountMatchesArgumentCount"
)
class ParameterizedLoggingTest : JavaRecipeTest {
    override val parser: JavaParser = JavaParser.fromJavaVersion()
        .logCompilationWarningsAndErrors(true)
        .classpath("slf4j", "log4j-api", "log4j-core")
        .build()

    @Test
    fun basicParameterization() = assertChanged(
        recipe = ParameterizedLogging("org.slf4j.Logger info(..)"),
        before = """
            import org.slf4j.Logger;

            class Test {
                static void method(Logger logger, String name) {
                    logger.info("Hello " + name + ", nice to meet you " + name);
                }
            }
        """,
        after = """
            import org.slf4j.Logger;

            class Test {
                static void method(Logger logger, String name) {
                    logger.info("Hello {}, nice to meet you {}", name, name);
                }
            }
        """
    )

    @Test
    fun concatenateLiteralStrings() = assertChanged(
        recipe = ParameterizedLogging("org.slf4j.Logger info(..)"),
        before = """
            import org.slf4j.Logger;

            class Test {
                static void method(Logger logger) {
                    logger.info("left" + " " + "right");
                }
            }
        """,
        after = """
            import org.slf4j.Logger;

            class Test {
                static void method(Logger logger) {
                    logger.info("left right");
                }
            }
        """
    )

    @Test
    @Issue("https://github.com/openrewrite/rewrite-logging-frameworks/issues/36")
    @Suppress("UnnecessaryStringEscape")
    fun handleEscapedCharacters() = assertChanged(
        recipe = ParameterizedLogging("org.slf4j.Logger info(..)"),
        before = """
            import org.slf4j.Logger;

            class Test {
                static void method(Logger logger, String str) {
                    logger.info("\n" + str);
                    logger.info("\t" + str);
                    logger.info("\r" + str);
                    logger.info("\"escape\" " + str);
                    logger.info("use \"escape\" " + str);
                }
            }
        """,
        after = """
            import org.slf4j.Logger;

            class Test {
                static void method(Logger logger, String str) {
                    logger.info("\n{}", str);
                    logger.info("\t{}", str);
                    logger.info("\r{}", str);
                    logger.info("\"escape\" {}", str);
                    logger.info("use \"escape\" {}", str);
                }
            }
        """
    )

    @Test
    @Issue("https://github.com/openrewrite/rewrite-logging-frameworks/issues/30")
    fun escapeMessageStrings() = assertChanged(
        recipe = ParameterizedLogging("org.slf4j.Logger info(..)"),
        before = """
            import org.slf4j.Logger;

            class Test {
                static void method(Logger logger, String text) {
                    logger.info("See link #" + text);
                }
            }
        """,
        after = """
            import org.slf4j.Logger;

            class Test {
                static void method(Logger logger, String text) {
                    logger.info("See link #{}", text);
                }
            }
        """
    )

    @Test
    fun exceptionArgumentsAsConcatenatedString() = assertChanged(
        recipe = ParameterizedLogging("org.slf4j.Logger debug(..)"),
        before = """
            import org.slf4j.Logger;

            class Test {
                static void asInteger(Logger logger, String numberString) {
                    try {
                        Integer i = Integer.valueOf(numberString);
                    } catch (NumberFormatException ex) {
                        logger.debug("some big error: " + ex);
                    }
                }
            }
        """,
        after = """
            import org.slf4j.Logger;

            class Test {
                static void asInteger(Logger logger, String numberString) {
                    try {
                        Integer i = Integer.valueOf(numberString);
                    } catch (NumberFormatException ex) {
                        logger.debug("some big error: {}", ex);
                    }
                }
            }
        """
    )

    @Test
    @Issue("https://github.com/openrewrite/rewrite-logging-frameworks/issues/38")
    fun indexOutOfBoundsExceptionOnParseMethodArguments() = assertChanged(
        recipe = ParameterizedLogging("org.slf4j.Logger warn(..)"),
        before = """
            import org.slf4j.Logger;

            import java.util.List;

            class Test {
                static void method(Logger logger, List<String> nameSpaces) {
                    nameSpaces.stream()
                            .forEach(namespace -> {
                                try {
                                } catch (Exception ex) {
                                    logger.warn("Couldn't get the pods in namespace:" + namespace, ex);
                                }
                            });
                }
            }
        """,
        after = """
            import org.slf4j.Logger;

            import java.util.List;

            class Test {
                static void method(Logger logger, List<String> nameSpaces) {
                    nameSpaces.stream()
                            .forEach(namespace -> {
                                try {
                                } catch (Exception ex) {
                                    logger.warn("Couldn't get the pods in namespace:{}", namespace, ex);
                                }
                            });
                }
            }
        """
    )

    @Test
    fun exceptionArgumentsWithThrowable() = assertChanged(
        recipe = ParameterizedLogging("org.slf4j.Logger warn(..)"),
        before = """
            import org.slf4j.Logger;

            class Test {
                static void asInteger(Logger logger, String numberString) {
                    try {
                        Integer i = Integer.valueOf(numberString);
                    } catch (NumberFormatException ex) {
                        logger.warn("some big error: " + ex.getMessage(), ex);
                    }
                }
            }
        """,
        after = """
            import org.slf4j.Logger;

            class Test {
                static void asInteger(Logger logger, String numberString) {
                    try {
                        Integer i = Integer.valueOf(numberString);
                    } catch (NumberFormatException ex) {
                        logger.warn("some big error: {}", ex.getMessage(), ex);
                    }
                }
            }
        """
    )

    @Test
    fun alreadyParameterizedThrowableArguments() = assertUnchanged(
        recipe = ParameterizedLogging("org.slf4j.Logger warn(..)"),
        before = """
            import org.slf4j.Logger;

            class Test {
                static void asInteger(Logger logger, String numberString) {
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
    @Issue("https://github.com/openrewrite/rewrite-logging-frameworks/issues/35")
    fun alreadyParameterizedBinaryExpressionArguments() = assertUnchanged(
        recipe = ParameterizedLogging("org.slf4j.Logger debug(..)"),
        before = """
            import org.slf4j.Logger;

            class Test {
                static void method(Logger logger, Long startTime, Long endTime) {
                    logger.debug("Time taken {} for indexing taskExecution logs", endTime - startTime);
                }
            }
        """
    )

    @Test
    @Issue("https://github.com/openrewrite/rewrite-logging-frameworks/issues/26")
    fun argumentsContainingBinaryExpressions() = assertChanged(
        recipe = ParameterizedLogging("org.slf4j.Logger debug(..)"),
        before = """
            import org.slf4j.Logger;

            class Test {
                static void method(Logger logger, String name, double percent) {
                    logger.debug("Process [" + name + "] is at [" + percent * 100 + "%]");
                }
            }
        """,
        after = """
            import org.slf4j.Logger;

            class Test {
                static void method(Logger logger, String name, double percent) {
                    logger.debug("Process [{}] is at [{}%]", name, percent * 100);
                }
            }
        """
    )

    @Test
    fun throwableParameters() = assertChanged(
        recipe = ParameterizedLogging("org.apache.logging.log4j.Logger error(..)"),
        before = """
            import org.apache.logging.log4j.Logger;

            class Test {
                static void method(Logger logger, String numberString) {
                    try {
                        Integer i = Integer.valueOf(numberString);
                    } catch (Exception e) {
                        logger.error(e.getMessage(), e);
                    }
                }
            }
        """,
        after = """
            import org.apache.logging.log4j.Logger;

            class Test {
                static void method(Logger logger, String numberString) {
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
        recipe = ParameterizedLogging("org.apache.logging.log4j.Logger info(..)"),
        before = """
            import org.apache.logging.log4j.Logger;

            class Test {
                static void method(Logger logger, Test test) {
                    logger.info(test);
                    logger.info(new Object());
                }
            }
        """,
        after = """
            import org.apache.logging.log4j.Logger;

            class Test {
                static void method(Logger logger, Test test) {
                    logger.info("{}", test);
                    logger.info("{}", new Object());
                }
            }
        """
    )

    @Test
    fun methodInvocationParameters() = assertChanged(
        recipe = ParameterizedLogging("org.apache.logging.log4j.Logger info(..)"),
        before = """
            import org.apache.logging.log4j.Logger;

            class Test {
                static void method(Logger logger, StringBuilder sb) {
                    logger.info(new StringBuilder("append0").append("append1").append(sb));
                }
            }
        """,
        after = """
            import org.apache.logging.log4j.Logger;

            class Test {
                static void method(Logger logger, StringBuilder sb) {
                    logger.info("{}", new StringBuilder("append0").append("append1").append(sb));
                }
            }
        """
    )

    @Issue("https://github.com/openrewrite/rewrite-logging-frameworks/issues/59")
    @Test
    fun handlesEscapeChars() = assertChanged(
        recipe = ParameterizedLogging("org.apache.logging.log4j.Logger info(..)"),
        before = """
            import org.apache.logging.log4j.Logger;
            class T {
            
                static void method(Logger logger) {
                    logger.info("\n\\\r_" + "\\\\_\n" + "_\"\n");
                    logger.info("t" + "");
                    logger.info("" + "");
                }
            }
        """,
        after = """
            import org.apache.logging.log4j.Logger;
            class T {
            
                static void method(Logger logger) {
                    logger.info("\n\\\r_\\\\_\n_\"\n");
                    logger.info("t");
                    logger.info("");
                }
            }
        """
    )
}