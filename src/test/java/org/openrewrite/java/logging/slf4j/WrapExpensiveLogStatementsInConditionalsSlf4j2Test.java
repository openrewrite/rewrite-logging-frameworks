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

class WrapExpensiveLogStatementsInConditionalsSlf4j2Test implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new WrapExpensiveLogStatementsInConditionals())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "slf4j-api-2.1.+"));
    }

    @DocumentExample
    @Test
    void convertToFluentApiWithExpensiveArgument() {
        rewriteRun(
          //language=java
          java(
            """
              import org.slf4j.Logger;

              class A {
                  void method(Logger logger) {
                      logger.info("Result: {}", calculateResult());
                      logger.info("This was {} and {}", doExpensiveOperation(), "bar");
                  }

                  String calculateResult() {
                      return "result";
                  }

                  String doExpensiveOperation() {
                      return "foo";
                  }
              }
              """,
            """
              import org.slf4j.Logger;

              class A {
                  void method(Logger logger) {
                      logger.atInfo().addArgument(() -> calculateResult()).log("Result: {}");
                      logger.atInfo().addArgument(() -> doExpensiveOperation()).addArgument("bar").log("This was {} and {}");
                  }

                  String calculateResult() {
                      return "result";
                  }

                  String doExpensiveOperation() {
                      return "foo";
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
    void allLogMethodsWithFluentApi(String method, String level) {
        rewriteRun(
          java(
            """
              import org.slf4j.Logger;

              class A {
                  void method(Logger logger) {
                      logger.%s("SomeString {}, {}", "some param", expensiveOp());
                  }

                  String expensiveOp() {
                      return "expensive";
                  }
              }
              """.formatted(method),
            """
              import org.slf4j.Logger;

              class A {
                  void method(Logger logger) {
                      logger.at%s().addArgument("some param").addArgument(() -> expensiveOp()).log("SomeString {}, {}");
                  }

                  String expensiveOp() {
                      return "expensive";
                  }
              }
              """.formatted(level)
          )
        );
    }

    @Test
    void doNotConvertWithoutExpensiveArguments() {
        rewriteRun(
          java(
            """
              import org.slf4j.Logger;

              class A {
                  String simpleField = "field";

                  void method(Logger logger) {
                      logger.info("Simple message");
                      logger.info("Message with {}", "literal");
                      logger.info("Message with {}", simpleField);
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotConvertRecordComponentAccessors() {
        rewriteRun(
          java(
            """
              record Track(String title, String artist) {}
              """
          ),
          java(
            """
              import org.slf4j.Logger;

              class A {
                  void method(Logger logger, Track track) {
                      logger.info("Track: {}", track.title());
                      logger.info("Track: {} by {}", track.title(), track.artist());
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotConvertAlreadyFluentApi() {
        rewriteRun(
          java(
            """
              import org.slf4j.Logger;

              class A {
                  void method(Logger logger) {
                      logger.atInfo().log("Some message");
                      logger.atDebug().addArgument("arg").log("With {}");
                  }
              }
              """
          )
        );
    }

    @Test
    void useMethodReferencesForSimpleMethodCalls() {
        rewriteRun(
          java(
            """
              import org.slf4j.Logger;

              class A {
                  void method(Logger logger) {
                      logger.info("Value: {}", computeValue());
                      logger.debug("Complex: {}", computeValue() + " suffix");
                  }

                  String computeValue() {
                      return "value";
                  }
              }
              """,
            """
              import org.slf4j.Logger;

              class A {
                  void method(Logger logger) {
                      logger.atInfo().addArgument(() -> computeValue()).log("Value: {}");
                      logger.atDebug().addArgument(() -> computeValue() + " suffix").log("Complex: {}");
                  }

                  String computeValue() {
                      return "value";
                  }
              }
              """
          )
        );
    }

    @Test
    void handleBlocksWithExpensiveOperationsUsingFluentApi() {
        rewriteRun(
          java(
            """
              import org.slf4j.Logger;

              class A {
                  void method(Logger logger) {
                      System.out.println("an unrelated statement");
                      logger.info(expensiveOp());
                      logger.info("SomeString {}", "some param");
                      logger.info("SomeString {}", expensiveOp());
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
                  void method(Logger logger) {
                      System.out.println("an unrelated statement");
                      logger.atInfo().log(() -> expensiveOp());
                      logger.info("SomeString {}", "some param");
                      logger.atInfo().addArgument(() -> expensiveOp()).log("SomeString {}");
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
}
