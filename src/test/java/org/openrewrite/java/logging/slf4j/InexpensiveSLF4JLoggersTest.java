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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class InexpensiveSLF4JLoggersTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new InexpensiveSLF4JLoggers())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "slf4j-api-2.1.+"));
    }

    @DocumentExample
    @Test
    void documentationExample() {
        //language=java
        rewriteRun(
          java(
            """
              import org.slf4j.Logger;

              class A {
                  void method(Logger LOG) {
                      LOG.debug("SomeString {}, {}", "some param", expensiveOp());
                  }

                  String expensiveOp() {
                      return "expensive";
                  }
              }
              """,
            """
              import org.slf4j.Logger;

              class A {
                  void method(Logger LOG) {
                      if (LOG.isDebugEnabled()) {
                          LOG.debug("SomeString {}, {}", "some param", expensiveOp());
                      }
                  }

                  String expensiveOp() {
                      return "expensive";
                  }
              }
              """
          )
        );
    }

    @ParameterizedTest
    @CsvSource({"info, Info", "debug, Debug", "trace, Trace", "error, Error", "warn, Warn"})
    void allLogMethods(String method, String check) {
        //language=java
        rewriteRun(
          java(
            String.format("""
              import org.slf4j.Logger;

              class A {
                  void method(Logger LOG) {
                      LOG.%s("SomeString {}, {}", "some param", expensiveOp());
                  }

                  String expensiveOp() {
                      return "expensive";
                  }
              }
              """, method),
            String.format("""
              import org.slf4j.Logger;

              class A {
                  void method(Logger LOG) {
                      if (LOG.is%sEnabled()) {
                          LOG.%s("SomeString {}, {}", "some param", expensiveOp());
                      }
                  }

                  String expensiveOp() {
                      return "expensive";
                  }
              }
              """, check, method)
          )
        );
    }

    @Test
    void handleBlocksWithExpensiveOperations() {
        rewriteRun(
          //language=java
          java(
            """
              import org.slf4j.Logger;

              class A {
                  void method(Logger LOG) {
                      System.out.println("an unrelated statement");
                      LOG.info(expensiveOp());
                      LOG.info("SomeString {}", "some param");
                      LOG.info("SomeString {}", expensiveOp());
                      System.out.println("another unrelated statement");
                  }

                  String expensiveOp() {
                      return "expensive";
                  }
              }
              """,
            """
              import org.slf4j.Logger;

              class A {
                  void method(Logger LOG) {
                      System.out.println("an unrelated statement");
                      if (LOG.isInfoEnabled()) {
                          LOG.info(expensiveOp());
                          LOG.info("SomeString {}", "some param");
                          LOG.info("SomeString {}", expensiveOp());
                      }
                      System.out.println("another unrelated statement");
                  }

                  String expensiveOp() {
                      return "expensive";
                  }
              }
              """
          )
        );
    }

    @Test
    void leaveCheapLogLines() {
        rewriteRun(
          //language=java
          java(
            """
              import org.slf4j.Logger;

              class A {
                  void method(Logger LOG) {
                      String s = "message";
                      LOG.info("SomeString {}", "some param");
                      LOG.info("SomeString {}", s);
                  }
              }
              """
          )
        );
    }

    @Test
    void leaveLogLinesInIfWithOtherChecks() {
        rewriteRun(
          //language=java
          java(
            """
              import org.slf4j.Logger;

              class A {
                  void method(Logger LOG) {
                      String s = "message";
                      LOG.info("SomeString {}", "some param");
                      if(LOG.isInfoEnabled()) {
                          LOG.info("SomeString {}", expensiveOp());
                      }
                      if(1 == 1 && LOG.isInfoEnabled()) {
                          LOG.info("SomeString {}", expensiveOp());
                      }
                      LOG.info("SomeString {}", s);
                  }

                  String expensiveOp() {
                      return "expensive";
                  }
              }
              """
          )
        );
    }

    @Test
    void partiallyPrecheckedStatements() {
        rewriteRun(
          //language=java
          java(
            """
              import org.slf4j.Logger;

              class A {
                  void method(Logger LOG) {
                      String s = "message";
                      LOG.info("SomeString {}", "some param");
                      if (LOG.isInfoEnabled()) {
                          LOG.info("SomeString {}", expensiveOp());
                      }
                      LOG.info(expensiveOp());
                  }

                  String expensiveOp() {
                      return "expensive";
                  }
              }
              """,
            """
              import org.slf4j.Logger;

              class A {
                  void method(Logger LOG) {
                      String s = "message";
                      if (LOG.isInfoEnabled()) {
                          LOG.info("SomeString {}", "some param");
                          LOG.info("SomeString {}", expensiveOp());
                          LOG.info(expensiveOp());
                      }
                  }

                  String expensiveOp() {
                      return "expensive";
                  }
              }
              """
          )
        );
    }

    @Test
    void differentLogLevelStatements() {
        rewriteRun(
          //language=java
          java(
            """
              import org.slf4j.Logger;

              class A {
                  void method(Logger LOG) {
                      String s = "message";
                      LOG.info("SomeString {}", "some param");
                      LOG.info("SomeString {}", expensiveOp());
                      LOG.debug("SomeString {}", "some param");
                      LOG.debug(expensiveOp());
                      LOG.info(expensiveOp());
                  }

                  String expensiveOp() {
                      return "expensive";
                  }
              }
              """,
            """
              import org.slf4j.Logger;

              class A {
                  void method(Logger LOG) {
                      String s = "message";
                      if (LOG.isInfoEnabled()) {
                          LOG.info("SomeString {}", "some param");
                          LOG.info("SomeString {}", expensiveOp());
                      }
                      if (LOG.isDebugEnabled()) {
                          LOG.debug("SomeString {}", "some param");
                          LOG.debug(expensiveOp());
                      }
                      if (LOG.isInfoEnabled()) {
                          LOG.info(expensiveOp());
                      }
                  }

                  String expensiveOp() {
                      return "expensive";
                  }
              }
              """
          )
        );
    }

    @Test
    void logStatementsInOuterIf() {
        rewriteRun(
          //language=java
          java(
            """
              import org.slf4j.Logger;

              class A {
                  void method(Logger LOG) {
                      if (expensiveOp().equals("test")) {
                          String s = "message";
                          LOG.info("SomeString {}", "some param");
                          LOG.info("SomeString {}", expensiveOp());
                          LOG.debug("SomeString {}", "some param");
                          LOG.debug(expensiveOp());
                          LOG.info(expensiveOp());
                      }
                  }

                  String expensiveOp() {
                      return "expensive";
                  }
              }
              """,
            """
              import org.slf4j.Logger;

              class A {
                  void method(Logger LOG) {
                      if (expensiveOp().equals("test")) {
                          String s = "message";
                          if (LOG.isInfoEnabled()) {
                              LOG.info("SomeString {}", "some param");
                              LOG.info("SomeString {}", expensiveOp());
                          }
                          if (LOG.isDebugEnabled()) {
                              LOG.debug("SomeString {}", "some param");
                              LOG.debug(expensiveOp());
                          }
                          if (LOG.isInfoEnabled()) {
                              LOG.info(expensiveOp());
                          }
                      }
                  }

                  String expensiveOp() {
                      return "expensive";
                  }
              }
              """
          )
        );
    }

    @Test
    void lambdas() {
        rewriteRun(
          //language=java
          java(
            """
              import org.slf4j.Logger;
              import java.util.Optional;

              class A {
                  void method(Optional<String> op, Logger LOG) {
                      op.ifPresent(s -> {
                          LOG.info("SomeString {}", s);
                          LOG.info("SomeString {}", expensiveOp());
                      });
                      op.ifPresent(s -> LOG.info("SomeString {}", expensiveOp()));
                  }

                  String expensiveOp() {
                      return "expensive";
                  }
              }
              """,
            """
              import org.slf4j.Logger;
              import java.util.Optional;

              class A {
                  void method(Optional<String> op, Logger LOG) {
                      op.ifPresent(s -> {
                          if (LOG.isInfoEnabled()) {
                              LOG.info("SomeString {}", s);
                              LOG.info("SomeString {}", expensiveOp());
                          }
                      });
                      op.ifPresent(s -> LOG.info("SomeString {}", expensiveOp()));
                  }

                  String expensiveOp() {
                      return "expensive";
                  }
              }
              """
          )
        );
    }

    @Test
    void unrelatedIf() {
        rewriteRun(
          //language=java
          java(
            """
              import org.slf4j.Logger;

              class A {
                  void method(Logger LOG) {
                      LOG.info("Going to do something {}", expensiveOp());
                      if (expensiveOp().equals("test")) {
                          LOG.info("Doing Something {}", expensiveOp());
                          return;
                      }
                  }

                  String expensiveOp() {
                      return "expensive";
                  }
              }
              """,
            """
              import org.slf4j.Logger;

              class A {
                  void method(Logger LOG) {
                      if (LOG.isInfoEnabled()) {
                          LOG.info("Going to do something {}", expensiveOp());
                      }
                      if (expensiveOp().equals("test")) {
                          if (LOG.isInfoEnabled()) {
                              LOG.info("Doing Something {}", expensiveOp());
                          }
                          return;
                      }
                  }

                  String expensiveOp() {
                      return "expensive";
                  }
              }
              """
          )
        );
    }
}

