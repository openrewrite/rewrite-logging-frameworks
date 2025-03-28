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
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings("RedundantSlf4jDefinition")
class CatchBlockLogLevelTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new CatchBlockLogLevel());
    }

    @DocumentExample
    @Test
    void log4j1() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion().classpath("log4j")),
          //language=java
          java(
               """
              import org.apache.log4j.Logger;

              class A {
                  Logger log = Logger.getLogger(A.class);
                  void test() {
                      try {
                          log.info("unchanged");
                          throw new RuntimeException();
                      } catch (Exception e) {
                          log.info("Some context");
                          log.info("Caught exception", e);
                      }
                  }
              }
              """,
            """
              import org.apache.log4j.Logger;

              class A {
                  Logger log = Logger.getLogger(A.class);
                  void test() {
                      try {
                          log.info("unchanged");
                          throw new RuntimeException();
                      } catch (Exception e) {
                          log.warn("Some context");
                          log.error("Caught exception", e);
                      }
                  }
              }
              """)
        );
    }

    @Test
    void log4j2() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion().classpath("log4j-core", "log4j-api")),
          //language=java
          java(
               """
              import org.apache.logging.log4j.LogManager;
              import org.apache.logging.log4j.Logger;

              class A {
                  Logger log = LogManager.getLogger(A.class);
                  void test() {
                      try {
                          log.info("unchanged");
                          throw new RuntimeException();
                      } catch (Exception e) {
                          log.info("Some context");
                          log.info("Caught exception", e);
                      }
                  }
              }
              """,
            """
              import org.apache.logging.log4j.LogManager;
              import org.apache.logging.log4j.Logger;

              class A {
                  Logger log = LogManager.getLogger(A.class);
                  void test() {
                      try {
                          log.info("unchanged");
                          throw new RuntimeException();
                      } catch (Exception e) {
                          log.warn("Some context");
                          log.error("Caught exception", e);
                      }
                  }
              }
              """)
        );
    }

    @Test
    void slf4j() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion().classpath("slf4j-api")),
          //language=java
          java(
               """
              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;

              class A {
                  Logger log = LoggerFactory.getLogger(A.class);
                  void test() {
                      try {
                          log.info("unchanged");
                          throw new RuntimeException();
                      } catch (Exception e) {
                          log.info("Some context");
                          log.info("Caught exception", e);
                      }
                  }
              }
              """,
            """
              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;

              class A {
                  Logger log = LoggerFactory.getLogger(A.class);
                  void test() {
                      try {
                          log.info("unchanged");
                          throw new RuntimeException();
                      } catch (Exception e) {
                          log.warn("Some context");
                          log.error("Caught exception", e);
                      }
                  }
              }
              """)
        );
    }

    @Test
    void logbackClassic() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion().classpath("slf4j-api", "logback-classic", "logback-core")),
          //language=java
          java(
               """
              import ch.qos.logback.classic.Logger;
              import org.slf4j.LoggerFactory;

              class A {
                  Logger log = (Logger) LoggerFactory.getLogger(A.class);
                  void test() {
                      try {
                          log.info("unchanged");
                          throw new RuntimeException();
                      } catch (Exception e) {
                          log.info("Some context");
                          log.info("Caught exception", e);
                      }
                  }
              }
              """,
            """
              import ch.qos.logback.classic.Logger;
              import org.slf4j.LoggerFactory;

              class A {
                  Logger log = (Logger) LoggerFactory.getLogger(A.class);
                  void test() {
                      try {
                          log.info("unchanged");
                          throw new RuntimeException();
                      } catch (Exception e) {
                          log.warn("Some context");
                          log.error("Caught exception", e);
                      }
                  }
              }
              """)
        );

    }
}
