/*
 * Copyright 2025 the original author or authors.
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

class StringFormatToParameterizedLoggingTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new StringFormatToParameterizedLogging())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "slf4j-api-2"));
    }

    @DocumentExample
    @Test
    void replacePatterns() {
        //language=java
        rewriteRun(
          java(
            """
              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;
              import org.slf4j.Marker;
              import org.slf4j.MarkerFactory;

              class Test {
                  private static final Logger LOGGER = LoggerFactory.getLogger(Test.class);
                  private static final Marker MARKER = MarkerFactory.getMarker("TEST");

                  void method(String username, int count, double value, String message) {
                      Exception exception = new Exception();

                      LOGGER.trace(String.format("Trace %s", username));
                      LOGGER.debug(String.format("Debug %s", username));
                      LOGGER.info(String.format("User %s logged in", username));
                      LOGGER.warn(String.format("Warning %s", username));
                      LOGGER.error(String.format("Error %s", username));

                      LOGGER.info(String.format("Count: %d", count));
                      LOGGER.info(String.format("Value: %f", value));
                      LOGGER.info(String.format("User %s has %d items", username, count));
                      LOGGER.info(String.format("User %s has %d items worth $%f", username, count, value));

                      LOGGER.info(String.format("String: %s", username));
                      LOGGER.info(String.format("Decimal: %d", count));
                      LOGGER.info(String.format("Hex: %x", count));
                      LOGGER.info(String.format("Octal: %o", count));
                      LOGGER.info(String.format("Float: %f", value));
                      LOGGER.info(String.format("Boolean: %b", true));
                      LOGGER.info(String.format("Char: %c", 'x'));

                      LOGGER.info(MARKER, String.format("Message %s", message));
                      LOGGER.error(String.format("Failed: %s", message), exception);
                      LOGGER.error(MARKER, String.format("Failed: %s", message), exception);

                      LOGGER.info(String.format("User %s"
                              + " logged in", username));
                      LOGGER.info(String.format("isHTML"
                              + " '%s', body: %s", username, message));
                      LOGGER.info(String.format("part1"
                              + " part2"
                              + " %s", username));
                  }
              }
              """,
            """
              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;
              import org.slf4j.Marker;
              import org.slf4j.MarkerFactory;

              class Test {
                  private static final Logger LOGGER = LoggerFactory.getLogger(Test.class);
                  private static final Marker MARKER = MarkerFactory.getMarker("TEST");

                  void method(String username, int count, double value, String message) {
                      Exception exception = new Exception();

                      LOGGER.trace("Trace {}", username);
                      LOGGER.debug("Debug {}", username);
                      LOGGER.info("User {} logged in", username);
                      LOGGER.warn("Warning {}", username);
                      LOGGER.error("Error {}", username);

                      LOGGER.info("Count: {}", count);
                      LOGGER.info("Value: {}", value);
                      LOGGER.info("User {} has {} items", username, count);
                      LOGGER.info("User {} has {} items worth ${}", username, count, value);

                      LOGGER.info("String: {}", username);
                      LOGGER.info("Decimal: {}", count);
                      LOGGER.info("Hex: {}", count);
                      LOGGER.info("Octal: {}", count);
                      LOGGER.info("Float: {}", value);
                      LOGGER.info("Boolean: {}", true);
                      LOGGER.info("Char: {}", 'x');

                      LOGGER.info(MARKER, "Message {}", message);
                      LOGGER.error("Failed: {}", message, exception);
                      LOGGER.error(MARKER, "Failed: {}", message, exception);

                      LOGGER.info("User {} logged in", username);
                      LOGGER.info("isHTML '{}', body: {}", username, message);
                      LOGGER.info("part1 part2 {}", username);
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceStaticImport() {
        //language=java
        rewriteRun(
          java(
            """
              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;
              import static java.lang.String.format;

              class Test {
                  private static final Logger LOGGER = LoggerFactory.getLogger(Test.class);

                  void method(String username) {
                      LOGGER.info(format("User %s logged in", username));
                  }
              }
              """,
            """
              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;

              class Test {
                  private static final Logger LOGGER = LoggerFactory.getLogger(Test.class);

                  void method(String username) {
                      LOGGER.info("User {} logged in", username);
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotReplaceInvalidPatterns() {
        //language=java
        rewriteRun(
          java(
            """
              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;

              class Test {
                  private static final Logger LOGGER = LoggerFactory.getLogger(Test.class);

                  void method(double value, int number, String first, String second) {
                      LOGGER.info(String.format("Value: %.2f", value));
                      LOGGER.info(String.format("Width: %5d", number));
                      LOGGER.info(String.format("Order: %2$s %1$s", first, second));
                      LOGGER.info(String.format("Complete: 100%%"));
                      LOGGER.info(String.format("Line1%nLine2"));
                  }
              }
              """
          )
        );
    }
}
