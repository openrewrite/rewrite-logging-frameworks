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
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings("EmptyTryBlock")
class PrintStackTraceToLogErrorTest implements RewriteTest {

    @Test
    void useSlf4j() {
        rewriteRun(
          spec -> spec.recipe(new PrintStackTraceToLogError(null, "LOGGER", null))
            .parser(JavaParser.fromJavaVersion().classpath("slf4j-api")),
          //language=java
          java(
            """
                  import org.slf4j.Logger;
                  class Test {
                      Logger logger;
                      
                      void test() {
                          try {
                          } catch(Throwable t) {
                              t.printStackTrace();
                              t.printStackTrace(System.err);
                              t.printStackTrace(System.out);
                          }
                      }
                  }
              """,
            """
                  import org.slf4j.Logger;
                  class Test {
                      Logger logger;
                      
                      void test() {
                          try {
                          } catch(Throwable t) {
                              logger.error("Exception", t);
                              logger.error("Exception", t);
                              logger.error("Exception", t);
                          }
                      }
                  }
              """
          )
        );
    }

    @Test
    void useLog4j2() {
        rewriteRun(
          spec -> spec.recipe(new PrintStackTraceToLogError(null, "LOGGER", "Log4j2"))
            .parser(JavaParser.fromJavaVersion().classpath("log4j-api")),
          //language=java
          java(
            """
              import org.apache.logging.log4j.Logger;
              class Test {
                  Logger logger;
                  
                  void test() {
                      try {
                      } catch(Throwable t) {
                          t.printStackTrace();
                      }
                  }
              }
              """,
            """
              import org.apache.logging.log4j.Logger;
              class Test {
                  Logger logger;
                  
                  void test() {
                      try {
                      } catch(Throwable t) {
                          logger.error("Exception", t);
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void useJul() {
        rewriteRun(
          spec -> spec.recipe(new PrintStackTraceToLogError(null, "LOGGER", "jul")),
          //language=java
          java(
            """
              import java.util.logging.Logger;
              class Test {
                  Logger logger;
                  
                  void test() {
                      try {
                      } catch(Throwable t) {
                          t.printStackTrace();
                      }
                  }
              }
              """,
            """
              import java.util.logging.Level;
              import java.util.logging.Logger;
                            
              class Test {
                  Logger logger;
                  
                  void test() {
                      try {
                      } catch(Throwable t) {
                          logger.log(Level.SEVERE, "Exception", t);
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
          spec -> spec.recipe(new PrintStackTraceToLogError(true, "LOGGER", null))
            .parser(JavaParser.fromJavaVersion().classpath("slf4j-api")),
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
              """,
            """
              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;
                            
              class Test {
                  private static final Logger LOGGER = LoggerFactory.getLogger(Test.class);
                            
                  void test() {
                      try {
                      } catch(Throwable t) {
                          LOGGER.error("Exception", t);
                      }
                  }
              }
              """
          )
        );
    }

    @SuppressWarnings("RedundantSlf4jDefinition")
    @Issue("https://github.com/openrewrite/rewrite-logging-frameworks/issues/64")
    @Test
    void addLoggerTwoStaticClass() {
        rewriteRun(
          spec -> spec.recipe(new PrintStackTraceToLogError(true, "LOGGER", null))
            .parser(JavaParser.fromJavaVersion().classpath("slf4j-api")),
          //language=java
          java(
            """
              public class Test {
                  public static class MyErrorReceiver {
                      public void error(Exception e) {
                          e.printStackTrace();
                      }
                  }

                  public static class Another {
                      public void close() {
                          try {
                          } catch ( java.io.IOException e ) {
                              e.printStackTrace();
                          }
                      }
                  }
              }
              """,
            """
              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;
              
              public class Test {
                  public static class MyErrorReceiver {
                      private static final Logger LOGGER = LoggerFactory.getLogger(MyErrorReceiver.class);

                      public void error(Exception e) {
                          LOGGER.error("Exception", e);
                      }
                  }

                  public static class Another {
                      private static final Logger LOGGER = LoggerFactory.getLogger(Another.class);

                      public void close() {
                          try {
                          } catch ( java.io.IOException e ) {
                              LOGGER.error("Exception", e);
                          }
                      }
                  }
              }
              """
          )
        );
    }
}
