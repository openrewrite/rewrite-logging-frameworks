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
package org.openrewrite.java.logging.slf4j;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class CompleteExceptionLoggingTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new CompleteExceptionLogging())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "slf4j-api-2"));
    }

    @DocumentExample
    @Test
    void addExceptionToBeTheLastArg() {
        //language=java
        rewriteRun(
          java(
            """
              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;

              class Test {
                  Logger logger = LoggerFactory.getLogger(Test.class);
                  void doSomething() {
                      try {
                          Integer num = Integer.valueOf("a");
                      } catch (NumberFormatException e) {
                          // TEST CASE #1:
                          logger.error(e.getMessage());

                          // TEST CASE #2:
                          logger.error("BEFORE MESSAGE " + e.getMessage());

                          // TEST CASE #3:
                          logger.error("BEFORE MESSAGE " + e.getMessage() + " AFTER MESSAGE");

                          // TEST CASE #4: No Changes, since stack trace already being logged
                          logger.error("BEFORE MESSAGE " + e.getMessage() + " AFTER MESSAGE", e);
                      }
                  }
              }
              """,
            """

              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;

              class Test {
                  Logger logger = LoggerFactory.getLogger(Test.class);
                  void doSomething() {
                      try {
                          Integer num = Integer.valueOf("a");
                      } catch (NumberFormatException e) {
                          // TEST CASE #1:
                          logger.error("", e);

                          // TEST CASE #2:
                          logger.error("BEFORE MESSAGE " + e.getMessage(), e);

                          // TEST CASE #3:
                          logger.error("BEFORE MESSAGE " + e.getMessage() + " AFTER MESSAGE", e);

                          // TEST CASE #4: No Changes, since stack trace already being logged
                          logger.error("BEFORE MESSAGE " + e.getMessage() + " AFTER MESSAGE", e);
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceGetMessageWithException() {
        //language=java
        rewriteRun(
          java(
            """
              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;

              class A {
                  private final static Logger LOG = LoggerFactory.getLogger(A.class);

                  void produceException() {
                      throw new RuntimeException("");
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
                      throw new RuntimeException("");
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
        //language=java
        rewriteRun(
          java(
            """
              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;

              class A {
                  private final static Logger LOG = LoggerFactory.getLogger(A.class);

                  void produceException() {
                      throw new RuntimeException("");
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
                      throw new RuntimeException("");
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
    void variousGetMessageAsParameter() {
        //language=java
        rewriteRun(
          java(
            """
              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;

              class A {
                  private final static Logger LOG = LoggerFactory.getLogger(A.class);

                  void produceException() {
                      throw new RuntimeException("");
                  }
                  void method() {
                      try {
                          produceException();
                      } catch (Exception e) {
                          // #1, GetMessage is not the last parameter
                          LOG.error("error message {}, occurred {} times ", e.getMessage(), 1);

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
              """,
            """
              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;

              class A {
                  private final static Logger LOG = LoggerFactory.getLogger(A.class);

                  void produceException() {
                      throw new RuntimeException("");
                  }
                  void method() {
                      try {
                          produceException();
                      } catch (Exception e) {
                          // #1, GetMessage is not the last parameter
                          LOG.error("error message {}, occurred {} times ", e.getMessage(), 1, e);

                          // #2, getMessage() is part of a string, no change
                          LOG.error("Error message : " + e.getMessage(), e);

                          // #3, getMessage() is not a parameter of LOG methods, no change
                          LOG.error(format(e.getMessage()), e);
                          LOG.error("Error message : ", format(e.getMessage()), e);
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
        //language=java
        rewriteRun(
          java(
            """
              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;

              class A {
                  private final static Logger LOG = LoggerFactory.getLogger(A.class);

                  void produceException() {
                      throw new RuntimeException("");
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

                          LOG.debug("An error occurred", e.getLocalizedMessage());
                          LOG.error("An error occurred", e.getLocalizedMessage());
                          LOG.info("An error occurred", e.getLocalizedMessage());
                          LOG.trace("An error occurred", e.getLocalizedMessage());
                          LOG.warn("An error occurred", e.getLocalizedMessage());
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
                      throw new RuntimeException("");
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

    @Test
    void convertWithMessage() {
        //language=java
        rewriteRun(
          java(
            """
              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;

              class Test {
                  Logger logger = LoggerFactory.getLogger(Test.class);
                  void doSomething() {
                      try {
                          Integer num = Integer.valueOf("a");
                      } catch (NumberFormatException e) {
                          logger.error(e.getMessage());
                          logger.warn(e.getLocalizedMessage());
                      }
                  }
              }
              """,
            """
              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;

              class Test {
                  Logger logger = LoggerFactory.getLogger(Test.class);
                  void doSomething() {
                      try {
                          Integer num = Integer.valueOf("a");
                      } catch (NumberFormatException e) {
                          logger.error("", e);
                          logger.warn("", e);
                      }
                  }
              }
              """
          )
        );
    }
}
