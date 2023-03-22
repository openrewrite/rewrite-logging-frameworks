/*
 * Copyright 2022 the original author or authors.
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
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class CompleteExceptionLoggingTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new CompleteExceptionLogging())
          .parser(JavaParser.fromJavaVersion().classpath("slf4j-api"));
    }

    @Test
    void replaceGetMessageWithException() {
        rewriteRun(
          java(
            """
              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;

              class A {
                  private final static Logger LOG = LoggerFactory.getLogger(A.class);

                  void produceException() {
                      int i = 10 / 0;
                  }
                  void method() {
                      try {
                          produceException();
                      } catch (Exception e) {
                          // #1, String contains no format specifiers, `e.getMessage()` should be `e`.
                          LOG.error("An error occurred", e.getMessage());
                          
                          // #2, String contains format specifiers, `e.getMessage()` should be `e`.
                          LOG.error("An error occurred {} times", 1, e.getMessage());
                      }
                  }
              }
              """,
            """
              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;

              class A {
                  private final static Logger LOG = LoggerFactory.getLogger(A.class);

                  void produceException() {
                      int i = 10 / 0;
                  }
                  void method() {
                      try {
                          produceException();
                      } catch (Exception e) {
                          // #1, String contains no format specifiers, `e.getMessage()` should be `e`.
                          LOG.error("An error occurred", e);
                          
                          // #2, String contains format specifiers, `e.getMessage()` should be `e`.
                          LOG.error("An error occurred {} times", 1, e);
                      }
                  }
              }
                """
          )
        );
    }

    @Test
    void addNewParameter() {
        rewriteRun(
          java(
            """
              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;

              class A {
                  private final static Logger LOG = LoggerFactory.getLogger(A.class);

                  void produceException() {
                      int i = 10 / 0;
                  }
                  void method() {
                      try {
                          produceException();
                      } catch (Exception e) {
                          // #1, String contains format specifiers, add `e` as follows.
                          LOG.error("Error message : {}", e.getMessage());
                          
                          // #2, Multiple placeholders, add `e` as follows.
                          LOG.error("Error message : {} {} {}", 1, 2, e.getMessage());
                      }
                  }
              }
              """,
            """
              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;

              class A {
                  private final static Logger LOG = LoggerFactory.getLogger(A.class);

                  void produceException() {
                      int i = 10 / 0;
                  }
                  void method() {
                      try {
                          produceException();
                      } catch (Exception e) {
                          // #1, String contains format specifiers, add `e` as follows.
                          LOG.error("Error message : {}", e.getMessage(), e);
                          
                          // #2, Multiple placeholders, add `e` as follows.
                          LOG.error("Error message : {} {} {}", 1, 2, e.getMessage(), e);
                      }
                  }
              }
                """
          )
        );
    }

    @Test
    void noChangeIfGetMessageIsNotValidParameter() {
        rewriteRun(
          java(
            """
              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;

              class A {
                  private final static Logger LOG = LoggerFactory.getLogger(A.class);

                  void produceException() {
                      int i = 10 / 0;
                  }
                  void method() {
                      try {
                          produceException();
                      } catch (Exception e) {
                          // #1, GetMessage is not the last parameter
                          LOG.error("error message {}, occurred {} times times ", e.getMessage(), 1);
                          
                          // #2, getMessage() is part of a string, no change
                          LOG.error("Error message : " + e.getMessage());

                          // #3, getMessage() is not a parameter of LOG methods, no change
                          LOG.error(format(e.getMessage()));
                          LOG.error("Error message : ", format(e.getMessage()));
                      }
                  }

                  String format(String input) {
                      return input + "!";
                  }
              }
              """
          )
        );
    }

    @Test
    void allLogMethods() {
        rewriteRun(
          java(
            """
              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;

              class A {
                  private final static Logger LOG = LoggerFactory.getLogger(A.class);

                  void produceException() {
                      int i = 10 / 0;
                  }
                  void method() {
                      try {
                          produceException();
                      } catch (Exception e) {
                          LOG.debug("An error occurred", e.getMessage());
                          LOG.error("An error occurred", e.getMessage());
                          LOG.info("An error occurred", e.getMessage());
                          LOG.trace("An error occurred", e.getMessage());
                          LOG.warn("An error occurred", e.getMessage());
                      }
                  }
              }
              """,
            """
              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;

              class A {
                  private final static Logger LOG = LoggerFactory.getLogger(A.class);

                  void produceException() {
                      int i = 10 / 0;
                  }
                  void method() {
                      try {
                          produceException();
                      } catch (Exception e) {
                          LOG.debug("An error occurred", e);
                          LOG.error("An error occurred", e);
                          LOG.info("An error occurred", e);
                          LOG.trace("An error occurred", e);
                          LOG.warn("An error occurred", e);
                      }
                  }
              }
              """
          )
        );
    }
}
