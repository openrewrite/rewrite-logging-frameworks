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
package org.openrewrite.java.logging.jul;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class LoggerLevelArgumentToMethodTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new LoggerLevelArgumentToMethodRecipes());
    }

    @DocumentExample
    @Test
    void replaceLevelArguments() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.logging.Level;
              import java.util.logging.Logger;

              class Test {
                  void test(Logger logger, String message) {
                      logger.log(Level.FINEST, message);
                      logger.log(Level.FINER, message);
                      logger.log(Level.FINE, message);
                      logger.log(Level.INFO, message);
                      logger.log(Level.WARNING, message);
                      logger.log(Level.SEVERE, message);
                  }
              }
              """,
            """
              import java.util.logging.Logger;

              class Test {
                  void test(Logger logger, String message) {
                      logger.finest(message);
                      logger.finer(message);
                      logger.fine(message);
                      logger.info(message);
                      logger.warning(message);
                      logger.severe(message);
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceLevelArgumentsWithSupplier() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.function.Supplier;
              import java.util.logging.Level;
              import java.util.logging.Logger;

              class Test {
                  void test(Logger logger, Supplier<String> message) {
                      logger.log(Level.FINEST, message);
                      logger.log(Level.FINER, message);
                      logger.log(Level.FINE, message);
                      logger.log(Level.INFO, message);
                      logger.log(Level.WARNING, message);
                      logger.log(Level.SEVERE, message);
                  }
              }
              """,
            """
              import java.util.function.Supplier;
              import java.util.logging.Logger;

              class Test {
                  void test(Logger logger, Supplier<String> message) {
                      logger.finest(message);
                      logger.finer(message);
                      logger.fine(message);
                      logger.info(message);
                      logger.warning(message);
                      logger.severe(message);
                  }
              }
              """
          )
        );
    }

    @Test
    void noReplacementMethod() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.logging.Level;
              import java.util.logging.Logger;

              class Test {
                  void test(Logger logger, String message) {
                      logger.log(Level.ALL, message);
                      logger.log(Level.CONFIG, message);
                      logger.log(Level.OFF, message);
                  }
              }
              """
          )
        );
    }
}
