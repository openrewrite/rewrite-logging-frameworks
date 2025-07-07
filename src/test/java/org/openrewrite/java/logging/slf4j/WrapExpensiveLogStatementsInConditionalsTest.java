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
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.openrewrite.java.Assertions.java;

class WrapExpensiveLogStatementsInConditionalsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new WrapExpensiveLogStatementsInConditionals())
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

    @CsvSource(textBlock = """
      info, Info
      debug, Debug
      trace, Trace
      """)
    @ParameterizedTest
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
                      expensiveOp();
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
                      expensiveOp();
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
    void whiteSpaceAndComments() {
        rewriteRun(
          //language=java
          java(
            """
              import org.slf4j.Logger;

              class A {
                  void method(Logger LOG) {
                      System.out.println();

                      // Log this as Info
                      LOG.info("Do something {}", expensiveOp());

                      // Log this also
                      LOG.info("Do something else {}", expensiveOp());
                      // And this
                      LOG.info("Do something different {}", expensiveOp());

                      System.out.println();
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
                      System.out.println();

                      if (LOG.isInfoEnabled()) {
                          // Log this as Info
                          LOG.info("Do something {}", expensiveOp());

                          // Log this also
                          LOG.info("Do something else {}", expensiveOp());
                          // And this
                          LOG.info("Do something different {}", expensiveOp());
                      }

                      System.out.println();
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
    void leaveSurroundingFormatting() {
        rewriteRun(
          //language=java
          java(
            """
              import org.slf4j.Logger;
              import java.lang.StringBuilder;

              class A {
                  String method(Logger LOG, StringBuilder builder) {
                          System.out.println();

                      // Log this as Info
                      LOG.info("Do something {}", expensiveOp());

                      // Log this also
                      LOG.info("Do something else {}", expensiveOp());
                      // And this
                      LOG.info("Do something different {}", expensiveOp());

                      return builder
                                                  .append("test")
                                          .append(1)
                                 .append(true)
                                 .toString();
                  }

                  String expensiveOp() {
                      return "expensive";
                  }
              }
              """,
            """
              import org.slf4j.Logger;
              import java.lang.StringBuilder;

              class A {
                  String method(Logger LOG, StringBuilder builder) {
                          System.out.println();

                      if (LOG.isInfoEnabled()) {
                          // Log this as Info
                          LOG.info("Do something {}", expensiveOp());

                          // Log this also
                          LOG.info("Do something else {}", expensiveOp());
                          // And this
                          LOG.info("Do something different {}", expensiveOp());
                      }

                      return builder
                                                  .append("test")
                                          .append(1)
                                 .append(true)
                                 .toString();
                  }

                  String expensiveOp() {
                      return "expensive";
                  }
              }
              """
          )
        );
    }

    private static Stream<Arguments> wrapWhenExpensiveArgument() {
        return Stream.of(
          Arguments.of("notAGetter()"), // not a getter
          Arguments.of("new A()"), // allocating a new object
          Arguments.of("new A().getClass()"), // allocating a new object first
          Arguments.of("\"foo\".getBytes()"), // allocating a string first
          Arguments.of("input.getBytes(StandardCharsets.UTF_16)"), // getter with an argument
          Arguments.of("getClass().getName()"), // getter on a method invocation expression
          Arguments.of("optional.get()"), // not a getter
          Arguments.of("A.getExpensive()"), // static getter likely to use external resources or allocate things
          Arguments.of("getExpensive()"), // static getter likely to use external resources or allocate things
          Arguments.of("342 + input"), // allocating a new string
          Arguments.of("\"foo\" + getClass()"), // allocating a new string
          Arguments.of("true && isSomething(1)")
        );
    }

    @MethodSource
    @ParameterizedTest
    void wrapWhenExpensiveArgument(String logArgument) {
        //language=java
        rewriteRun(
          java(
            String.format("""
              import java.nio.charset.StandardCharsets;
              import java.util.Optional;
              import org.slf4j.Logger;

              class A {
                  void method(Logger log, String input, Optional<String> optional, boolean boolVariable) {
                      log.info("{}", %s);
                  }

                  String notAGetter() {
                      return "property";
                  }

                  static String getExpensive() {
                      return "expensive";
                  }

                  boolean isSomething(int i) {
                      return true;
                  }
              }
              """, logArgument),
            String.format("""
              import java.nio.charset.StandardCharsets;
              import java.util.Optional;
              import org.slf4j.Logger;

              class A {
                  void method(Logger log, String input, Optional<String> optional, boolean boolVariable) {
                      if (log.isInfoEnabled()) {
                          log.info("{}", %s);
                      }
                  }

                  String notAGetter() {
                      return "property";
                  }

                  static String getExpensive() {
                      return "expensive";
                  }

                  boolean isSomething(int i) {
                      return true;
                  }
              }
              """, logArgument)
          )
        );
    }

    private static Stream<Arguments> dontWrapWhenCheapArgument() {
        // We don't allocate stuff or very unlikely:
        return Stream.of(
          Arguments.of("input"), // identifier alone
          Arguments.of("getClass()"), // a getter
          Arguments.of("log.getName()"), // a getter
          Arguments.of("34 + 78"), // literal
          Arguments.of("8344"), // literal
          Arguments.of("\"like, literally!\""), // literal
          Arguments.of("\"one\" + \"two\" + \"three\""), // compile time literal
          Arguments.of("\"one\" + 1"), // compile time literal
          Arguments.of("true && false"), // boolean literal
          Arguments.of("true && isSomething()"), // boolean literal and boolean getter
          Arguments.of("true && boolVariable || isSomething()") // boolean literal and boolean variable
        );
    }

    @MethodSource
    @ParameterizedTest
    void dontWrapWhenCheapArgument(String logArgument) {
        //language=java
        rewriteRun(
          java(
            String.format("""
              import org.slf4j.Logger;

              class A {
                  void method(Logger log, String input, boolean boolVariable) {
                      log.info("{}", %s);
                  }

                  boolean isSomething() {
                      return true;
                  }
              }
              """, logArgument)
          )
        );
    }
}
