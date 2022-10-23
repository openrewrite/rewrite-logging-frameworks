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
package org.openrewrite.java.logging.log4j;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class Log4j1ToLog4j2Test implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(Environment.builder()
            .scanRuntimeClasspath("org.openrewrite.java.logging")
            .build()
            .activateRecipes("org.openrewrite.java.logging.log4j.Log4j1ToLog4j2"))
          .parser(JavaParser.fromJavaVersion().classpath("log4j"));
    }

    @Test
    void loggerToLogManager() {
        //language=java
        rewriteRun(
          java(
            """
              import org.apache.log4j.Logger;
              
              class Test {
                  Logger logger = Logger.getLogger(Test.class);
              }
              """,
            """
              import org.apache.logging.log4j.LogManager;
              import org.apache.logging.log4j.Logger;
              
              class Test {
                  Logger logger = LogManager.getLogger(Test.class);
              }
              """
          )
        );
    }

    @Test
    void getRootLoggerToLogManager() {
        //language=java
        rewriteRun(
          java(
            """
              import org.apache.log4j.Logger;
              import org.apache.log4j.LogManager;
              class Test {
                  Logger logger0 = Logger.getRootLogger();
                  Logger logger1 = LogManager.getRootLogger();
              }
              """,
            """
              import org.apache.logging.log4j.Logger;
              import org.apache.logging.log4j.LogManager;
              class Test {
                  Logger logger0 = LogManager.getRootLogger();
                  Logger logger1 = LogManager.getRootLogger();
              }
              """
          )
        );
    }

    @Test
    void loggerGetEffectiveLevel() {
        //language=java
        rewriteRun(
          java(
            """
              import org.apache.log4j.Logger;
              class Test {
                  static void method(Logger logger) {
                      logger.getEffectiveLevel();
                  }
              }
              """,
            """
              import org.apache.logging.log4j.Logger;
              class Test {
                  static void method(Logger logger) {
                      logger.getLevel();
                  }
              }
              """
          )
        );
    }
}
