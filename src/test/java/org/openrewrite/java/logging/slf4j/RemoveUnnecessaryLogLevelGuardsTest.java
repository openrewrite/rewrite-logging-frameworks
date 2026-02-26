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
package org.openrewrite.java.logging.slf4j;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class RemoveUnnecessaryLogLevelGuardsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveUnnecessaryLogLevelGuards())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "slf4j-api-2"));
    }

    @DocumentExample
    @Test
    void replacePatterns() {
        //language=java
        rewriteRun(
          java(
            """
              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;
              import org.slf4j.Marker;
              import org.slf4j.MarkerFactory;

              class Test {
                  private static final Logger LOGGER = LoggerFactory.getLogger(Test.class);
                  private static final Marker MARKER = MarkerFactory.getMarker("TEST");

                  void test(String name, int count, Exception ex) {
                      // all log levels
                      if (LOGGER.isTraceEnabled()) {
                          LOGGER.trace("Trace: {} count={}", name, count);
                      }
                      if (LOGGER.isDebugEnabled()) {
                          LOGGER.debug("Debug: {} count={}", name, count);
                      }
                      if (LOGGER.isInfoEnabled()) {
                          LOGGER.info("Info: {} count={}", name, count);
                      }
                      if (LOGGER.isWarnEnabled()) {
                          LOGGER.warn("Warning: {} count={}", name, count);
                      }
                      if (LOGGER.isErrorEnabled()) {
                          LOGGER.error("Error: {} count={}", name, count);
                      }

                      // multiple statements in guard
                      if (LOGGER.isDebugEnabled()) {
                          LOGGER.debug("First message: {}", name);
                          LOGGER.debug("Second message: {}", count);
                          LOGGER.debug("Third message");
                      }

                      // guard with marker
                      if (LOGGER.isDebugEnabled(MARKER)) {
                          LOGGER.debug(MARKER, "Message: {}", name);
                      }

                      // exception.getMessage() allowed
                      if (LOGGER.isDebugEnabled()) {
                          LOGGER.debug("Error: {}", ex.getMessage());
                      }
                      if (LOGGER.isErrorEnabled()) {
                          LOGGER.error("Failed: {}", ex.getMessage(), ex);
                      }

                      // single statement without braces
                      if (LOGGER.isDebugEnabled())
                          LOGGER.debug("No braces: {}", name);

                      // mixed log levels in guard
                      if (LOGGER.isDebugEnabled()) {
                          LOGGER.debug("Debug: {}", name);
                          LOGGER.info("Also info: {}", name);
                      }
                  }
              }
              """,
            """
              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;
              import org.slf4j.Marker;
              import org.slf4j.MarkerFactory;

              class Test {
                  private static final Logger LOGGER = LoggerFactory.getLogger(Test.class);
                  private static final Marker MARKER = MarkerFactory.getMarker("TEST");

                  void test(String name, int count, Exception ex) {
                      // all log levels
                      LOGGER.trace("Trace: {} count={}", name, count);
                      LOGGER.debug("Debug: {} count={}", name, count);
                      LOGGER.info("Info: {} count={}", name, count);
                      LOGGER.warn("Warning: {} count={}", name, count);
                      LOGGER.error("Error: {} count={}", name, count);

                      // multiple statements in guard
                      LOGGER.debug("First message: {}", name);
                      LOGGER.debug("Second message: {}", count);
                      LOGGER.debug("Third message");

                      // guard with marker
                      LOGGER.debug(MARKER, "Message: {}", name);

                      // exception.getMessage() allowed
                      LOGGER.debug("Error: {}", ex.getMessage());
                      LOGGER.error("Failed: {}", ex.getMessage(), ex);

                      // single statement without braces
                      LOGGER.debug("No braces: {}", name);

                      // mixed log levels in guard
                      LOGGER.debug("Debug: {}", name);
                      LOGGER.info("Also info: {}", name);
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotReplaceInvalidPatterns() {
        //language=java
        rewriteRun(
          java(
            """
              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;

              class Test {
                  private static final Logger LOGGER = LoggerFactory.getLogger(Test.class);

                  void test(String name, boolean extraCondition) {
                      // else branch
                      if (LOGGER.isDebugEnabled()) {
                          LOGGER.debug("Debug: {}", name);
                      } else {
                          doSomething();
                      }

                      // unsafe method call
                      if (LOGGER.isDebugEnabled()) {
                          LOGGER.debug("Result: {}", this.computeExpensiveValue());
                      }

                      // compound condition
                      if (LOGGER.isDebugEnabled() && extraCondition) {
                          LOGGER.debug("Debug: {}", name);
                      }

                      // non-logging body
                      if (LOGGER.isDebugEnabled()) {
                          LOGGER.debug("Debug: {}", name);
                          doSomething();
                      }

                      // negated condition
                      if (!LOGGER.isDebugEnabled()) {
                          doSomething();
                      }

                      // string concatenation in guard
                      if (LOGGER.isDebugEnabled()) {
                          LOGGER.debug("Name: " + name);
                      }
                  }

                  String computeExpensiveValue() {
                      return "expensive";
                  }

                  void doSomething() {
                  }
              }
              """
          )
        );
    }
}
