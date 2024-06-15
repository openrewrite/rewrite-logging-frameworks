/*
 * Copyright 2023 the original author or authors.
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
package org.openrewrite.java.logging.log4j;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class JulToLog4jTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResource("/META-INF/rewrite/log4j.yml", "org.openrewrite.java.logging.log4j.JulToLog4j")
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "log4j-api-2.23", "lombok-1.18"));
    }

    @Test
    void loggerToLogManager() {
        rewriteRun(
          // language=java
          java(
            """
              import java.util.logging.Logger;

              class Test {
                  Logger log = Logger.getLogger("Test");
              }
              """,
            """
              import org.apache.logging.log4j.LogManager;
              import org.apache.logging.log4j.Logger;

              class Test {
                  Logger log = LogManager.getLogger("Test");
              }
              """
          )
        );
    }

    @Test
    @DocumentExample
    void simpleLoggerCalls() {
        rewriteRun(
          // language=java
          java(
            """
              import java.util.logging.Level;import java.util.logging.Logger;

              class Test {
                  void method(Logger logger) {
                      logger.config("Hello");
                      logger.config(() -> "Hello");
                      logger.fine("Hello");
                      logger.fine(() -> "Hello");
                      logger.finer("Hello");
                      logger.finer(() -> "Hello");
                      logger.finest("Hello");
                      logger.finest(() -> "Hello");
                      logger.info("Hello");
                      logger.info(() -> "Hello");
                      logger.severe("Hello");
                      logger.severe(() -> "Hello");
                      logger.warning("Hello");
                      logger.warning(() -> "Hello");

                      logger.log(Level.INFO, "Hello");
                  }
              }
              """,
            """
              import org.apache.logging.log4j.Logger;

              class Test {
                  void method(Logger logger) {
                      logger.info("Hello");
                      logger.info(() -> "Hello");
                      logger.debug("Hello");
                      logger.debug(() -> "Hello");
                      logger.trace("Hello");
                      logger.trace(() -> "Hello");
                      logger.trace("Hello");
                      logger.trace(() -> "Hello");
                      logger.info("Hello");
                      logger.info(() -> "Hello");
                      logger.error("Hello");
                      logger.error(() -> "Hello");
                      logger.warn("Hello");
                      logger.warn(() -> "Hello");

                      logger.info("Hello");
                  }
              }
              """
          )
        );
    }

    @Test
    void changeLombokLogAnnotation() {
        rewriteRun(spec -> spec.typeValidationOptions(TypeValidation.builder()
            .identifiers(false)
            .methodInvocations(false)
            .build()),
          // language=java
          java(
            """
              import lombok.extern.java.Log;

              @Log
              class Test {
                  void method() {
                      log.info("uh oh");
                  }
              }
              """,
            """
              import lombok.extern.log4j.Log4j2;

              @Log4j2
              class Test {
                  void method() {
                      log.info("uh oh");
                  }
              }
              """
          )
        );
    }
}
