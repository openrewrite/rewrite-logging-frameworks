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
package org.openrewrite.java.logging;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.staticanalysis.SimplifyArraysAsList;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings("RedundantArrayCreation")
class ArgumentArrayToVarargsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ArgumentArrayToVarargs())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "slf4j-api-2.1.+"));
    }

    @DocumentExample
    @Test
    void objectArrayToVarargs() {
        rewriteRun(
          //language=java
          java(
            """
              import org.slf4j.Logger;
              class Test {
                  Logger logger;
                  void method() {
                      logger.info("Message {} {} {}", new Object[]{"old", "style", "args"});
                  }
              }
              """,
            """
              import org.slf4j.Logger;
              class Test {
                  Logger logger;
                  void method() {
                      logger.info("Message {} {} {}", "old", "style", "args");
                  }
              }
              """
          )
        );
    }

     @Test
     void emptyObjectArray() {
         rewriteRun(
           //language=java
           java(
             """
               import org.slf4j.Logger;
               class Test {
                   Logger logger;
                   void method() {
                       logger.info("Message without placeholders", new Object[]{});
                   }
               }
               """,
             """
               import org.slf4j.Logger;
               class Test {
                   Logger logger;
                   void method() {
                       logger.info("Message without placeholders");
                   }
               }
               """
           )
         );
     }

    @Test
    void singleElementArray() {
        rewriteRun(
          //language=java
          java(
            """
              import org.slf4j.Logger;
              class Test {
                  Logger logger;
                  void method() {
                      logger.warn("Single placeholder: {}", new Object[]{"value"});
                  }
              }
              """,
            """
              import org.slf4j.Logger;
              class Test {
                  Logger logger;
                  void method() {
                      logger.warn("Single placeholder: {}", "value");
                  }
              }
              """
          )
        );
    }

    @SuppressWarnings("ConfusingArgumentToVarargsMethod")
    @Test
    void nonObjectArrayNotConverted() {
        rewriteRun(
          //language=java
          java(
            """
              import org.slf4j.Logger;
              class Test {
                  Logger logger;
                  void method() {
                      logger.info("Message {}", new String[]{"test"});
                  }
              }
              """
          )
        );
    }

    @Test
    void notLastArgumentNotConverted() {
        rewriteRun(
          //language=java
          java(
            """
              import org.slf4j.Logger;
              class Test {
                  Logger logger;
                  void method() {
                      logger.info("Message {} {}", new Object[]{"test"}, "other");
                  }
              }
              """
          )
        );
    }

    @Test
    void variableArrayNotConverted() {
        rewriteRun(
          //language=java
          java(
            """
              import org.slf4j.Logger;
              class Test {
                  Logger logger;
                  void method() {
                      Object[] args = {"old", "style", "args"};
                      logger.info("Message {} {} {}", args);
                  }
              }
              """
          )
        );
    }

    @Test
    void notVarargsMethodParameterTypeNotConverted() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.logging.Level;
              import java.util.logging.Logger;
              class Test {
                  Logger logger;
                  void method(Level level, String msg, Object o) {
                      logger.log(level, msg, new Object[]{o});
                  }
              }
              """
          )
        );
    }
}
