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
package org.openrewrite.java.logging.jboss;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class LoggerLevelArgumentToMethodTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new LoggerLevelArgumentToMethod());
    }

    @DocumentExample
    @Test
    void replaceLevelArguments() {
        rewriteRun(
          //language=java
          java(
            """
              import org.jboss.logging.Logger;

              class Test {
                  void test(Logger logger, String msg) {
                      logger.log(Logger.Level.INFO, msg);
                  }
              }
              """,
            """
              import org.jboss.logging.Logger;

              class Test {
                  void test(Logger logger, String msg) {
                      logger.info(msg);
                  }
              }
              """
          )
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"trace", "debug", "info", "warn", "error", "fatal"})
    void replaceLevelArguments(String level) {
        rewriteRun(
          //language=java
          java(
            """
              import org.jboss.logging.Logger;

              import static org.jboss.logging.Logger.Level.%1$S;

              class Test {
                  void test(Logger logger, Object msg, Throwable t, String fqcn) {
                      logger.log(Logger.Level.%1$S, msg);
                      logger.log(%1$S, msg);
                      logger.log(Logger.Level.%1$S, msg, t);
                      logger.log(Logger.Level.%1$S, fqcn, msg, t);
                      logger.log(Logger.Level.%1$S, msg, new String[]{"msg"});
                      logger.log(Logger.Level.%1$S, msg, new String[]{"msg"}, t);
                      logger.log(fqcn, Logger.Level.%1$S, msg, new String[]{"msg"}, t);
                  }
              }
              """.formatted(level),
            """
              import org.jboss.logging.Logger;

              import static org.jboss.logging.Logger.Level.%1$S;

              class Test {
                  void test(Logger logger, Object msg, Throwable t, String fqcn) {
                      logger.%1$s(msg);
                      logger.%1$s(msg);
                      logger.%1$s(msg, t);
                      logger.%1$s(fqcn, msg, t);
                      logger.%1$s(msg, new String[]{"msg"});
                      logger.%1$s(msg, new String[]{"msg"}, t);
                      logger.%1$s(fqcn, msg, new String[]{"msg"}, t);
                  }
              }
              """.formatted(level)
          )
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"trace", "debug", "info", "warn", "error", "fatal"})
    void replaceLevelArgumentsFormatF(String level) {
        rewriteRun(
          //language=java
          java(
            """
              import org.jboss.logging.Logger;

              import static org.jboss.logging.Logger.Level.%1$S;

              class Test {
                  void test(Logger logger, String msg, Throwable t, String fqcn, Object[] p) {
                      logger.logf(Logger.Level.%1$S, msg, p[0]);
                      logger.logf(Logger.Level.%1$S, msg, p[0], p[1]);
                      logger.logf(Logger.Level.%1$S, msg, p[0], p[2], p[3]);
                      logger.logf(Logger.Level.%1$S, msg, p[0], p[2], p[3], p[4]);

                      logger.logf(Logger.Level.%1$S, t, msg, p[0]);
                      logger.logf(Logger.Level.%1$S, t, msg, p[0], p[1]);
                      logger.logf(Logger.Level.%1$S, t, msg, p[0], p[2], p[3]);
                      logger.logf(Logger.Level.%1$S, t, msg, p[0], p[2], p[3], p[4]);

                      logger.logf(fqcn, Logger.Level.%1$S, t, msg, p[0]);
                  }
              }
              """.formatted(level),
            """
              import org.jboss.logging.Logger;

              import static org.jboss.logging.Logger.Level.%1$S;

              class Test {
                  void test(Logger logger, String msg, Throwable t, String fqcn, Object[] p) {
                      logger.%1$sf(msg, p[0]);
                      logger.%1$sf(msg, p[0], p[1]);
                      logger.%1$sf(msg, p[0], p[2], p[3]);
                      logger.%1$sf(msg, p[0], p[2], p[3], p[4]);

                      logger.%1$sf(t, msg, p[0]);
                      logger.%1$sf(t, msg, p[0], p[1]);
                      logger.%1$sf(t, msg, p[0], p[2], p[3]);
                      logger.%1$sf(t, msg, p[0], p[2], p[3], p[4]);

                      logger.logf(fqcn, Logger.Level.%1$S, t, msg, p[0]);
                  }
              }
              """.formatted(level)
          )
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"trace", "debug", "info", "warn", "error", "fatal"})
    void replaceLevelArgumentsFormatV(String level) {
        rewriteRun(
          //language=java
          java(
            """
              import org.jboss.logging.Logger;

              import static org.jboss.logging.Logger.Level.%1$S;

              class Test {
                  void test(Logger logger, String msg, Throwable t, String fqcn, Object[] p) {
                      logger.logv(Logger.Level.%1$S, msg, p[0]);
                      logger.logv(Logger.Level.%1$S, msg, p[0], p[1]);
                      logger.logv(Logger.Level.%1$S, msg, p[0], p[2], p[3]);
                      logger.logv(Logger.Level.%1$S, msg, p[0], p[2], p[3], p[4]);

                      logger.logv(Logger.Level.%1$S, t, msg, p[0]);
                      logger.logv(Logger.Level.%1$S, t, msg, p[0], p[1]);
                      logger.logv(Logger.Level.%1$S, t, msg, p[0], p[2], p[3]);
                      logger.logv(Logger.Level.%1$S, t, msg, p[0], p[2], p[3], p[4]);

                      logger.logv(fqcn, Logger.Level.%1$S, t, msg, p[0]);
                  }
              }
              """.formatted(level),
            """
              import org.jboss.logging.Logger;

              import static org.jboss.logging.Logger.Level.%1$S;

              class Test {
                  void test(Logger logger, String msg, Throwable t, String fqcn, Object[] p) {
                      logger.%1$sv(msg, p[0]);
                      logger.%1$sv(msg, p[0], p[1]);
                      logger.%1$sv(msg, p[0], p[2], p[3]);
                      logger.%1$sv(msg, p[0], p[2], p[3], p[4]);

                      logger.%1$sv(t, msg, p[0]);
                      logger.%1$sv(t, msg, p[0], p[1]);
                      logger.%1$sv(t, msg, p[0], p[2], p[3]);
                      logger.%1$sv(t, msg, p[0], p[2], p[3], p[4]);

                      logger.logv(fqcn, Logger.Level.%1$S, t, msg, p[0]);
                  }
              }
              """.formatted(level)
          )
        );
    }


    @Test
    void noChangeIfLevelUnknown() {
        rewriteRun(
          //language=java
          java(
            """
              import org.jboss.logging.Logger;

              class Test {
                  void test(Logger logger, Logger.Level level, String msg) {
                      logger.log(level, msg);
                  }
              }
              """
          )
        );
    }
}
