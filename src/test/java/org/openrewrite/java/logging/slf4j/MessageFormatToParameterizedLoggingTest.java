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

class MessageFormatToParameterizedLoggingTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MessageFormatToParameterizedLogging())
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
              import java.text.MessageFormat;

              class Test {
                  private static final Logger logger = LoggerFactory.getLogger(Test.class);
                  private static final Marker marker = MarkerFactory.getMarker("TEST");

                  void method(String username, int count, String message) {
                      Exception exception = new Exception();

                      logger.trace(MessageFormat.format("Trace {0}", username));
                      logger.debug(MessageFormat.format("Debug {0}", username));
                      logger.info(MessageFormat.format("User {0} logged in", username));
                      logger.warn(MessageFormat.format("Warning {0}", username));
                      logger.error(MessageFormat.format("Error {0}", username));

                      logger.info(MessageFormat.format("User {0} has {1} items", username, count));
                      logger.info(MessageFormat.format("Values: {0}, {1}, {2}", "a", "b", "c"));

                      logger.info(marker, MessageFormat.format("Message {0}", message));
                      logger.error(MessageFormat.format("Failed: {0}", message), exception);
                      logger.error(marker, MessageFormat.format("Failed: {0}", message), exception);

                      logger.info(MessageFormat.format("User {0}"
                              + " logged in", username));
                      logger.info(MessageFormat.format("isHTML"
                              + " ''{0}'', body: {1}", username, message));
                      logger.info(MessageFormat.format("part1"
                              + " part2"
                              + " {0}", username));
                  }
              }
              """,
            """
              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;
              import org.slf4j.Marker;
              import org.slf4j.MarkerFactory;

              class Test {
                  private static final Logger logger = LoggerFactory.getLogger(Test.class);
                  private static final Marker marker = MarkerFactory.getMarker("TEST");

                  void method(String username, int count, String message) {
                      Exception exception = new Exception();

                      logger.trace("Trace {}", username);
                      logger.debug("Debug {}", username);
                      logger.info("User {} logged in", username);
                      logger.warn("Warning {}", username);
                      logger.error("Error {}", username);

                      logger.info("User {} has {} items", username, count);
                      logger.info("Values: {}, {}, {}", "a", "b", "c");

                      logger.info(marker, "Message {}", message);
                      logger.error("Failed: {}", message, exception);
                      logger.error(marker, "Failed: {}", message, exception);

                      logger.info("User {} logged in", username);
                      logger.info("isHTML ''{}'', body: {}", username, message);
                      logger.info("part1 part2 {}", username);
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
              import static java.text.MessageFormat.format;

              class Test {
                  private static final Logger logger = LoggerFactory.getLogger(Test.class);

                  void method(String username) {
                      logger.info(format("User {0} logged in", username));
                  }
              }
              """,
            """
              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;

              class Test {
                  private static final Logger logger = LoggerFactory.getLogger(Test.class);

                  void method(String username) {
                      logger.info("User {} logged in", username);
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
              import java.text.MessageFormat;
              import java.util.Date;

              class Test {
                  private static final Logger logger = LoggerFactory.getLogger(Test.class);

                  void method(double value, Date date, String a, String b, String c) {
                      logger.info(MessageFormat.format("Value: {0,number,currency}", value));
                      logger.info(MessageFormat.format("Date: {0,date,short}", date));
                      logger.info(MessageFormat.format("Order: {2} {0} {1}", a, b, c));
                  }
              }
              """
          )
        );
    }
}
