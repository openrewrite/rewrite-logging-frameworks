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
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class Log4j2ToSlf4j1Test implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(Environment.builder()
            .scanRuntimeClasspath("org.openrewrite.java.logging")
            .build()
            .activateRecipes("org.openrewrite.java.logging.slf4j.Log4j2ToSlf4j1"))
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "log4j-api-2.+", "log4j-core-2.+", "lombok-1.18.+"));
    }

    @DocumentExample
    @Test
    void logLevelFatalToError() {
        //language=java
        rewriteRun(
          java(
            """
              import org.apache.logging.log4j.Logger;

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

    @Test
    void loggerUsage() {
        //language=java
        rewriteRun(
          java(
            """
             import org.apache.logging.log4j.Logger;
             import org.apache.logging.log4j.LogManager;

             class Test {
                 private static final Logger LOGGER = LogManager.getLogger(Test.class);
                 private static final Logger ROOT_LOGGER = LogManager.getRootLogger();

                 public static void main(String[] args) {
                     if (LOGGER.isDebugEnabled()) {
                         LOGGER.debug("logger message");
                     }
                     ROOT_LOGGER.info("root logger message");
                 }
             }
             """,
            """
             import org.slf4j.Logger;
             import org.slf4j.LoggerFactory;

             class Test {
                 private static final Logger LOGGER = LoggerFactory.getLogger(Test.class);
                 private static final Logger ROOT_LOGGER = LoggerFactory.getRootLogger();

                 public static void main(String[] args) {
                     if (LOGGER.isDebugEnabled()) {
                         LOGGER.debug("logger message");
                     }
                     ROOT_LOGGER.info("root logger message");
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
              import lombok.extern.log4j.Log4j;

              @Log4j
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

    @Test
    void unparameterizedLogging() {
        //language=java
        rewriteRun(
          java("""
            package foo;
            public class LogConstants {
                public static final String CONSTANT = "constant";
            }
            """),
          java(
            """
              import foo.LogConstants;
              import org.apache.logging.log4j.Logger;

              class Test {
                  Logger logger;
                  void method(String arg) {
                      logger.info(LogConstants.CONSTANT + arg);
                  }
              }
              """,
            """
              import foo.LogConstants;
              import org.slf4j.Logger;

              class Test {
                  Logger logger;
                  void method(String arg) {
                      logger.info("{}{}", LogConstants.CONSTANT, arg);
                  }
              }
              """
          )
        );
    }
}
