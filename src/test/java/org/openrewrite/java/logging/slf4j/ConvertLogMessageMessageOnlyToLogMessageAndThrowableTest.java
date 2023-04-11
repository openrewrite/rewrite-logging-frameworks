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
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings("RedundantSlf4jDefinition")
class ConvertLogMessageMessageOnlyToLogMessageAndThrowableTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ConvertLogMessageMessageOnlyToLogMessageAndThrowable(null))
          .parser(JavaParser.fromJavaVersion().classpath("slf4j-api"));
    }

    @Test
    void paramIsNotThrowable() {
        //language=java
        rewriteRun(
          spec -> spec.recipe(new ConvertLogMessageMessageOnlyToLogMessageAndThrowable("test-message")),
          java(
            """
              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;

              class Test {
                  Logger logger = LoggerFactory.getLogger(Test.class);
                  void doSomething() {
                      try {
                          Integer num = Integer.valueOf("a");
                      } catch (NumberFormatException e) {
                          logger.error("Invalid");
                          logger.warn(Integer.valueOf(1).toString());
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void convertWithoutMessage() {
        //language=java
        rewriteRun(
          spec -> spec.recipe(new ConvertLogMessageMessageOnlyToLogMessageAndThrowable("test-message")),
          java(
            """
              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;

              class Test {
                  Logger logger = LoggerFactory.getLogger(Test.class);
                  void doSomething() {
                      try {
                          Integer num = Integer.valueOf("a");
                      } catch (NumberFormatException e) {
                          logger.error(e.getMessage());
                          logger.warn(e.getLocalizedMessage());
                      }
                  }
              }
              """,
            """
              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;

              class Test {
                  Logger logger = LoggerFactory.getLogger(Test.class);
                  void doSomething() {
                      try {
                          Integer num = Integer.valueOf("a");
                      } catch (NumberFormatException e) {
                          logger.error("test-message", e);
                          logger.warn("test-message", e);
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void convertWithMessage() {
        //language=java
        rewriteRun(
          java(
            """
              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;

              class Test {
                  Logger logger = LoggerFactory.getLogger(Test.class);
                  void doSomething() {
                      try {
                          Integer num = Integer.valueOf("a");
                      } catch (NumberFormatException e) {
                          logger.error(e.getMessage());
                          logger.warn(e.getLocalizedMessage());
                      }
                  }
              }
              """,
            """
              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;

              class Test {
                  Logger logger = LoggerFactory.getLogger(Test.class);
                  void doSomething() {
                      try {
                          Integer num = Integer.valueOf("a");
                      } catch (NumberFormatException e) {
                          logger.error("", e);
                          logger.warn("", e);
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void templateError() {
        //language=java
        rewriteRun(
          java(
            """
              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;

              import java.util.Map;

              public class A {
                  private static final Logger logger = LoggerFactory.getLogger(A.class);

                  public String create(
                      String app,
                      int id,
                      String instanceID,
                      String hostname,
                      String ip,
                      String rac,
                      Map<String, Object> volumes,
                      String token) {
                      try {
                          PriamInstance ins =
                              makePriamInstance(app, id, instanceID, hostname, ip, rac, volumes, token);
                          // remove old data node which are dead.
                          if (app.endsWith("-dead")) {
                              try {
                                  PriamInstance oldData = getInstance(app, id);
                                  // clean up a very old data...
                                  if (null != oldData
                                      && oldData.getUpdatetime()
                                         < (System.currentTimeMillis() - (3 * 60 * 1000)))
                                      this.deregisterInstance(oldData);
                              } catch (Exception ex) {
                                  // Do nothing
                                  logger.error(ex.getMessage(), ex);
                              }
                          }
                          this.deregisterInstance(ins);
                          return ins.toString();
                      } catch (Exception e) {
                          logger.error(e.getMessage());
                          throw new RuntimeException(e);
                      }
                  }

                  PriamInstance makePriamInstance( String app,
                                                   int id,
                                                   String instanceID,
                                                   String hostname,
                                                   String ip,
                                                   String rac,
                                                   Map<String, Object> volumes,
                                                   String token) {
                      return new PriamInstance();
                  }

                  PriamInstance getInstance(String app, int id) {
                      return new PriamInstance();
                  }

                  void deregisterInstance(PriamInstance p) {

                  }

                  class PriamInstance {
                      long getUpdatetime() {
                          return System.currentTimeMillis();
                      }
                  }
              }
              """
          )
        );
    }
}
