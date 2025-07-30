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
package org.openrewrite.java.logging.jboss;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class FormattedArgumentsToVMethodTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FormattedArgumentsToVMethodRecipes());
    }

    @DocumentExample
    @Test
    void convertInfo() {
        rewriteRun(
          //language=java
          java(
            """
              import org.jboss.logging.Logger;

              class Test {
                  void test(Logger logger, String msg, Throwable t, Object[] formatArgs, Object o) {
                      logger.info(msg, formatArgs);
                      logger.info((Object)msg, formatArgs, t);
                      logger.info(o, formatArgs, t);
                  }
              }
              """,
            """
              import org.jboss.logging.Logger;

              class Test {
                  void test(Logger logger, String msg, Throwable t, Object[] formatArgs, Object o) {
                      logger.infov(msg, formatArgs);
                      logger.infov(msg, formatArgs, t);
                      logger.info(o, formatArgs, t);
                  }
              }
              """
          )
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"trace", "debug", "info", "warn", "error", "fatal"})
    void deprecatedParametrizedCallsToVCalls(String level) {
        rewriteRun(
          //language=java
          java(
            """
              import org.jboss.logging.Logger;

              class Test {
                  void test(Logger logger, String msg, Throwable t, Object[] formatArgs, Object o) {
                      logger.%1$s(msg, formatArgs);
                      logger.%1$s((Object)msg, formatArgs, t);
                      logger.%1$s(o, formatArgs, t);
                  }
              }
              """.formatted(level),
            """
              import org.jboss.logging.Logger;

              class Test {
                  void test(Logger logger, String msg, Throwable t, Object[] formatArgs, Object o) {
                      logger.%1$sv(msg, formatArgs);
                      logger.%1$sv(msg, formatArgs, t);
                      logger.%1$s(o, formatArgs, t);
                  }
              }
              """.formatted(level)
          )
        );
    }
}
