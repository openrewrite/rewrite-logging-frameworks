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
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class SystemOutToLoggingTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion()
          .classpathFromResources(new InMemoryExecutionContext(), "slf4j-api-2.1", "lombok-1.18"));
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

    @Test
    @Issue("https://github.com/openrewrite/rewrite-logging-frameworks/issues/114")
    void supportLombokLogAnnotations() {
        rewriteRun(
          spec -> spec.recipe(new SystemOutToLogging(null, null, null, "info"))
            .typeValidationOptions(TypeValidation.builder().identifiers(false).build()),
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

    @Test
    @Issue("https://github.com/openrewrite/rewrite-logging-frameworks/issues/125")
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
}
