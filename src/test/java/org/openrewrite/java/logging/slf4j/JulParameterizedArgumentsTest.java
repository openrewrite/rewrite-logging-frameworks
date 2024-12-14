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
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class JulParameterizedArgumentsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.logging.slf4j.JulToSlf4j");
    }

    @DocumentExample
    @Test
    void parameterizedSingleArgument() {
        rewriteRun(
          // language=java
          java(
            """
              import java.util.logging.Level;
              import java.util.logging.Logger;

              class Test {
                  void method(Logger logger, String param1) {
                      logger.log(Level.INFO, "INFO Log entry, param1: {0}", param1);
                  }
              }
              """,
            """
              import org.slf4j.Logger;

              class Test {
                  void method(Logger logger, String param1) {
                      logger.info("INFO Log entry, param1: {}", param1);
                  }
              }
              """
          )
        );
    }

    @Test
    void parameterizedArgumentArray() {
        rewriteRun(
          // language=java
          java(
            """
              import java.util.logging.Level;
              import java.util.logging.Logger;

              class Test {
                  void method(Logger logger, String param1, String param2) {
                      logger.log(Level.INFO, "INFO Log entry, param1: {0}, param2: {1}, etc", new String[]{ param1, param2 });
                      logger.log(Level.INFO, "INFO Log entry, param1: {0}, param2: {1}, etc", new Object[]{ param1, param2 });
                  }
              }
              """,
            """
              import org.slf4j.Logger;

              class Test {
                  void method(Logger logger, String param1, String param2) {
                      logger.info("INFO Log entry, param1: {}, param2: {}, etc", param1, param2);
                      logger.info("INFO Log entry, param1: {}, param2: {}, etc", param1, param2);
                  }
              }
              """
          )
        );
    }

    @Test
    void retainLoggedArgumentOrder() {
        rewriteRun(
          // language=java
          java(
            """
              import java.util.logging.Level;
              import java.util.logging.Logger;

              class Test {
                  void method(Logger logger, String param1, String param2) {
                      logger.log(Level.INFO, "INFO Log entry, param2: {1}, param1: {0}, etc", new String[]{ param1, param2 });
                  }
              }
              """,
            """
              import org.slf4j.Logger;

              class Test {
                  void method(Logger logger, String param1, String param2) {
                      logger.info("INFO Log entry, param2: {}, param1: {}, etc", param2, param1);
                  }
              }
              """
          )
        );
    }

    @Test
    void repeatLoggedArgumentAsNeeded() {
        rewriteRun(
          // language=java
          java(
            """
              import java.util.logging.Level;
              import java.util.logging.Logger;

              class Test {
                  void method(Logger logger, String param1, String param2) {
                      logger.log(Level.INFO, "INFO Log entry, param1: {0}, param1: {0}, etc", param1);
                  }
              }
              """,
            """
              import org.slf4j.Logger;

              class Test {
                  void method(Logger logger, String param1, String param2) {
                      logger.info("INFO Log entry, param1: {}, param1: {}, etc", param1, param1);
                  }
              }
              """
          )
        );
    }

    @Test
    void mapLogLevelsToCorrectMethod() {
        rewriteRun(
          // language=java
          java(
            """
              import java.util.logging.Level;
              import java.util.logging.Logger;

              class Test {
                  void method(Logger logger, String param1) {
                      logger.log(Level.FINEST, "FINEST Log entry, param1: {0}", param1);
                      logger.log(Level.FINER, "FINER Log entry, param1: {0}", param1);
                      logger.log(Level.FINE, "FINE Log entry, param1: {0}", param1);
                      logger.log(Level.CONFIG, "CONFIG Log entry, param1: {0}", param1);
                      logger.log(Level.INFO, "INFO Log entry, param1: {0}", param1);
                      logger.log(Level.WARNING, "WARNING Log entry, param1: {0}", param1);
                      logger.log(Level.SEVERE, "SEVERE Log entry, param1: {0}", param1);
                  }
              }
              """,
            """
              import org.slf4j.Logger;

              class Test {
                  void method(Logger logger, String param1) {
                      logger.trace("FINEST Log entry, param1: {}", param1);
                      logger.trace("FINER Log entry, param1: {}", param1);
                      logger.debug("FINE Log entry, param1: {}", param1);
                      logger.info("CONFIG Log entry, param1: {}", param1);
                      logger.info("INFO Log entry, param1: {}", param1);
                      logger.warn("WARNING Log entry, param1: {}", param1);
                      logger.error("SEVERE Log entry, param1: {}", param1);
                  }
              }
              """
          )
        );
    }

    @Test
    void staticImportLevel() {
        rewriteRun(
          // language=java
          java(
            """
              import java.util.logging.Logger;
              import static java.util.logging.Level.INFO;

              class Test {
                  void method(Logger logger, String param1) {
                      logger.log(INFO, "INFO Log entry, param1: {0}", param1);
                  }
              }
              """,
            """
              import org.slf4j.Logger;

              class Test {
                  void method(Logger logger, String param1) {
                      logger.info("INFO Log entry, param1: {}", param1);
                  }
              }
              """
          )
        );
    }

    @Test
    void levelVariableLeadsToPartialConversion() {
        rewriteRun(
          // language=java
          java(
            """
              import java.util.logging.Logger;
              import java.util.logging.Level;

              class Test {
                  void method(Logger logger, Level level, String param1) {
                      // No way to determine the replacement logging method
                      logger.log(level, "INFO Log entry, param1: {0}", param1);
                  }
              }
              """,
            """
              import org.slf4j.Logger;

              import java.util.logging.Level;

              class Test {
                  void method(Logger logger, Level level, String param1) {
                      // No way to determine the replacement logging method
                      logger.log(level, "INFO Log entry, param1: {0}", param1);
                  }
              }
              """
          )
        );
    }
}
