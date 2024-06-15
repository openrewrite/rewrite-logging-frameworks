/*
 * Copyright 2024 the original author or authors.
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
package org.openrewrite.java.logging.slf4j;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

// Levels mapping:
// * ALL     -> TRACE
// * FINEST  -> TRACE
// * FINER   -> TRACE
// * FINE    -> DEBUG
// * CONFIG  -> INFO
// * INFO    -> INFO
// * WARNING -> WARN
// * SEVERE  -> ERROR

class JulToSlf4jTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.typeValidationOptions(TypeValidation.builder().build())
          .recipeFromResources("org.openrewrite.java.logging.slf4j.JulToSlf4j");
    }

    @DocumentExample
    @Test
    void simpleLoggerCalls() {
        rewriteRun(
          // language=java
          java(
            """
            import java.util.logging.Level;
            import java.util.logging.Logger;

            class Test {
                void method(Logger logger) {
                    logger.finest("finest");
                    logger.finest(() -> "finest");
                    logger.finer("finer");
                    logger.finer(() -> "finer");
                    logger.fine("fine");
                    logger.fine(() -> "fine");
                    logger.config("config");
                    logger.config(() -> "config");
                    logger.info("info");
                    logger.info(() -> "info");
                    logger.warning("warning");
                    logger.warning(() -> "warning");
                    logger.severe("severe");
                    logger.severe(() -> "severe");

                    logger.log(Level.INFO, "info");
                    logger.log(Level.INFO, () -> "info");
                }
            }
            """,
            """
              import org.slf4j.Logger;

              class Test {
                  void method(Logger logger) {
                      logger.trace("finest");
                      logger.trace(() -> "finest");
                      logger.trace("finer");
                      logger.trace(() -> "finer");
                      logger.debug("fine");
                      logger.debug(() -> "fine");
                      logger.info("config");
                      logger.info(() -> "config");
                      logger.info("info");
                      logger.info(() -> "info");
                      logger.warn("warning");
                      logger.warn(() -> "warning");
                      logger.error("severe");
                      logger.error(() -> "severe");

                      logger.info("info");
                      logger.info(() -> "info");
                  }
              }
              """
          )
        );
    }

    @Test
    void logLevelIsLoggable() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.logging.Level;import java.util.logging.Logger;

              class Test {
                  static void method(Logger logger) {
                      if (logger.isLoggable(Level.FINEST)) {
                          logger.finest("FINEST log entry");
                      }
                      if (logger.isLoggable(Level.FINER)) {
                          logger.finer("FINER log entry");
                      }
                      if (logger.isLoggable(Level.FINE)) {
                          logger.fine("FINE log entry");
                      }
                      if (logger.isLoggable(Level.CONFIG)) {
                          logger.config("CONFIG log entry");
                      }
                      if (logger.isLoggable(Level.INFO)) {
                          logger.info("INFO log entry");
                      }
                      if (logger.isLoggable(Level.WARNING)) {
                          logger.warning("WARNING log entry");
                      }
                      if (logger.isLoggable(Level.SEVERE)) {
                          logger.severe("SEVERE log entry");
                      }
                  }
              }
              """,
            """
              import org.slf4j.Logger;

              class Test {
                  static void method(Logger logger) {
                      if (logger.isTraceEnabled()) {
                          logger.trace("FINEST log entry");
                      }
                      if (logger.isTraceEnabled()) {
                          logger.trace("FINER log entry");
                      }
                      if (logger.isDebugEnabled()) {
                          logger.debug("FINE log entry");
                      }
                      if (logger.isInfoEnabled()) {
                          logger.info("CONFIG log entry");
                      }
                      if (logger.isInfoEnabled()) {
                          logger.info("INFO log entry");
                      }
                      if (logger.isWarnEnabled()) {
                          logger.warn("WARNING log entry");
                      }
                      if (logger.isErrorEnabled()) {
                          logger.error("SEVERE log entry");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void loggerToLoggerFactory() {
        rewriteRun(
          // language=java
          java(
            """
              import java.util.logging.Logger;

              class Test {
                  Logger logger1 = Logger.getLogger("Test");
                  Logger logger2 = Logger.getLogger(Test.class.getName());
                  Logger logger3 = Logger.getLogger(Test.class.getCanonicalName());
              }
              """,
            """
              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;

              class Test {
                  Logger logger1 = LoggerFactory.getLogger("Test");
                  Logger logger2 = LoggerFactory.getLogger(Test.class);
                  Logger logger3 = LoggerFactory.getLogger(Test.class);
              }
              """
          )
        );
    }

    @Test
    @Disabled
    void parametrizedLoggerCallsIsLoggable() {
        rewriteRun(
          // language=java
          java(
            """
              import java.util.logging.Level;
              import java.util.logging.Logger;

              class Test {
                  void method(Logger logger, String param1, String param2) {
                      logger.log(Level.FINEST, "FINEST Log entry, param1: {0}", param1);
                      logger.log(Level.FINEST, "FINEST Log entry, param1: {0}, param2: {1}, etc", new Object[]{param1, param2});
                      logger.log(Level.FINEST, "FINEST Log entry, param1: {0}, param2: {1}, etc", new String[]{param1, param2});

                      logger.log(Level.FINER, "FINER Log entry, param1: {0}", param1);
                      logger.log(Level.FINER, "FINER Log entry, param1: {0}, param2: {1}, etc", new Object[]{param1, param2});
                      logger.log(Level.FINER, "FINER Log entry, param1: {0}, param2: {1}, etc", new String[]{param1, param2});

                      logger.log(Level.FINE, "FINE Log entry, param1: {0}", param1);
                      logger.log(Level.FINE, "FINE Log entry, param1: {0}, param2: {1}, etc", new Object[]{param1, param2});
                      logger.log(Level.FINE, "FINE Log entry, param1: {0}, param2: {1}, etc", new String[]{param1, param2});

                      logger.log(Level.CONFIG, "CONFIG Log entry, param1: {0}", param1);
                      logger.log(Level.CONFIG, "CONFIG Log entry, param1: {0}, param2: {1}, etc", new Object[]{param1, param2});
                      logger.log(Level.CONFIG, "CONFIG Log entry, param1: {0}, param2: {1}, etc", new String[]{param1, param2});

                      logger.log(Level.INFO, "INFO Log entry, param1: {0}", param1);
                      logger.log(Level.INFO, "INFO Log entry, param1: {0}, param2: {1}, etc", new Object[]{param1, param2});
                      logger.log(Level.INFO, "INFO Log entry, param1: {0}, param2: {1}, etc", new String[]{param1, param2});

                      logger.log(Level.WARNING, "WARNING Log entry, param1: {0}", param1);
                      logger.log(Level.WARNING, "WARNING Log entry, param1: {0}, param2: {1}, etc", new Object[]{param1, param2});
                      logger.log(Level.WARNING, "WARNING Log entry, param1: {0}, param2: {1}, etc", new String[]{param1, param2});

                      logger.log(Level.SEVERE, "SEVERE Log entry, param1: {0}", param1);
                      logger.log(Level.SEVERE, "SEVERE Log entry, param1: {0}, param2: {1}, etc", new Object[]{param1, param2});
                      logger.log(Level.SEVERE, "SEVERE Log entry, param1: {0}, param2: {1}, etc", new String[]{param1, param2});
                  }
              }
              """,
            """
              import org.slf4j.Logger;

              class Test {
                  void method(Logger logger, String param1, String param2) {
                      logger.trace("FINEST Log entry, param1: {}", param1);
                      logger.trace("FINEST Log entry, param1: {}, param2: {}, etc", param1, param2);
                      logger.trace("FINEST Log entry, param1: {}, param2: {}, etc", param1, param2);

                      logger.trace("FINER Log entry, param1: {}", param1);
                      logger.trace("FINER Log entry, param1: {}, param2: {}, etc", param1, param2);
                      logger.trace("FINER Log entry, param1: {}, param2: {}, etc", param1, param2);

                      logger.debug("FINE Log entry, param1: {}", param1);
                      logger.debug("FINE Log entry, param1: {}, param2: {}, etc", param1, param2);
                      logger.debug("FINE Log entry, param1: {}, param2: {}, etc", param1, param2);

                      logger.info("CONFIG Log entry, param1: {}", param1);
                      logger.info("CONFIG Log entry, param1: {}, param2: {}, etc", param1, param2);
                      logger.info("CONFIG Log entry, param1: {}, param2: {}, etc", param1, param2);

                      logger.info("INFO Log entry, param1: {}", param1);
                      logger.info("INFO Log entry, param1: {}, param2: {}, etc", param1, param2);
                      logger.info("INFO Log entry, param1: {}, param2: {}, etc", param1, param2);

                      logger.warn("WARNING Log entry, param1: {}", param1);
                      logger.warn("WARNING Log entry, param1: {}, param2: {}, etc", param1, param2);
                      logger.warn("WARNING Log entry, param1: {}, param2: {}, etc", param1, param2);

                      logger.error("SEVERE Log entry, param1: {}", param1);
                      logger.error("SEVERE Log entry, param1: {}, param2: {}, etc", param1, param2);
                      logger.error("SEVERE Log entry, param1: {}, param2: {}, etc", param1, param2);
                  }
              }
              """
          )
        );
    }

    @Test
    void staticFinalLoggerIsStaticFinal() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.logging.Logger;

              class Test {
                  private static final Logger logger1 = Logger.getLogger("Test");
                  private static final Logger logger2 = Logger.getLogger(Test.class.getName());
                  private static final Logger logger3 = Logger.getLogger(Test.class.getCanonicalName());
              }
              """,
            """
              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;

              class Test {
                  private static final Logger logger1 = LoggerFactory.getLogger("Test");
                  private static final Logger logger2 = LoggerFactory.getLogger(Test.class);
                  private static final Logger logger3 = LoggerFactory.getLogger(Test.class);
              }
              """
          )
        );
    }
}
