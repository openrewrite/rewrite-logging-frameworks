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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MatchIsLogLevelEnabledWithLogStatementsTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MatchIsLogLevelEnabledWithLogStatements());
    }

    @DocumentExample
    @Test
    void debugEnabledToInfo() {
        rewriteRun(
          java(
            """
              class Test {
                  void test(org.slf4j.Logger logger) {
                      if (logger.isDebugEnabled()) {
                          logger.info("message");
                      }
                  }
              }
              """,
            """
              class Test {
                  void test(org.slf4j.Logger logger) {
                      if (logger.isInfoEnabled()) {
                          logger.info("message");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void pickHighestEnabled() {
        rewriteRun(
          java(
            """
              class Test {
                  void test(org.slf4j.Logger logger) {
                      if (logger.isDebugEnabled()) {
                          logger.debug("message");
                          logger.info("message");
                          logger.warn("message");
                      }
                  }
              }
              """,
            """
              class Test {
                  void test(org.slf4j.Logger logger) {
                      if (logger.isWarnEnabled()) {
                          logger.debug("message");
                          logger.info("message");
                          logger.warn("message");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void pickLowerIfPossible() {
        rewriteRun(
          java(
            """
              class Test {
                  void test(org.slf4j.Logger logger) {
                      if (logger.isWarnEnabled()) {
                          logger.debug("message");
                      }
                  }
              }
              """,
            """
              class Test {
                  void test(org.slf4j.Logger logger) {
                      if (logger.isDebugEnabled()) {
                          logger.debug("message");
                      }
                  }
              }
              """
          )
        );
    }

    @Nested
    class NoChange {
        @Test
        void alreadyMatching() {
            rewriteRun(
              java(
                """
                  class Test {
                      void composed(org.slf4j.Logger logger) {
                          if (logger.isDebugEnabled()) {
                              logger.debug("message");
                          }
                      }
                  }
                  """
              )
            );
        }

        @Test
        void noChangeForNestedTryCatch() {
            rewriteRun(
              java(
                """
                  class Test {
                      void composed(org.slf4j.Logger logger) {
                          if (logger.isDebugEnabled()) {
                              try {
                                  logger.debug("message");
                              } catch (Exception e) {
                                  // Fault handling for debug logging should not raise conditional logging
                                  logger.warn("warning", e);
                              }
                          }
                      }
                  }
                  """
              )
            );
        }

        @Test
        void noMatchingLoggingStatements() {
            rewriteRun(
              java(
                """
                  class Test {
                      void composed(org.slf4j.Logger logger) {
                          if (logger.isDebugEnabled()) {
                              // Some indirection to a logging call
                          }
                      }
                  }
                  """
              )
            );
        }

        @Test
        void additionalDetailsInElse() {
            rewriteRun(
              java(
                """
                  class Test {
                      void composed(org.slf4j.Logger logger, Throwable t) {
                          if (logger.isDebugEnabled()) {
                              logger.warn("message", t);
                          } else {
                              logger.warn("message");
                          }
                      }
                  }
                  """
              )
            );
        }

        @Test
        void composedConditional() {
            rewriteRun(
              java(
                """
                  class Test {
                      void composed(org.slf4j.Logger logger, boolean condition) {
                          if (logger.isDebugEnabled() && condition) {
                              logger.info("message");
                          }
                      }
                  }
                  """
              )
            );
        }

        @Test
        void negatedConditional() {
            rewriteRun(
              java(
                """
                  class Test {
                      void negated(org.slf4j.Logger logger) {
                          if (!logger.isDebugEnabled()) {
                              logger.info("message");
                          }
                      }
                  }
                  """
              )
            );
        }
    }
}
