package org.openrewrite.java.logging.log4j;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

public class LoggingExceptionConcatenationTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new LoggingExceptionConcatenationRecipe())
          .parser(JavaParser.fromJavaVersion().classpath("log4j-api"));
    }

    @Test
    void loggingException() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.logging.log4j.Logger;
                            
              class Test {
                  void test(Logger logger, RuntimeException e) {
                      logger.error("test" + e);
                  }
              }
              """,
            """
              import org.apache.logging.log4j.Logger;
                            
              class Test {
                  void test(Logger logger, RuntimeException e) {
                      logger.error("test", e);
                  }
              }
              """
          )
        );
    }
}
