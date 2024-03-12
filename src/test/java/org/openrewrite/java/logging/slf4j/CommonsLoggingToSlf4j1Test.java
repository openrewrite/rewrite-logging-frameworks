/*
 * Copyright 2021 the original author or authors.
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
package org.openrewrite.java.logging.slf4j;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class CommonsLoggingToSlf4j1Test implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.typeValidationOptions(TypeValidation.builder().build())
          .recipe(Environment.builder()
            .scanRuntimeClasspath("org.openrewrite.java.logging")
            .build()
            .activateRecipes("org.openrewrite.java.logging.slf4j.CommonsLogging1ToSlf4j1"))
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "commons-logging-1.3", "slf4j-api-2.1", "lombok-1.18"));
    }

    @DocumentExample
    @Test
    void useLoggerFactory() {
        //language=java
        rewriteRun(
          java(
            """
              import org.apache.commons.logging.LogFactory;
              import org.apache.commons.logging.Log;

              class Test {
                  Log logger0 = LogFactory.getLog(Test.class);
                  Log logger1 = LogFactory.getLog("foobar");
                  Log logger2 = LogFactory.getFactory().getInstance(Test.class);
                  Log logger3 = LogFactory.getFactory().getInstance("foobar");
              }
              """,
            """
              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;

              class Test {
                  Logger logger0 = LoggerFactory.getLogger(Test.class);
                  Logger logger1 = LoggerFactory.getLogger("foobar");
                  Logger logger2 = LoggerFactory.getLogger(Test.class);
                  Logger logger3 = LoggerFactory.getLogger("foobar");
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
              import org.apache.commons.logging.LogFactory;
              import org.apache.commons.logging.Log;

              class Test {
                  private static final Log logger0 = LogFactory.getLog(Test.class);
                  private static final Log logger1 = LogFactory.getLog("foobar");
                  private static final Log logger2 = LogFactory.getFactory().getInstance(Test.class);
                  private static final Log logger3 = LogFactory.getFactory().getInstance("foobar");
              }
              """,
            """
              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;

              class Test {
                  private static final Logger logger0 = LoggerFactory.getLogger(Test.class);
                  private static final Logger logger1 = LoggerFactory.getLogger("foobar");
                  private static final Logger logger2 = LoggerFactory.getLogger(Test.class);
                  private static final Logger logger3 = LoggerFactory.getLogger("foobar");
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
              import org.apache.commons.logging.Log;

              class Test {
                  static void method(Log logger) {
                      if (logger.isFatalEnabled()) {
                          logger.fatal("uh oh");
                      }
                  }
              }
              """,
            """
              import org.slf4j.Logger;

              class Test {
                  static void method(Logger logger) {
                      if (logger.isErrorEnabled()) {
                          logger.error("uh oh");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void changeLombokLogAnnotation() {
        //language=java
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.builder().identifiers(false).methodInvocations(false).build()),
          java(
            """
              import lombok.extern.apachecommons.CommonsLog;

              @CommonsLog
              class Test {
                  void method() {
                      log.info("uh oh");
                  }
              }
              """,
            """
              import lombok.extern.slf4j.Slf4j;

              @Slf4j
              class Test {
                  void method() {
                      log.info("uh oh");
                  }
              }
              """
          )
        );
    }
}
