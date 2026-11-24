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

import static org.openrewrite.java.Assertions.java;

class Log4j2BestPracticesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.logging.log4j.Log4j2BestPractices")
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "log4j-api"));
    }

    @DocumentExample
    @Test
    void applyBestPractices() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.logging.log4j.LogManager;
              import org.apache.logging.log4j.Logger;

              class Test {
                  Logger logger = LogManager.getLogger(Test.class);

                  void test(String name, Order order) {
                      logger.debug("Hello " + name);
                      logger.debug("Order " + order.getId() + " costs " + order.computeTotal());
                      logger.info("Order {} costs {}", order.getId(), order.computeTotal());
                      try {
                          order.computeTotal();
                      } catch (RuntimeException e) {
                          logger.error("Failed " + e);
                      }
                  }

                  interface Order {
                      String getId();

                      long computeTotal();
                  }
              }
              """,
            """
              import org.apache.logging.log4j.LogManager;
              import org.apache.logging.log4j.Logger;

              class Test {
                  private Logger logger = LogManager.getLogger(Test.class);

                  void test(String name, Order order) {
                      logger.debug("Hello {}", name);
                      logger.debug("Order {} costs {}", () -> order.getId(), () -> order.computeTotal());
                      logger.info("Order {} costs {}", order.getId(), order.computeTotal());
                      try {
                          order.computeTotal();
                      } catch (RuntimeException e) {
                          logger.error("Failed ", e);
                      }
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
}
