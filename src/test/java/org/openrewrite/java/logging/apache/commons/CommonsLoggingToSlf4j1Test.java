/*
 * Copyright 2023 the original author or authors.
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
package org.openrewrite.java.logging.apache.commons;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class CommonsLoggingToSlf4j1Test implements RewriteTest {

    @DocumentExample
    @Test
    void useLoggerFactory() {
        //language=java
        rewriteRun(
          java(
            """
              import org.apache.commons.logging.Logger;
              import org.slf4.LoggerFactory;

              class Test {
                  Log logger0 = Logger.getLogger(Test.class);
                  Log logger1 = LogManager.getLogger(Test.class);
                  Log logger2 = LogManager.getInstance().getLogger(Test.class);
              }
              """,
            """
              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;

              class Test {
                  Logger logger0 = LoggerFactory.getLogger(Test.class);
                  Logger logger1 = LoggerFactory.getLogger(Test.class);
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
              import org.apache.log4j.LogManager;

              class Test {
                  private static final Logger logger0 = Logger.getLogger(Test.class);
                  private static final Logger logger1 = LogManager.getLogger(Test.class);
                  private static final Logger logger2 = LogManager.getLogger("foobar");
              }
              """,
            """
              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;

              class Test {
                  private static final Logger logger0 = LoggerFactory.getLogger(Test.class);
                  private static final Logger logger1 = LoggerFactory.getLogger(Test.class);
                  private static final Logger logger2 = LoggerFactory.getLogger("foobar");
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
}
