package org.openrewrite.java.logging.slf4j;

import org.junit.jupiter.api.Test;
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

    @Test
    void replaceGetMessageWithException() {
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
}
