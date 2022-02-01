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
package org.openrewrite.java.logging

import org.junit.jupiter.api.Test
import org.openrewrite.java.JavaParser
import org.openrewrite.java.JavaRecipeTest

@Suppress("EmptyTryBlock")
class SystemErrToLoggingTest : JavaRecipeTest {

    @Test
    fun useSlf4j() = assertChanged(
        parser = JavaParser.fromJavaVersion()
            .classpath("slf4j-api")
            .build(),
        recipe = SystemErrToLogging(null, "LOGGER", null),
        before = """
            import org.slf4j.Logger;
            class Test {
                Logger logger;
                
                void test() {
                    try {
                    } catch(Throwable t) {
                        System.err.println("Oh no");
                        t.printStackTrace();
                    }
                }
            }
        """,
        after = """
            import org.slf4j.Logger;
            class Test {
                Logger logger;
                
                void test() {
                    try {
                    } catch(Throwable t) {
                        logger.error("Oh no", t);
                    }
                }
            }
        """
    )

    @Suppress("RedundantSlf4jDefinition")
    @Test
    fun addLogger() = assertChanged(
        parser = JavaParser.fromJavaVersion()
            .classpath("slf4j-api")
            .build(),
        recipe = SystemErrToLogging(true, "LOGGER", null),
        before = """
            class Test {
                void test() {
                    try {
                    } catch(Throwable t) {
                        System.err.println("Oh no");
                        t.printStackTrace();
                    }
                }
            }
        """,
        after = """
            import org.slf4j.Logger;
            import org.slf4j.LoggerFactory;
            
            class Test {
                private static final Logger LOGGER = LoggerFactory.getLogger(Test.class);
            
                void test() {
                    try {
                    } catch(Throwable t) {
                        LOGGER.error("Oh no", t);
                    }
                }
            }
        """
    )

    @Test
    fun dontChangePrintStackTrace() = assertUnchanged(
        recipe = SystemErrToLogging(true, "LOGGER", null),
        before = """
            class Test {
                void test() {
                    try {
                    } catch(Throwable t) {
                        t.printStackTrace();
                    }
                }
            }
        """
    )
}
