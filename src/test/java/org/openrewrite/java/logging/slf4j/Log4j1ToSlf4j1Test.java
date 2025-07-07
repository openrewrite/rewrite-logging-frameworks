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
package org.openrewrite.java.logging.slf4j;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Issue;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class Log4j1ToSlf4j1Test implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(Environment.builder()
            .scanRuntimeClasspath("org.openrewrite.java.logging")
            .build()
            .activateRecipes("org.openrewrite.java.logging.slf4j.Log4j1ToSlf4j1"))
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "log4j-1.2.+"));
    }

    @DocumentExample
    @Test
    void useLoggerFactory() {
        //language=java
        rewriteRun(
          java(
            """
              import org.apache.log4j.Logger;

              class Test {
                  Logger logger0 = Logger.getLogger(Test.class);
              }
              """,
            """
              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;

              class Test {
                  Logger logger0 = LoggerFactory.getLogger(Test.class);
              }
              """
          )
        );
    }

    @Test
    void staticFinalLoggerIsStaticFinal() {
        //language=java
        rewriteRun(
          java(
            """
              import org.apache.log4j.Logger;

              class A {
                  private static final Logger logger = Logger.getLogger(A.class);
              }
              """,
            """
              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;

              class A {
                  private static final Logger logger = LoggerFactory.getLogger(A.class);
              }
              """
          ),
          java(
            """
              import org.apache.log4j.Logger;
              import org.apache.log4j.LogManager;

              class B {
                  private static final Logger logger = LogManager.getLogger(B.class);
              }
              """,
            """
              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;

              class B {
                  private static final Logger logger = LoggerFactory.getLogger(B.class);
              }
              """
          ),
          java(
            """
              import org.apache.log4j.Logger;
              import org.apache.log4j.LogManager;

              class C {
                  private static final Logger logger = LogManager.getLogger("C");
              }
              """,
            """
              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;

              class C {
                  private static final Logger logger = LoggerFactory.getLogger("C");
              }
              """
          )
        );
    }

    @Test
    void logLevelFatalToError() {
        //language=java
        rewriteRun(
          java(
            """
              import org.apache.log4j.Logger;

              class Test {
                  static void method(Logger logger) {
                      logger.fatal("uh oh");
                  }
              }
              """,
            """
              import org.slf4j.Logger;

              class Test {
                  static void method(Logger logger) {
                      logger.error("uh oh");
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-logging-frameworks/issues/47")
    @Test
    void migrateMDC() {
        //language=java
        rewriteRun(
          java(
            """
              import org.apache.log4j.MDC;

              class Test {
                  static void method() {
                      MDC.clear();
                  }
              }
              """,
            """
              import org.slf4j.MDC;

              class Test {
                  static void method() {
                      MDC.clear();
                  }
              }
              """
          )
        );
    }
}
