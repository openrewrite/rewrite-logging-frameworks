/*
 * Copyright 2026 the original author or authors.
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
package org.openrewrite.java.logging.log4j;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.List;

import static org.openrewrite.java.Assertions.java;

class ParameterizedLoggingToLambdaSuppliersTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ParameterizedLoggingToLambdaSuppliers(null, null))
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "log4j-api"));
    }

    @DocumentExample
    @Test
    void wrapParametersInSuppliers() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.logging.log4j.Logger;

              class Test {
                  void test(Logger logger, Order order) {
                      logger.debug("Order {} costs {}", order.getId(), order.computeTotal());
                  }

                  interface Order {
                      String getId();
                      long computeTotal();
                  }
              }
              """,
            """
              import org.apache.logging.log4j.Logger;

              class Test {
                  void test(Logger logger, Order order) {
                      logger.debug("Order {} costs {}", () -> order.getId(), () -> order.computeTotal());
                  }

                  interface Order {
                      String getId();
                      long computeTotal();
                  }
              }
              """
          )
        );
    }

    @Test
    void everyLevel() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.logging.log4j.Logger;

              class Test {
                  void test(Logger logger) {
                      logger.trace("{}", compute());
                      logger.debug("{}", compute());
                      logger.info("{}", compute());
                      logger.warn("{}", compute());
                      logger.error("{}", compute());
                      logger.fatal("{}", compute());
                  }

                  String compute() {
                      return "";
                  }
              }
              """,
            """
              import org.apache.logging.log4j.Logger;

              class Test {
                  void test(Logger logger) {
                      logger.trace("{}", () -> compute());
                      logger.debug("{}", () -> compute());
                      logger.info("{}", () -> compute());
                      logger.warn("{}", () -> compute());
                      logger.error("{}", () -> compute());
                      logger.fatal("{}", () -> compute());
                  }

                  String compute() {
                      return "";
                  }
              }
              """
          )
        );
    }

    @Test
    void onlySelectedLevels() {
        rewriteRun(
          spec -> spec.recipe(new ParameterizedLoggingToLambdaSuppliers(List.of("trace", "DEBUG"), null)),
          //language=java
          java(
            """
              import org.apache.logging.log4j.Level;
              import org.apache.logging.log4j.Logger;

              class Test {
                  void test(Logger logger) {
                      logger.trace("{}", compute());
                      logger.debug("{}", compute());
                      logger.info("{}", compute());
                      logger.log(Level.DEBUG, "{}", compute());
                      logger.log(Level.ERROR, "{}", compute());
                  }

                  String compute() {
                      return "";
                  }
              }
              """,
            """
              import org.apache.logging.log4j.Level;
              import org.apache.logging.log4j.Logger;

              class Test {
                  void test(Logger logger) {
                      logger.trace("{}", () -> compute());
                      logger.debug("{}", () -> compute());
                      logger.info("{}", compute());
                      logger.log(Level.DEBUG, "{}", () -> compute());
                      logger.log(Level.ERROR, "{}", compute());
                  }

                  String compute() {
                      return "";
                  }
              }
              """
          )
        );
    }

    @Test
    void markerAndLevel() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.logging.log4j.Level;
              import org.apache.logging.log4j.Logger;
              import org.apache.logging.log4j.Marker;

              class Test {
                  void test(Logger logger, Marker marker) {
                      logger.info(marker, "{}", compute());
                      logger.log(Level.WARN, "{}", compute());
                      logger.log(Level.WARN, marker, "{}", compute());
                  }

                  String compute() {
                      return "";
                  }
              }
              """,
            """
              import org.apache.logging.log4j.Level;
              import org.apache.logging.log4j.Logger;
              import org.apache.logging.log4j.Marker;

              class Test {
                  void test(Logger logger, Marker marker) {
                      logger.info(marker, "{}", () -> compute());
                      logger.log(Level.WARN, "{}", () -> compute());
                      logger.log(Level.WARN, marker, "{}", () -> compute());
                  }

                  String compute() {
                      return "";
                  }
              }
              """
          )
        );
    }

    @Test
    void manyParameters() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.logging.log4j.Logger;

              class Test {
                  void test(Logger logger, String a, int b) {
                      logger.debug("{} {} {} {} {} {} {} {} {} {} {}", a, b, a, b, a, b, a, b, a, b, String.valueOf(b));
                  }
              }
              """,
            """
              import org.apache.logging.log4j.Logger;

              class Test {
                  void test(Logger logger, String a, int b) {
                      logger.debug("{} {} {} {} {} {} {} {} {} {} {}", () -> a, () -> b, () -> a, () -> b, () -> a, () -> b, () -> a, () -> b, () -> a, () -> b, () -> String.valueOf(b));
                  }
              }
              """
          )
        );
    }

    @Test
    void keepSimpleArgumentsByDefault() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.logging.log4j.Logger;

              class Test {
                  static final String CONSTANT = "c";
                  String name;

                  void test(Logger logger, String id) {
                      logger.debug("{} {} {} {} {}", id, 42, CONSTANT, this.name, getName());
                  }

                  String getName() {
                      return name;
                  }
              }
              """
          )
        );
    }

    @Test
    void convertSimpleArgumentsWhenAsked() {
        rewriteRun(
          spec -> spec.recipe(new ParameterizedLoggingToLambdaSuppliers(null, true)),
          //language=java
          java(
            """
              import org.apache.logging.log4j.Logger;

              class Test {
                  void test(Logger logger, String id) {
                      logger.debug("Id {}", id);
                  }
              }
              """,
            """
              import org.apache.logging.log4j.Logger;

              class Test {
                  void test(Logger logger, String id) {
                      logger.debug("Id {}", () -> id);
                  }
              }
              """
          )
        );
    }

    @Test
    void keepTrailingThrowable() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.logging.log4j.Logger;

              class Test {
                  void test(Logger logger, Exception e) {
                      logger.error("Failed {}", compute(), e);
                  }

                  String compute() {
                      return "";
                  }
              }
              """
          )
        );
    }

    @Test
    void keepNonEffectivelyFinalLocals() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.logging.log4j.Logger;

              class Test {
                  void test(Logger logger, String s) {
                      for (int i = 0; i < 3; i++) {
                          logger.debug("{}", String.valueOf(i));
                      }
                      s = s.trim();
                      logger.debug("{}", s.toUpperCase());
                  }
              }
              """
          )
        );
    }

    @Test
    void keepSideEffectsAndCheckedExceptions() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.logging.log4j.Logger;

              class Test {
                  int counter;

                  void test(Logger logger) throws Exception {
                      logger.debug("{}", String.valueOf(counter++));
                      logger.debug("{}", load());
                  }

                  String load() throws Exception {
                      return "";
                  }
              }
              """
          )
        );
    }

    @Test
    void keepMessagesWithoutParametersAndExplicitArrays() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.logging.log4j.Logger;

              class Test {
                  void test(Logger logger, Object[] params) {
                      logger.debug("no params");
                      logger.debug("{} {}", params);
                      logger.debug(compute());
                      logger.debug("{}", () -> compute());
                  }

                  String compute() {
                      return "";
                  }
              }
              """
          )
        );
    }
}
