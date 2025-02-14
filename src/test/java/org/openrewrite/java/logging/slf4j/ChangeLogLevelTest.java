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

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class ChangeLogLevelTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ChangeLogLevel(ChangeLogLevel.Level.INFO, ChangeLogLevel.Level.DEBUG, "LaunchDarkly"))
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "slf4j-api-2.1.+"));
    }

    @DocumentExample
    @Test
    void basic() {
        rewriteRun(
          //language=java
          java(
            """
            import org.slf4j.Logger;
            import org.slf4j.LoggerFactory;

            class Test {
                private static final Logger log = LoggerFactory.getLogger(Test.class);

                void test() {
                    log.info("LaunchDarkly Hello");
                }
            }
            """,
            """
            import org.slf4j.Logger;
            import org.slf4j.LoggerFactory;

            class Test {
                private static final Logger log = LoggerFactory.getLogger(Test.class);

                void test() {
                    log.debug("LaunchDarkly Hello");
                }
            }
            """)
        );
    }

    @Test
    void concatenatedString() {
        rewriteRun(
          //language=java
          java(
            """
            import org.slf4j.Logger;
            import org.slf4j.LoggerFactory;

            class Test {
                private static final Logger log = LoggerFactory.getLogger(Test.class);

                void test() {
                    log.info("LaunchDarkly " + 1 + "Hello");
                }
            }
            """,
            """
            import org.slf4j.Logger;
            import org.slf4j.LoggerFactory;

            class Test {
                private static final Logger log = LoggerFactory.getLogger(Test.class);

                void test() {
                    log.debug("LaunchDarkly " + 1 + "Hello");
                }
            }
            """)
        );
    }


}
