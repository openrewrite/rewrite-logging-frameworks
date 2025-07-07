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
import org.openrewrite.kotlin.KotlinParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.kotlin.Assertions.kotlin;

@SuppressWarnings({
  "EmptyTryBlock",
  "SimplifyStreamApiCallChains",
  "PlaceholderCountMatchesArgumentCount"
})
class ParameterizedLoggingTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion()
          .classpathFromResources(new InMemoryExecutionContext(), "slf4j-api-2.1.+", "log4j-api-2.+", "log4j-core-2.+", "lombok"));
    }

    @DocumentExample
    @Test
    void basicParameterization() {
        rewriteRun(
          spec -> spec.recipe(new ParameterizedLogging("org.slf4j.Logger info(..)", false)),
          //language=java
          java(
            """
              import org.slf4j.Logger;

              class Test {
                  static void method(Logger logger, String name) {
                      logger.info("Hello " + name + ", nice to meet you " + name);
                  }
              }
              """,
            """
              import org.slf4j.Logger;

              class Test {
                  static void method(Logger logger, String name) {
                      logger.info("Hello {}, nice to meet you {}", name, name);
                  }
              }
              """
          )
        );
    }

    @SuppressWarnings("UnnecessaryToStringCall")
    @Test
    void noNeedToCallToStringOnParameterizedArgument() {
        rewriteRun(
          spec -> spec.recipe(new ParameterizedLogging("org.slf4j.Logger info(..)", true)),
          //language=java
          java(
            """
              import org.slf4j.Logger;

              class Test {
                  static void method(Logger logger, Object person) {
                      logger.info("Hello " + person.toString() + ", your name has " + person.toString().length() + " characters. Just counting " + person.toString());
                  }
              }
              """,
            """
              import org.slf4j.Logger;

              class Test {
                  static void method(Logger logger, Object person) {
                      logger.info("Hello {}, your name has {} characters. Just counting {}", person, person.toString().length(), person);
                  }
              }
              """
          )
        );
    }

    @SuppressWarnings("UnnecessaryToStringCall")
    @Test
    void noNeedToCallToStringOnParameterizedArgumentOfAnyType() {
        rewriteRun(
          spec -> spec.recipe(new ParameterizedLogging("org.slf4j.Logger info(..)", true)),
          //language=java
          java(
            """
              import java.util.stream.Stream;
              import org.slf4j.Logger;

              class Test {
                  static void method(Logger logger, Stream person) {
                      logger.info("Hello " + person.toString() + ", your name has " + person.toString().length() + " characters. Just counting " + person.toString());
                  }
              }
              """,
            """
              import java.util.stream.Stream;
              import org.slf4j.Logger;

              class Test {
                  static void method(Logger logger, Stream person) {
                      logger.info("Hello {}, your name has {} characters. Just counting {}", person, person.toString().length(), person);
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotRemoveStringOnParameterizedArgument() {
        rewriteRun(
          spec -> spec.recipe(new ParameterizedLogging("org.slf4j.Logger info(..)", false)),
          //language=java
          java(
            """
              import org.slf4j.Logger;

              class Test {
                  static void method(Logger logger, Object person) {
                      logger.info("Hello " + person.toString());
                  }
              }
              """,
            """
              import org.slf4j.Logger;

              class Test {
                  static void method(Logger logger, Object person) {
                      logger.info("Hello {}", person.toString());
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-logging-frameworks/issues/36")
    @Test
    void handleEscapedCharacters() {
        rewriteRun(
          spec -> spec.recipe(new ParameterizedLogging("org.slf4j.Logger info(..)", false)),
          //language=java
          java(
            """
              import org.slf4j.Logger;

              class Test {
                  static void method(Logger logger, String str) {
                      logger.info("\\n" + str);
                      logger.info("\\t" + str);
                      logger.info("\\r" + str);
                      logger.info("\\"escape\\" " + str);
                      logger.info("use \\"escape\\" " + str);
                  }
              }
              """,
            """
              import org.slf4j.Logger;

              class Test {
                  static void method(Logger logger, String str) {
                      logger.info("\\n{}", str);
                      logger.info("\\t{}", str);
                      logger.info("\\r{}", str);
                      logger.info("\\"escape\\" {}", str);
                      logger.info("use \\"escape\\" {}", str);
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-logging-frameworks/issues/30")
    @Test
    void escapeMessageStrings() {
        rewriteRun(
          spec -> spec.recipe(new ParameterizedLogging("org.slf4j.Logger info(..)", false)),
          //language=java
          java(
            """
              import org.slf4j.Logger;

              class Test {
                  static void method(Logger logger, String text) {
                      logger.info("See link #" + text);
                  }
              }
              """,
            """
              import org.slf4j.Logger;

              class Test {
                  static void method(Logger logger, String text) {
                      logger.info("See link #{}", text);
                  }
              }
              """
          )
        );
    }

    @Test
    void exceptionArgumentsAsConcatenatedString() {
        rewriteRun(
          spec -> spec.recipe(new ParameterizedLogging("org.slf4j.Logger debug(..)", false)),
          //language=java
          java(
            """
              import org.slf4j.Logger;

              class Test {
                  static void asInteger(Logger logger, String numberString) {
                      try {
                          Integer i = Integer.valueOf(numberString);
                      } catch (NumberFormatException ex) {
                          logger.debug("some big error: " + ex);
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
                          logger.debug("some big error: {}", ex);
                      }
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-logging-frameworks/issues/38")
    @Test
    void indexOutOfBoundsExceptionOnParseMethodArguments() {
        rewriteRun(
          spec -> spec.recipe(new ParameterizedLogging("org.slf4j.Logger warn(..)", false)),
          //language=java
          java(
            """
              import org.slf4j.Logger;

              import java.util.List;

              class Test {
                  static void method(Logger logger, List<String> nameSpaces) {
                      nameSpaces.stream()
                              .forEach(namespace -> {
                                  try {
                                  } catch (Exception ex) {
                                      logger.warn("Couldn't get the pods in namespace:" + namespace, ex);
                                  }
                              });
                  }
              }
              """,
            """
              import org.slf4j.Logger;

              import java.util.List;

              class Test {
                  static void method(Logger logger, List<String> nameSpaces) {
                      nameSpaces.stream()
                              .forEach(namespace -> {
                                  try {
                                  } catch (Exception ex) {
                                      logger.warn("Couldn't get the pods in namespace:{}", namespace, ex);
                                  }
                              });
                  }
              }
              """
          )
        );
    }

    @Test
    void exceptionArgumentsWithThrowable() {
        rewriteRun(
          spec -> spec.recipe(new ParameterizedLogging("org.slf4j.Logger warn(..)", false)),
          //language=java
          java(
            """
              import org.slf4j.Logger;

              class Test {
                  static void asInteger(Logger logger, String numberString) {
                      try {
                          Integer i = Integer.valueOf(numberString);
                      } catch (NumberFormatException ex) {
                          logger.warn("some big error: " + ex.getMessage(), ex);
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
                          logger.warn("some big error: {}", ex.getMessage(), ex);
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void alreadyParameterizedThrowableArguments() {
        rewriteRun(
          spec -> spec.recipe(new ParameterizedLogging("org.slf4j.Logger warn(..)", false)),
          //language=java
          java(
            """
              import org.slf4j.Logger;

              class Test {
                  static void asInteger(Logger logger, String numberString) {
                      try {
                          Integer i = Integer.valueOf(numberString);
                      } catch (NumberFormatException ex) {
                          logger.warn("Invalid parameter: {}", ex.getMessage(), ex);
                      }
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-logging-frameworks/issues/35")
    @Test
    void alreadyParameterizedBinaryExpressionArguments() {
        rewriteRun(
          spec -> spec.recipe(new ParameterizedLogging("org.slf4j.Logger debug(..)", false)),
          //language=java
          java(
            """
              import org.slf4j.Logger;

              class Test {
                  static void method(Logger logger, Long startTime, Long endTime) {
                      logger.debug("Time taken {} for indexing taskExecution logs", endTime - startTime);
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-logging-frameworks/issues/26")
    @Test
    void argumentsContainingBinaryExpressions() {
        rewriteRun(
          spec -> spec.recipe(new ParameterizedLogging("org.slf4j.Logger debug(..)", false)),
          //language=java
          java(
            """
              import org.slf4j.Logger;

              class Test {
                  static void method(Logger logger, String name, double percent) {
                      logger.debug("Process [" + name + "] is at [" + percent * 100 + "%]");
                  }
              }
              """,
            """
              import org.slf4j.Logger;

              class Test {
                  static void method(Logger logger, String name, double percent) {
                      logger.debug("Process [{}] is at [{}%]", name, percent * 100);
                  }
              }
              """
          )
        );
    }

    @Test
    void throwableParameters() {
        rewriteRun(
          spec -> spec.recipe(new ParameterizedLogging("org.apache.logging.log4j.Logger error(..)", false)),
          //language=java
          java(
            """
              import org.apache.logging.log4j.Logger;

              class Test {
                  static void method(Logger logger, String numberString) {
                      try {
                          Integer i = Integer.valueOf(numberString);
                      } catch (Exception e) {
                          logger.error(e.getMessage(), e);
                      }
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-logging-frameworks/issues/131")
    @Test
    void throwableSingleParameter() {
        rewriteRun(
          spec -> spec.recipe(new ParameterizedLogging("org.apache.logging.log4j.Logger error(..)", false)),
          //language=java
          java(
            """
              import org.apache.logging.log4j.Logger;

              class Test {
                  static void method(Logger logger, String numberString) {
                      try {
                          Integer i = Integer.valueOf(numberString);
                      } catch (Exception e) {
                          logger.error(e);
                      }
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-logging-frameworks/issues/58")
    @Test
    void methodInvocationReturnTypeIsString() {
        rewriteRun(
          spec -> spec.recipe(new ParameterizedLogging("org.apache.logging.log4j.Logger info(..)", false)),
          //language=java
          java(
            """
              import org.apache.logging.log4j.Logger;

              class Test {
                  static String getMessage() {return "";}
                  static void method(Logger logger, StringBuilder sb) {
                      logger.info(getMessage());
                  }
              }
              """
          )
        );
    }

    @Test
    void objectParameters() {
        rewriteRun(
          spec -> spec.recipe(new ParameterizedLogging("org.apache.logging.log4j.Logger info(..)", false)),
          //language=java
          java(
            """
              import org.apache.logging.log4j.Logger;

              class Test {
                  static void method(Logger logger, Test test) {
                      logger.info(test);
                      logger.info(new Object());
                  }
              }
              """,
            """
              import org.apache.logging.log4j.Logger;

              class Test {
                  static void method(Logger logger, Test test) {
                      logger.info("{}", test);
                      logger.info("{}", new Object());
                  }
              }
              """
          )
        );
    }

    @Test
    void methodInvocationParameters() {
        rewriteRun(
          spec -> spec.recipe(new ParameterizedLogging("org.apache.logging.log4j.Logger info(..)", false)),
          //language=java
          java(
            """
              import org.apache.logging.log4j.Logger;

              class Test {
                  static void method(Logger logger, StringBuilder sb) {
                      logger.info(new StringBuilder("append0").append("append1").append(sb));
                  }
              }
              """,
            """
              import org.apache.logging.log4j.Logger;

              class Test {
                  static void method(Logger logger, StringBuilder sb) {
                      logger.info("{}", new StringBuilder("append0").append("append1").append(sb));
                  }
              }
              """
          )
        );
    }

    @Test
    void logMethodWithDollarSign() {
        rewriteRun(
          spec -> spec.recipe(new ParameterizedLogging("org.slf4j.Logger info(..)", false)),
          //language=java
          java(
            """
              import org.slf4j.Logger;

              class Test {
                  static void method(Logger logger, String name) {
                      logger.info("This is a message for " + name + " with a $ dollar sign");
                  }
              }
              """,
            """
              import org.slf4j.Logger;

              class Test {
                  static void method(Logger logger, String name) {
                      logger.info("This is a message for {} with a $ dollar sign", name);
                  }
              }
              """
          )
        );
    }

    @Test
    void logMethodWithCurlyBracketsLiteral() {
        rewriteRun(
          spec -> spec.recipe(new ParameterizedLogging("org.slf4j.Logger info(..)", false)),
          //language=java
          java(
            """
              import org.slf4j.Logger;

              class Test {
                  static void method(Logger logger, String name) {
                      logger.info("This is a message for " + name + " with a curly bracket constant: ${exception}");
                  }
              }
              """,
            """
              import org.slf4j.Logger;

              class Test {
                  static void method(Logger logger, String name) {
                      logger.info("This is a message for {} with a curly bracket constant: ${exception}", name);
                  }
              }
              """
          )
        );
    }

    @Test
    void multilineStringLiteralLeftAlone() {
        rewriteRun(
          spec -> spec.recipe(new ParameterizedLogging("org.slf4j.Logger info(..)", false)),
          //language=java
          java(
            """
              import org.slf4j.Logger;

              class Test {
                  static void method(Logger logger) {
                      String one = "one";
                      logger.info("This is a long message, too long to all fit comfortably on " + one + " line. " +
                              "So it is split up into multiple lines. There is no need for the recipe to attempt to optimize this " +
                              "because the compiler will combine these into a single string literal in the bytecode");
                  }
              }
              """,
            """
              import org.slf4j.Logger;

              class Test {
                  static void method(Logger logger) {
                      String one = "one";
                      logger.info("This is a long message, too long to all fit comfortably on {} line. " +
                              "So it is split up into multiple lines. There is no need for the recipe to attempt to optimize this " +
                              "because the compiler will combine these into a single string literal in the bytecode", one);
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-logging-frameworks/issues/159")
    @Test
    void concatenationWithMarker() {
        rewriteRun(
          spec -> spec.recipe(new ParameterizedLogging("org.slf4j.Logger info(..)", false)),
          // language=java
          java(
            """
              import org.slf4j.Logger;
              import org.slf4j.Marker;

              class Test {
                  static void method(Logger logger, Marker marker, String name) {
                      logger.info(marker, "Hello " + name + ", nice to meet you " + name);
                  }
              }
              """,
            """
              import org.slf4j.Logger;
              import org.slf4j.Marker;

              class Test {
                  static void method(Logger logger, Marker marker, String name) {
                      logger.info(marker, "Hello {}, nice to meet you {}", name, name);
                  }
              }
              """
          )
        );
    }

    @Test
    void concatenationWithMarkerAndLambda() {
        rewriteRun(
          spec -> spec.recipe(new ParameterizedLogging("org.apache.logging.log4j.Logger debug(..)", false)),
          // language=java
          java(
            """
              import lombok.extern.log4j.Log4j2;
              import org.apache.logging.log4j.Marker;
              import org.apache.logging.log4j.MarkerManager;

              @Log4j2
               class A {
                  public static final Marker MY_MARKER = MarkerManager.getMarker("my-A");
                  void foo(String bar) {
                      log.debug(MY_MARKER, "foo1");
                      log.debug(MY_MARKER, "foo2 " + bar);
                      log.debug(MY_MARKER, "foo3 " + bar + " foo4 " + bar);
                      log.debug(MY_MARKER, () -> bar);
                  }
              }
              """,
            """
              import lombok.extern.log4j.Log4j2;
              import org.apache.logging.log4j.Marker;
              import org.apache.logging.log4j.MarkerManager;

              @Log4j2
               class A {
                  public static final Marker MY_MARKER = MarkerManager.getMarker("my-A");
                  void foo(String bar) {
                      log.debug(MY_MARKER, "foo1");
                      log.debug(MY_MARKER, "foo2 {}", bar);
                      log.debug(MY_MARKER, "foo3 {} foo4 {}", bar, bar);
                      log.debug(MY_MARKER, () -> bar);
                  }
              }
              """
          )
        );
    }

    @Test
    void noConcatenationWithMarkerAndSupplier() {
        rewriteRun(
          spec -> spec.recipe(new ParameterizedLogging("org.apache.logging.log4j.Logger debug(..)", false)),
          // language=java
          java(
            """
              import lombok.extern.log4j.Log4j2;
              import org.apache.logging.log4j.Marker;
              import org.apache.logging.log4j.MarkerManager;

              import java.util.function.Supplier;

              @Log4j2
               class A {
                  public static final Marker MY_MARKER = MarkerManager.getMarker("my-A");
                  void foo(String bar) {
                      Supplier supplier = () -> bar;
                      log.debug(MY_MARKER, supplier);
                  }
              }
              """
          )
        );
    }

    @Test
    void markerWithObjectParameters() {
        rewriteRun(
          spec -> spec.recipe(new ParameterizedLogging("org.apache.logging.log4j.Logger debug(..)", false)),
          // language=java
          java(
            """
              import lombok.extern.log4j.Log4j2;
              import org.apache.logging.log4j.Marker;
              import org.apache.logging.log4j.MarkerManager;

              @Log4j2
               class Test {
                  public static final Marker MY_MARKER = MarkerManager.getMarker("my-A");
                  void foo(Test test) {
                      log.debug(MY_MARKER, test);
                      log.debug(MY_MARKER, new Object());
                  }
              }
              """,
            """
              import lombok.extern.log4j.Log4j2;
              import org.apache.logging.log4j.Marker;
              import org.apache.logging.log4j.MarkerManager;

              @Log4j2
               class Test {
                  public static final Marker MY_MARKER = MarkerManager.getMarker("my-A");
                  void foo(Test test) {
                      log.debug(MY_MARKER, "{}", test);
                      log.debug(MY_MARKER, "{}", new Object());
                  }
              }
              """
          )
        );
    }

    @Test
    void kotlinStringTemplateSkipped() {
        rewriteRun(
          spec -> spec
            .recipe(new ParameterizedLogging("org.slf4j.Logger info(..)", false))
            .parser(KotlinParser.builder().classpathFromResources(new InMemoryExecutionContext(), "slf4j-api-2.1.+")),
          //language=kotlin
          kotlin(
            """
              import org.slf4j.Logger

              fun main(logger: Logger, name: String) {
                  logger.info("Hello $name")
                  logger.info("Hello " + name)
              }
              """
          )
        );
    }

    @Test
    void logMethodInSwitchInTry() {
        rewriteRun(
          spec -> spec.recipe(new ParameterizedLogging("org.slf4j.Logger info(..)", false)),
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
                                  logger.info("This is a message for " + name + " with a $ dollar sign");
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
                                  logger.info("This is a message for {} with a $ dollar sign", name);
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

    @Test
    void lombokLoggingAnnotation() {
        rewriteRun(
          spec -> spec.recipe(new ParameterizedLogging("org.slf4j.Logger info(..)", false)),
          // language=java
          java(
            """
              import lombok.extern.slf4j.Slf4j;

              @Slf4j
              class Test {
                  static void method(String name) {
                      log.info("Hello " + name + ", nice to meet you " + name);
                  }
              }
              """,
            """
              import lombok.extern.slf4j.Slf4j;

              @Slf4j
              class Test {
                  static void method(String name) {
                      log.info("Hello {}, nice to meet you {}", name, name);
                  }
              }
              """
          )
        );
    }

    @Test
    void returnsAnonymousClassThatUsesLoggerInMethodImplementation() {
        rewriteRun(
            spec -> spec.recipe(new ParameterizedLogging("org.slf4j.Logger info(..)", false)),
            //language=java
            java(
                """
                  import java.util.function.Predicate;
                  import org.slf4j.Logger;

                  class Test {
                      Logger logger;
                      Predicate<String> method() {
                          return new Predicate<String>() {
                              @Override
                              public boolean test(String s) {
                                  logger.info("uh oh: " + s);
                                  return true;
                              }
                          };
                      }
                  }
                  """,
                """
                  import java.util.function.Predicate;
                  import org.slf4j.Logger;

                  class Test {
                      Logger logger;
                      Predicate<String> method() {
                          return new Predicate<String>() {
                              @Override
                              public boolean test(String s) {
                                  logger.info("uh oh: {}", s);
                                  return true;
                              }
                          };
                      }
                  }
                  """
            )
        );
    }

    @Test
    void loggerInAnonymousFunction() {
        rewriteRun(
            spec -> spec.recipe(new ParameterizedLogging("org.slf4j.Logger info(..)", false)),
            //language=java
            java(
                """
                  import java.util.List;
                  import java.util.function.Consumer;
                  import java.util.stream.Collectors;
                  import org.slf4j.Logger;

                  class Test {
                      Logger logger;
                      List<String> method() {
                          List<String> list = List.of("a", "b", "c");
                          return list.stream()
                              .peek(item -> logger.info("uh oh: " + item))
                              .collect(Collectors.toList());
                      }
                  }
                  """,
                """
                  import java.util.List;
                  import java.util.function.Consumer;
                  import java.util.stream.Collectors;
                  import org.slf4j.Logger;

                  class Test {
                      Logger logger;
                      List<String> method() {
                          List<String> list = List.of("a", "b", "c");
                          return list.stream()
                              .peek(item -> logger.info("uh oh: {}", item))
                              .collect(Collectors.toList());
                      }
                  }
                  """
            )
        );
    }

    @Test
    void loggerInInlineClassInstance() {
        rewriteRun(
            spec -> spec.recipe(new ParameterizedLogging("org.slf4j.Logger info(..)", false)),
            //language=java
            java(
                """
                  import org.slf4j.Logger;

                  class Test {
                      Logger logger;
                      void method(String s) {
                          Thread thread = new Thread(new Runnable() {
                              @Override
                              public void run() {
                                  logger.info("uh oh: " + s);
                              }
                          });
                          thread.start();
                      }
                  }
                  """,
                """
                  import org.slf4j.Logger;

                  class Test {
                      Logger logger;
                      void method(String s) {
                          Thread thread = new Thread(new Runnable() {
                              @Override
                              public void run() {
                                  logger.info("uh oh: {}", s);
                              }
                          });
                          thread.start();
                      }
                  }
                  """
            )
        );
    }
}
