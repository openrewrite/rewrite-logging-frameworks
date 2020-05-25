/*
 * Copyright 2020 the original author or authors.
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
package org.openrewrite.logging

import org.junit.jupiter.api.Test
import org.openrewrite.java.JavaParser

class Log4jToSlf4jTest : JavaParser(dependenciesFromClasspath("log4j")) {
    @Test
    fun loggingStatements() {
        val a = parse("""
            import org.apache.log4j.*;
            
            class A {
                Logger logger = Logger.getLogger(A.class);
                
                void myMethod() {
                    String name = "Jon";
                    logger.fatal("uh oh");
                    logger.info(new Object());
                    logger.info("Hello " + name + ", nice to meet you " + name);
                }
            }
        """.trimIndent())

        val fixed = a.refactor().visit(Log4jToSlf4j()).fix().fixed

        assertRefactored(fixed, """
            import org.slf4j.Logger;
            import org.slf4j.LoggerFactory;
            
            class A {
                Logger logger = LoggerFactory.getLogger(A.class);
                
                void myMethod() {
                    String name = "Jon";
                    logger.error("uh oh");
                    logger.info(new Object().toString());
                    logger.info("Hello {}, nice to meet you {}", name, name);
                }
            }
        """)
    }
}