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
package org.openrewrite.java.logging;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.logging.logback.Log4jAppenderToLogback;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings({"EmptyTryBlock", "CallToPrintStackTrace"})
class SystemErrToLoggingTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new Log4jAppenderToLogback())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "log4j-1.2", "slf4j-api-2.1", "lombok-1.18"));
    }

    @DocumentExample
    @Test
    void useSlf4j() {
        rewriteRun(
          spec -> spec.recipe(new SystemErrToLogging(null, "LOGGER", null)),
          //language=java
          java(
            """
              import org.slf4j.Logger;
              class Test {
                  int n;
                  Logger logger;

                  void test() {
                      try {
                      } catch(Throwable t) {
                          System.err.println("Oh " + n + " no");
                          t.printStackTrace();
                      }
                  }
              }
              """,
            """
              import org.slf4j.Logger;
              class Test {
                  int n;
                  Logger logger;

                  void test() {
                      try {
                      } catch(Throwable t) {
                          logger.error("Oh {} no", n, t);
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void addLogger() {
        rewriteRun(
          spec -> spec.recipe(new SystemErrToLogging(true, "LOGGER", null)),
          //language=java
          java(
            """
              class Test {
                  void test() {
                      try {
                      } catch(Throwable t) {
                          System.err.println("Oh no");
                          t.printStackTrace();
                      }
                  }
              }
              """,
            """
              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;

              class Test {
                  private static final Logger LOGGER = LoggerFactory.getLogger(Test.class);

                  void test() {
                      try {
                      } catch(Throwable t) {
                          LOGGER.error("Oh no", t);
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void dontChangePrintStackTrace() {
        rewriteRun(
          spec -> spec.recipe(new SystemErrToLogging(true, "LOGGER", null)),
          //language=java
          java(
            """
              class Test {
                  void test() {
                      try {
                      } catch(Throwable t) {
                          t.printStackTrace();
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void inRunnable() {
        rewriteRun(
          spec -> spec.recipe(new SystemErrToLogging(null, "LOGGER", null)),
          //language=java
          java(
            """
              import org.slf4j.Logger;
              class Test {
                  Logger logger;

                  void test() {
                      Runnable r = () -> System.err.println("single");
                  }
              }
              """,
            """
              import org.slf4j.Logger;
              class Test {
                  Logger logger;

                  void test() {
                      Runnable r = () -> logger.error("single");
                  }
              }
              """
          )
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite-logging-frameworks/issues/114")
    void supportLombokLogAnnotations() {
        rewriteRun(
          spec -> spec.recipe(new SystemErrToLogging(null, null, null))
            .typeValidationOptions(TypeValidation.builder().identifiers(false).build()),
          //language=java
          java(
            """
              import lombok.extern.slf4j.Slf4j;
              @Slf4j
              class Test {
                  int n;

                  void test() {
                      try {
                      } catch(Throwable t) {
                          System.err.println("Oh " + n + " no");
                          t.printStackTrace();
                      }
                  }
              }
              """,
            """
              import lombok.extern.slf4j.Slf4j;
              @Slf4j
              class Test {
                  int n;

                  void test() {
                      try {
                      } catch(Throwable t) {
                          log.error("Oh {} no", n, t);
                      }
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-logging-frameworks/issues/192")
    @Test
    void switchCaseStatements() {
        rewriteRun(
          spec -> spec.recipe(new SystemErrToLogging(false, "logger", "SLF4J")),
          //language=java
          java(
            """
              class A {
                  org.slf4j.Logger logger = null;

                  void m(int cnt) {
                      switch (cnt) {
                          case 1:
                              java.util.List<Integer> numbers = new java.util.ArrayList<>();
                              numbers.forEach(o -> System.err.println(String.valueOf(o)));
                              break;
                          case 2:
                          default:
                              break;
                      }
                  }
              }
              """,
            """
              class A {
                  org.slf4j.Logger logger = null;

                  void m(int cnt) {
                      switch (cnt) {
                          case 1:
                              java.util.List<Integer> numbers = new java.util.ArrayList<>();
                              numbers.forEach(o -> logger.error(String.valueOf(o)));
                              break;
                          case 2:
                          default:
                              break;
                      }
                  }
              }
              """
          ),
          //language=java
          java(
            """
              class B {
                  org.slf4j.Logger logger = null;

                  void m(int cnt) {
                      int val = switch (cnt) {
                          case 1:
                              java.util.List<Integer> numbers = new java.util.ArrayList<>();
                              numbers.forEach(o -> System.err.println(String.valueOf(o)));
                              yield 1;
                          case 2:
                          default:
                              yield 2;
                      };
                  }
              }
              """,
            """
              class B {
                  org.slf4j.Logger logger = null;

                  void m(int cnt) {
                      int val = switch (cnt) {
                          case 1:
                              java.util.List<Integer> numbers = new java.util.ArrayList<>();
                              numbers.forEach(o -> logger.error(String.valueOf(o)));
                              yield 1;
                          case 2:
                          default:
                              yield 2;
                      };
                  }
              }
              """
          ),
          //language=java
          java(
            """
              class Test {
                  org.slf4j.Logger logger = null;

                  void method(int cnt, String name) {
                      switch (cnt) {
                          case 1:
                              try {
                                  yield "bla";
                              } catch (Exception e) {
                                  System.err.println("This is a message for " + name + " with a $ dollar sign");
                                  yield "bla";
                              }
                              break;
                          default:
                              break;
                      }
                  }
              }
              """,
            """
              class Test {
                  org.slf4j.Logger logger = null;

                  void method(int cnt, String name) {
                      switch (cnt) {
                          case 1:
                              try {
                                  yield "bla";
                              } catch (Exception e) {
                                  logger.error("This is a message for {} with a $ dollar sign", name);
                                  yield "bla";
                              }
                              break;
                          default:
                              break;
                      }
                  }
              }"""
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-logging-frameworks/issues/192")
    @Test
    void switchCaseStatementsWithAdditionalMethods() {
        rewriteRun(
          spec -> spec.recipe(new SystemErrToLogging(false, "logger", "SLF4J")),
          //language=java
          java(
            """
              class A {
                  org.slf4j.Logger logger = null;

                  void m(int cnt) {
                      switch (cnt) {
                          case 1:
                              System.err.println("Oh no");
                              break;
                          case 2:
                          default:
                              break;
                      }
                  }

                  String m2() {
                      return null;
                  }
              }
              """,
            """
              class A {
                  org.slf4j.Logger logger = null;

                  void m(int cnt) {
                      switch (cnt) {
                          case 1:
                              logger.error("Oh no");
                              break;
                          case 2:
                          default:
                              break;
                      }
                  }

                  String m2() {
                      return null;
                  }
              }
              """
          ),
          //language=java
          java(
            """
              class B {
                  org.slf4j.Logger logger = null;

                  void m(int cnt, Throwable t) {
                      switch (cnt) {
                          case 1:
                              System.err.println("Oh " + cnt + " no");
                              t.printStackTrace();
                              break;
                          case 2:
                          default:
                              break;
                      }
                  }

                  String m2() {
                      return null;
                  }
              }
              """,
            """
              class B {
                  org.slf4j.Logger logger = null;

                  void m(int cnt, Throwable t) {
                      switch (cnt) {
                          case 1:
                              logger.error("Oh {} no", cnt, t);
                              break;
                          case 2:
                          default:
                              break;
                      }
                  }

                  String m2() {
                      return null;
                  }
              }
              """
          )
        );
    }

}
