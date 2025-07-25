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
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class ChangeLoggersToPrivateTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ChangeLoggersToPrivate());
    }

    @DocumentExample
    @Test
    void changePublicSlf4jLoggerPrivate() {
        rewriteRun(
          //language=java
          java(
            """
              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;

              class Test {
                  public static final Logger LOGGER = LoggerFactory.getLogger(Test.class);
              }
              """,
            """
              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;

              class Test {
                  private static final Logger LOGGER = LoggerFactory.getLogger(Test.class);
              }
              """
          )
        );
    }

    @Test
    void changePublicLog4j2LoggerPrivate() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.logging.log4j.Logger;
              import org.apache.logging.log4j.LogManager;

              class Test {
                  public static final Logger LOGGER = LogManager.getLogger(Test.class);
              }
              """,
            """
              import org.apache.logging.log4j.Logger;
              import org.apache.logging.log4j.LogManager;

              class Test {
                  private static final Logger LOGGER = LogManager.getLogger(Test.class);
              }
              """
          )
        );
    }

    @Test
    void changeProtectedLog4jLoggerPrivate() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.log4j.Logger;

              class Test {
                  protected Logger log = Logger.getLogger(Test.class);
              }
              """,
            """
              import org.apache.log4j.Logger;

              class Test {
                  private Logger log = Logger.getLogger(Test.class);
              }
              """
          )
        );
    }

    @Test
    void changeDefaultJulLoggerPrivate() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.logging.Logger;

              class Test {
                  static final Logger LOG = Logger.getLogger(Test.class.getName());
              }
              """,
            """
              import java.util.logging.Logger;

              class Test {
                  private static final Logger LOG = Logger.getLogger(Test.class.getName());
              }
              """
          )
        );
    }

    @Test
    void keepExistingPrivateLogger() {
        rewriteRun(
          //language=java
          java(
            """
              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;

              class Test {
                  private final Logger logger = LoggerFactory.getLogger(Test.class);
              }
              """
          )
        );
    }

    @Test
    void notALoggerField() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  public String name = "test";
                  protected int count = 0;
              }
              """
          )
        );
    }

    @Test
    void loggerInInterfaceShouldNotChange() {
        rewriteRun(
          //language=java
          java(
            """
              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;

              interface Constants {
                  Logger logger = LoggerFactory.getLogger(Constants.class);
              }
              """
          )
        );
    }

    @Test
    void loggerInAbstractClassShouldNotChange() {
        rewriteRun(
          //language=java
          java(
            """
              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;

              abstract class Constants {
                  Logger logger = LoggerFactory.getLogger(Constants.class);
              }
              """
          )
        );
    }

    @Test
    void localVariableLoggerShouldNotChange() {
        rewriteRun(
          //language=java
          java(
            """
              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;

              class Test {
                  public void doSomething() {
                      Logger localLog = LoggerFactory.getLogger(Test.class);
                      localLog.info("Hello");
                  }
              }
              """
          )
        );
    }
}
