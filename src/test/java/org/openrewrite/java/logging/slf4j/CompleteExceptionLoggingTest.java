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
    void regular() {
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
                          // #1. `e.getMessage()` is the first parameter, then add `e` as follows
                          LOG.error(e.getMessage());
                          
                          // #2, String contains no format specifiers, `e.getMessage()` should be `e`.
                          LOG.error("An error occurred", e.getMessage());
                          
                          // #3, String contains format specifiers, add `e` as follows.
                          LOG.error("Error message : {}", e.getMessage());
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
                          // #1. `e.getMessage()` is the first parameter, then add `e` as follows
                          LOG.error(e.getMessage(), e);
                          
                          // #2, String contains no format specifiers, `e.getMessage()` should be `e`.
                          LOG.error("An error occurred", e);
 
                          // #3, String contains format specifiers, add `e` as follows.
                          LOG.error("Error message : {}", e.getMessage(), e);
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
                          // #4, getMessage() is part of a string, no change
                          LOG.error("Error message : " + e.getMessage());

                          // #5, getMessage() is not a parameter of LOG methods, no change
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
}
