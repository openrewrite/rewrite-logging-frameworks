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
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class SystemOutToLoggingTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion()
          .classpathFromResources(new InMemoryExecutionContext(), "slf4j-api-2", "lombok-1.18.+"));
    }

    @DocumentExample
    @Test
    void useSlf4j() {
        rewriteRun(
          spec -> spec.recipe(new SystemOutToLogging(null, "LOGGER", null, "debug")),
          //language=java
          java(
            """
              import org.slf4j.Logger;
              class Test {
                  int n;
                  Logger logger;

                  void test() {
                      System.out.println("Oh " + n + " no");
                  }
              }
              """,
            """
              import org.slf4j.Logger;
              class Test {
                  int n;
                  Logger logger;

                  void test() {
                      logger.debug("Oh {} no", n);
                  }
              }
              """
          )
        );
    }

    @Test
    void inRunnable() {
        rewriteRun(
          spec -> spec.recipe(new SystemOutToLogging(null, "LOGGER", null, "debug")),
          //language=java
          java(
            """
              import org.slf4j.Logger;
              class Test {
                  Logger logger;

                  void test() {
                      Runnable r = () -> System.out.println("single");
                  }
              }
              """,
            """
              import org.slf4j.Logger;
              class Test {
                  Logger logger;

                  void test() {
                      Runnable r = () -> logger.debug("single");
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-logging-frameworks/issues/114")
    @Test
    void supportLombokLogAnnotations() {
        rewriteRun(
          spec -> spec.recipe(new SystemOutToLogging(null, null, null, "info")),
          //language=java
          java(
            """
              import lombok.extern.slf4j.Slf4j;
              @Slf4j
              class Test {
                  int n;

                  void test() {
                      System.out.println("Oh " + n + " no");
                  }
              }
              """,
            """
              import lombok.extern.slf4j.Slf4j;
              @Slf4j
              class Test {
                  int n;

                  void test() {
                      log.info("Oh {} no", n);
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-logging-frameworks/issues/125")
    @Test
    void doNotDeleteFile() {
        rewriteRun(
          spec -> spec
            .recipe(new SystemOutToLogging(true, "log", "JUL", null)),
          //language=java
          java(
            """
              class Foo {
                void bar() {
                  System.out.println("Test");
                }
              }
              """,
            """
              import java.util.logging.Level;
              import java.util.logging.LogManager;
              import java.util.logging.Logger;

              class Foo {
                  private static final Logger log = LogManager.getLogManager().getLogger("Foo");

                  void bar() {
                      log.log(Level.INFO, "Test");
                }
              }
              """
          )
        );
    }

    @Test
    void useSystemLogger() {
        rewriteRun(
          spec -> spec.recipe(new SystemOutToLogging(true, "logger", "SYSTEM", "info")),
          //language=java
          java(
            """
              class Test {
                  void test() {
                      System.out.println("Hello World");
                  }
              }
              """,
            """
              import java.lang.System.Logger;
              import java.lang.System.Logger.Level;

              class Test {
                  private static final Logger logger = System.getLogger("Test");

                  void test() {
                      logger.log(Level.INFO, "Hello World");
                  }
              }
              """
          )
        );
    }

    @Test
    void useSystemLoggerWhereLoggerNamesAnotherType() {
        rewriteRun(
          spec -> spec.recipe(new SystemOutToLogging(true, "logger", "SYSTEM", "info")),
          //language=java
          java(
            """
              import org.slf4j.Logger;

              class Test {
                  Logger audit;

                  void test() {
                      System.out.println("Hello World");
                  }
              }
              """,
            """
              import org.slf4j.Logger;

              import java.lang.System.Logger.Level;

              class Test {
                  private static final System.Logger logger = System.getLogger("Test");
                  Logger audit;

                  void test() {
                      logger.log(Level.INFO, "Hello World");
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotUseLoggerHiddenByParameter() {
        rewriteRun(
          spec -> spec.recipe(new SystemOutToLogging(null, null, null, null)),
          //language=java
          java(
            """
              import org.slf4j.Logger;
              class Test {
                  Logger logger;

                  void test(String logger, Throwable t) {
                      System.out.println("Oh no");
                  }
              }
              """
          ),
          //language=java
          java(
            """
              import lombok.extern.slf4j.Slf4j;
              @Slf4j
              class LombokTest {
                  void test(String log, Throwable t) {
                      System.out.println("Oh no");
                  }
              }
              """
          )
        );
    }

    @Test
    void preferLombokLoggerOverLoggerOfOuterClass() {
        rewriteRun(
          spec -> spec.recipe(new SystemOutToLogging(null, null, null, null)),
          //language=java
          java(
            """
              import lombok.extern.slf4j.Slf4j;
              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;

              class Outer {
                  private static final Logger LOGGER = LoggerFactory.getLogger(Outer.class);

                  @Slf4j
                  static class Inner {
                      void test() {
                          System.out.println("Oh no");
                      }
                  }
              }
              """,
            """
              import lombok.extern.slf4j.Slf4j;
              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;

              class Outer {
                  private static final Logger LOGGER = LoggerFactory.getLogger(Outer.class);

                  @Slf4j
                  static class Inner {
                      void test() {
                          log.info("Oh no");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotUseInstanceLoggerFromStaticContext() {
        rewriteRun(
          spec -> spec.recipe(new SystemOutToLogging(null, null, null, null)),
          //language=java
          java(
            """
              import org.slf4j.Logger;
              class Test {
                  Logger logger;

                  public static void main(String[] args) {
                      System.out.println("Oh no");
                  }

                  static class Nested {
                      void test() {
                          System.out.println("Oh no");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void useInheritedLogger() {
        rewriteRun(
          spec -> spec.recipe(new SystemOutToLogging(true, null, null, null)),
          //language=java
          java(
            """
              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;

              class Base {
                  protected final Logger logger = LoggerFactory.getLogger(getClass());
              }
              """
          ),
          //language=java
          java(
            """
              class Test extends Base {
                  void test() {
                      System.out.println("Oh no");
                  }
              }
              """,
            """
              class Test extends Base {
                  void test() {
                      logger.info("Oh no");
                  }
              }
              """
          )
        );
    }

    @Test
    void useLoggerOfOuterClass() {
        rewriteRun(
          spec -> spec.recipe(new SystemOutToLogging(true, null, null, null)),
          //language=java
          java(
            """
              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;

              class Test {
                  private static final Logger logger = LoggerFactory.getLogger(Test.class);

                  static class Nested {
                      void test() {
                          System.out.println("Oh no");
                      }
                  }
              }
              """,
            """
              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;

              class Test {
                  private static final Logger logger = LoggerFactory.getLogger(Test.class);

                  static class Nested {
                      void test() {
                          logger.info("Oh no");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void useLombokLoggerThatHidesInheritedOrOuterField() {
        rewriteRun(
          spec -> spec.recipe(new SystemOutToLogging(null, null, null, null)),
          //language=java
          java(
            """
              class Base {
                  protected String log = "audit";
              }
              """
          ),
          //language=java
          java(
            """
              import lombok.extern.slf4j.Slf4j;
              @Slf4j
              class Test extends Base {
                  void test() {
                      System.out.println("Oh no");
                  }
              }
              """,
            """
              import lombok.extern.slf4j.Slf4j;
              @Slf4j
              class Test extends Base {
                  void test() {
                      log.info("Oh no");
                  }
              }
              """
          ),

          //language=java
          java(
            """
              import lombok.extern.slf4j.Slf4j;
              class Outer {
                  static String log = "audit";

                  @Slf4j
                  static class Nested {
                      void test() {
                          System.out.println("Oh no");
                      }
                  }
              }
              """,
            """
              import lombok.extern.slf4j.Slf4j;
              class Outer {
                  static String log = "audit";

                  @Slf4j
                  static class Nested {
                      void test() {
                          log.info("Oh no");
                      }
                  }
              }
              """
          )
        );
    }
}
