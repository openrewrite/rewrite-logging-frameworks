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
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.logging.logback.Log4jAppenderToLogback;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings("EmptyTryBlock")
class SystemErrToLoggingTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new Log4jAppenderToLogback())
          .parser(JavaParser.fromJavaVersion().classpath("log4j"));
    }

    @DocumentExample
    @Test
    void useSlf4j() {
        rewriteRun(
          spec -> spec.recipe(new SystemErrToLogging(null, "LOGGER", null))
            .parser(JavaParser.fromJavaVersion().classpath("slf4j-api")),
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

    @SuppressWarnings("RedundantSlf4jDefinition")
    @Test
    void addLogger() {
        rewriteRun(
          spec -> spec.recipe(new SystemErrToLogging(true, "LOGGER", null))
            .parser(JavaParser.fromJavaVersion().classpath("slf4j-api")),
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
          spec -> spec.recipe(new SystemErrToLogging(null, "LOGGER", null))
            .parser(JavaParser.fromJavaVersion().classpath("slf4j-api")),
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
            .parser(JavaParser.fromJavaVersion().classpath("slf4j-api", "lombok"))
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
}
