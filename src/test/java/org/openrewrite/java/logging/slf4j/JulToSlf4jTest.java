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
        spec.recipeFromResources("org.openrewrite.java.logging.slf4j.JulToSlf4j");
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
                      logger.finer("finer");
                      logger.fine("fine");
                      logger.config("config");
                      logger.info("info");
                      logger.warning("warning");
                      logger.severe("severe");

                      logger.log(Level.FINEST, "finest");
                      logger.log(Level.FINER, "finer");
                      logger.log(Level.FINE, "fine");
                      logger.log(Level.CONFIG, "config");
                      logger.log(Level.INFO, "info");
                      logger.log(Level.WARNING, "warning");
                      logger.log(Level.SEVERE, "severe");

                      logger.log(Level.ALL, "all");
                  }
              }
              """,
            """
              import org.slf4j.Logger;

              class Test {
                  void method(Logger logger) {
                      logger.trace("finest");
                      logger.trace("finer");
                      logger.debug("fine");
                      logger.info("config");
                      logger.info("info");
                      logger.warn("warning");
                      logger.error("severe");

                      logger.trace("finest");
                      logger.trace("finer");
                      logger.debug("fine");
                      logger.info("config");
                      logger.info("info");
                      logger.warn("warning");
                      logger.error("severe");

                      logger.trace("all");
                  }
              }
              """
          )
        );
    }

    @Test
    void simpleLoggerCallsWithThrowable() {
        rewriteRun(
          // language=java
          java(
            """
              import java.util.logging.Level;
              import java.util.logging.Logger;

              class Test {
                  void method(Logger logger, Throwable e) {
                      logger.log(Level.FINEST, "finest", e);
                      logger.log(Level.FINER, "finer", e);
                      logger.log(Level.FINE, "fine", e);
                      logger.log(Level.CONFIG, "config", e);
                      logger.log(Level.INFO, "info", e);
                      logger.log(Level.WARNING, "warning", e);
                      logger.log(Level.SEVERE, "severe", e);

                      logger.log(Level.ALL, "all", e);
                  }
              }
              """,
            """
              import org.slf4j.Logger;

              class Test {
                  void method(Logger logger, Throwable e) {
                      logger.trace("finest", e);
                      logger.trace("finer", e);
                      logger.debug("fine", e);
                      logger.info("config", e);
                      logger.info("info", e);
                      logger.warn("warning", e);
                      logger.error("severe", e);

                      logger.trace("all", e);
                  }
              }
              """
          )
        );
    }

    @Test
    void supplierLoggerCalls() {
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.builder().methodInvocations(false).build()),
          // language=java
          java(
            """
              import java.util.logging.Level;
              import java.util.logging.Logger;

              class Test {
                  void method(Logger logger) {
                      logger.finest(() -> "finest");
                      logger.finer(() -> "finer");
                      logger.fine(() -> "fine");
                      logger.config(() -> "config");
                      logger.info(() -> "info");
                      logger.warning(() -> "warning");
                      logger.severe(() -> "severe");

                      logger.log(Level.FINEST, () -> "finest");
                      logger.log(Level.FINER, () -> "finer");
                      logger.log(Level.FINE, () -> "fine");
                      logger.log(Level.CONFIG, () -> "config");
                      logger.log(Level.INFO, () -> "info");
                      logger.log(Level.WARNING, () -> "warning");
                      logger.log(Level.SEVERE, () -> "severe");

                      logger.log(Level.ALL, () -> "all");
                  }
              }
              """,
            """
              import org.slf4j.Logger;

              class Test {
                  void method(Logger logger) {
                      logger.atTrace().log(() -> "finest");
                      logger.atTrace().log(() -> "finer");
                      logger.atDebug().log(() -> "fine");
                      logger.atInfo().log(() -> "config");
                      logger.atInfo().log(() -> "info");
                      logger.atWarn().log(() -> "warning");
                      logger.atError().log(() -> "severe");

                      logger.atTrace().log(() -> "finest");
                      logger.atTrace().log(() -> "finer");
                      logger.atDebug().log(() -> "fine");
                      logger.atInfo().log(() -> "config");
                      logger.atInfo().log(() -> "info");
                      logger.atWarn().log(() -> "warning");
                      logger.atError().log(() -> "severe");

                      logger.atTrace().log(() -> "all");
                  }
              }
              """
          )
        );
    }

    @Test
    void supplierLoggerCallsWithThrowable() {
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.builder().methodInvocations(false).build()),
          // language=java
          java(
            """
              import java.util.logging.Level;
              import java.util.logging.Logger;

              class Test {
                  void method(Logger logger, Throwable e) {
                      logger.log(Level.FINEST, e, () -> "finest");
                      logger.log(Level.FINER, e, () -> "finer");
                      logger.log(Level.FINE, e, () -> "fine");
                      logger.log(Level.CONFIG, e, () -> "config");
                      logger.log(Level.INFO, e, () -> "info");
                      logger.log(Level.WARNING, e, () -> "warning");
                      logger.log(Level.SEVERE, e, () -> "severe");

                      logger.log(Level.ALL, e, () -> "all");
                  }
              }
              """,
            """
              import org.slf4j.Logger;

              class Test {
                  void method(Logger logger, Throwable e) {
                      logger.atTrace().setCause(e).log(() -> "finest");
                      logger.atTrace().setCause(e).log(() -> "finer");
                      logger.atDebug().setCause(e).log(() -> "fine");
                      logger.atInfo().setCause(e).log(() -> "config");
                      logger.atInfo().setCause(e).log(() -> "info");
                      logger.atWarn().setCause(e).log(() -> "warning");
                      logger.atError().setCause(e).log(() -> "severe");

                      logger.atTrace().setCause(e).log(() -> "all");
                  }
              }
              """
          )
        );
    }

    @Test
    void concatenatedSupplierLoggerCalls() {
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.builder().methodInvocations(false).build()),
          // language=java
          java(
            """
              import java.util.logging.Level;
              import java.util.logging.Logger;

              class Test {
                  void method(Logger logger) {
                      String variable = "variable";
                      logger.finest(() -> "finest" + variable + "rest");
                      logger.finer(() -> "finer" + variable + "rest");
                      logger.fine(() -> "fine" + variable + "rest");
                      logger.config(() -> "config" + variable + "rest");
                      logger.info(() -> "info" + variable + "rest");
                      logger.warning(() -> "warning" + variable + "rest");
                      logger.severe(() -> "severe" + variable + "rest");

                      logger.log(Level.INFO, () -> "info" + variable + "rest");
                  }
              }
              """,
            """
              import org.slf4j.Logger;

              class Test {
                  void method(Logger logger) {
                      String variable = "variable";
                      logger.atTrace().log(() -> "finest" + variable + "rest");
                      logger.atTrace().log(() -> "finer" + variable + "rest");
                      logger.atDebug().log(() -> "fine" + variable + "rest");
                      logger.atInfo().log(() -> "config" + variable + "rest");
                      logger.atInfo().log(() -> "info" + variable + "rest");
                      logger.atWarn().log(() -> "warning" + variable + "rest");
                      logger.atError().log(() -> "severe" + variable + "rest");

                      logger.atInfo().log(() -> "info" + variable + "rest");
                  }
              }
              """
          )
        );
    }

    @Test
    void concatenatedLoggerCalls() {
        rewriteRun(
          // language=java
          java(
            """
              import java.util.logging.Level;
              import java.util.logging.Logger;

              class Test {
                  void method(Logger logger) {
                      String variable = "variable";
                      logger.finest("finest " + variable + " rest");
                      logger.finer("finer " + variable + " rest");
                      logger.fine("fine " + variable + " rest");
                      logger.config("config " + variable + " rest");
                      logger.info("info " + variable + " rest");
                      logger.warning("warning " + variable + " rest");
                      logger.severe("severe " + variable + " rest");

                      logger.log(Level.INFO, "info " + variable + " rest");
                      logger.log(Level.ALL, "all " + variable + " rest");
                  }
              }
              """,
            """
              import org.slf4j.Logger;

              class Test {
                  void method(Logger logger) {
                      String variable = "variable";
                      logger.trace("finest {} rest", variable);
                      logger.trace("finer {} rest", variable);
                      logger.debug("fine {} rest", variable);
                      logger.info("config {} rest", variable);
                      logger.info("info {} rest", variable);
                      logger.warn("warning {} rest", variable);
                      logger.error("severe {} rest", variable);

                      logger.info("info {} rest", variable);
                      logger.trace("all {} rest", variable);
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
