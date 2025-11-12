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
package org.openrewrite.java.logging;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class ConvertLoggingExceptionCastToToStringTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion()
          .classpathFromResources(new InMemoryExecutionContext(), "slf4j-api-2.1.+", "log4j-api-2.+", "log4j-core-2.+"));
    }

    @DocumentExample
    @Test
    void convertThrowableCastToToString() {
        rewriteRun(
          spec -> spec.recipe(new ConvertLoggingExceptionCastToToString("org.slf4j.Logger debug(..)")),
          //language=java
          java(
            """
              import org.slf4j.Logger;

              class Test {
                  static void asInteger(Logger logger, String numberString) {
                      try {
                          Integer i = Integer.valueOf(numberString);
                      } catch (NumberFormatException ex) {
                          logger.debug("some big error: {}", (Object) ex);
                      }
                  }
              }
              """,
            """
              import org.slf4j.Logger;

              class Test {
                  static void asInteger(Logger logger, String numberString) {
                      try {
                          Integer i = Integer.valueOf(numberString);
                      } catch (NumberFormatException ex) {
                          logger.debug("some big error: {}", ex.toString());
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void convertMultipleThrowableCasts() {
        rewriteRun(
          spec -> spec.recipe(new ConvertLoggingExceptionCastToToString("org.slf4j.Logger info(..)")),
          //language=java
          java(
            """
              import org.slf4j.Logger;

              class Test {
                  static void method(Logger logger, Exception e1, RuntimeException e2) {
                      logger.info("Errors: {} and {}", (Object) e1, (Object) e2);
                  }
              }
              """,
            """
              import org.slf4j.Logger;

              class Test {
                  static void method(Logger logger, Exception e1, RuntimeException e2) {
                      logger.info("Errors: {} and {}", e1.toString(), e2.toString());
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeNonThrowableCasts() {
        rewriteRun(
          spec -> spec.recipe(new ConvertLoggingExceptionCastToToString("org.slf4j.Logger info(..)")),
          //language=java
          java(
            """
              import org.slf4j.Logger;

              class Test {
                  static void method(Logger logger, String str) {
                      logger.info("Value: {}", (Object) str);
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeNonObjectCasts() {
        rewriteRun(
          spec -> spec.recipe(new ConvertLoggingExceptionCastToToString("org.slf4j.Logger info(..)")),
          //language=java
          java(
            """
              import org.slf4j.Logger;

              class Test {
                  static void method(Logger logger, Exception ex) {
                      logger.info("Error: {}", (Throwable) ex);
                  }
              }
              """
          )
        );
    }

    @Test
    void workWithMarkers() {
        rewriteRun(
          spec -> spec.recipe(new ConvertLoggingExceptionCastToToString("org.slf4j.Logger info(..)")),
          //language=java
          java(
            """
              import org.slf4j.Logger;
              import org.slf4j.Marker;

              class Test {
                  static void method(Logger logger, Marker marker, Exception ex) {
                      logger.info(marker, "Error occurred: {}", (Object) ex);
                  }
              }
              """,
            """
              import org.slf4j.Logger;
              import org.slf4j.Marker;

              class Test {
                  static void method(Logger logger, Marker marker, Exception ex) {
                      logger.info(marker, "Error occurred: {}", ex.toString());
                  }
              }
              """
          )
        );
    }

    @Test
    void workWithLog4j() {
        rewriteRun(
          spec -> spec.recipe(new ConvertLoggingExceptionCastToToString("org.apache.logging.log4j.Logger error(..)")),
          //language=java
          java(
            """
              import org.apache.logging.log4j.Logger;

              class Test {
                  static void method(Logger logger, Exception ex) {
                      logger.error("Failed: {}", (Object) ex);
                  }
              }
              """,
            """
              import org.apache.logging.log4j.Logger;

              class Test {
                  static void method(Logger logger, Exception ex) {
                      logger.error("Failed: {}", ex.toString());
                  }
              }
              """
          )
        );
    }
}
