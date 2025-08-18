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
package org.openrewrite.java.logging.log4j;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.*;
import static org.openrewrite.maven.Assertions.pomXml;

class Log4j1ToLog4j2Test implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResource("/META-INF/rewrite/log4j.yml", "org.openrewrite.java.logging.log4j.Log4j1ToLog4j2")
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "log4j-1+"));
    }

    @DocumentExample
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
              import org.apache.logging.log4j.Logger;
              import org.apache.logging.log4j.LogManager;

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
              class Test {
                  Logger logger0 = Logger.getRootLogger();
              }
              """,
            """
              import org.apache.logging.log4j.Logger;
              import org.apache.logging.log4j.LogManager;

              class Test {
                  Logger logger0 = LogManager.getRootLogger();
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

    @Test
    void priorityInfo() {
        //language=java
        rewriteRun(
          java(
            """
              import org.apache.log4j.Logger;
              import org.apache.log4j.Priority;

              class Test {
                  void method(Logger logger) {
                      logger.log(Priority.INFO, "Hello world");
                  }
              }
              """,
            """
              import org.apache.logging.log4j.Logger;
              import org.apache.logging.log4j.Level;

              class Test {
                  void method(Logger logger) {
                      logger.log(Level.INFO, "Hello world");
                  }
              }
              """
          )
        );
    }

    @Test
    void loggerSetLevel() {
        //language=java
        rewriteRun(
          java(
            """
              import org.apache.log4j.Level;
              import org.apache.log4j.Logger;

              class Test {
                  void method(Logger logger) {
                      logger.setLevel(Level.INFO);
                  }
              }
              """,
            """
              import org.apache.logging.log4j.Level;
              import org.apache.logging.log4j.Logger;
              import org.apache.logging.log4j.core.config.Configurator;

              class Test {
                  void method(Logger logger) {
                      Configurator.setLevel(logger, Level.INFO);
                  }
              }
              """
          )
        );
    }

    @Test
    void isGreaterOrEqual() {
        //language=java
        rewriteRun(
          java(
            """
              import org.apache.log4j.Level;

              class Test {
                  boolean is = Level.ERROR.isGreaterOrEqual(Level.INFO);
              }
              """,
            """
              import org.apache.logging.log4j.Level;

              class Test {
                  boolean is = Level.ERROR.isMoreSpecificThan(Level.INFO);
              }
              """
          )
        );
    }

    @Test
    void categoryGetInstance() {
        //language=java
        rewriteRun(
          java(
            """
              import org.apache.log4j.Category;
              class Test {
                  Category category = Category.getInstance(Test.class);
              }
              """,
            """
              import org.apache.logging.log4j.LogManager;
              import org.apache.logging.log4j.Logger;

              class Test {
                  Logger category = LogManager.getLogger(Test.class);
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-logging-frameworks/issues/147")
    @Test
    void matchUnknownTypes() {
        //language=java
        rewriteRun(
          java(
            """
              import org.apache.log4j.Logger;

              class Main {
                  private static final Logger LOGGER = Logger.getLogger(Main.class);

                  public static void main(String[] args) {
                      LOGGER.info("Hello world");
                  }
              }
              """,
            """
              import org.apache.logging.log4j.Logger;
              import org.apache.logging.log4j.LogManager;

              class Main {
                  private static final Logger LOGGER = LogManager.getLogger(Main.class);

                  public static void main(String[] args) {
                      LOGGER.info("Hello world");
                  }
              }
              """
          )
        );
    }

    @Test
    void mavenProjectDependenciesUpdated() {
        rewriteRun(
          mavenProject("project",
            srcMainJava(
              //language=java
              java(
                """
                  import org.apache.log4j.Logger;

                  class Test {
                      Logger logger = Logger.getLogger(Test.class);
                  }
                  """,
                """
                  import org.apache.logging.log4j.Logger;
                  import org.apache.logging.log4j.LogManager;

                  class Test {
                      Logger logger = LogManager.getLogger(Test.class);
                  }
                  """
              ),
              //language=xml
              pomXml(
                """
                  <project>
                      <groupId>com.mycompany.app</groupId>
                      <artifactId>my-app</artifactId>
                      <version>1</version>
                      <dependencies>
                          <dependency>
                              <groupId>org.apache.httpcomponents</groupId>
                              <artifactId>httpclient</artifactId>
                              <version>4.5.13</version>
                          </dependency>
                          <dependency>
                              <groupId>log4j</groupId>
                              <artifactId>log4j</artifactId>
                              <version>1.2.17</version>
                          </dependency>
                          <dependency>
                              <groupId>org.slf4j</groupId>
                              <artifactId>slf4j-log4j12</artifactId>
                              <version>1.7.36</version>
                          </dependency>
                      </dependencies>
                  </project>
                  """,
                sourceSpecs -> sourceSpecs.after(after -> {
                    Matcher matcher = Pattern.compile("<version>(2\\.2\\d\\.\\d)</version>").matcher(after);
                    assertTrue(matcher.find());
                    String version = matcher.group(1);
                    return """
                      <project>
                          <groupId>com.mycompany.app</groupId>
                          <artifactId>my-app</artifactId>
                          <version>1</version>
                          <dependencies>
                              <dependency>
                                  <groupId>org.apache.httpcomponents</groupId>
                                  <artifactId>httpclient</artifactId>
                                  <version>4.5.13</version>
                              </dependency>
                              <dependency>
                                  <groupId>org.apache.logging.log4j</groupId>
                                  <artifactId>log4j-api</artifactId>
                                  <version>%1$s</version>
                              </dependency>
                              <dependency>
                                  <groupId>org.apache.logging.log4j</groupId>
                                  <artifactId>log4j-core</artifactId>
                                  <version>%1$s</version>
                              </dependency>
                              <dependency>
                                  <groupId>org.apache.logging.log4j</groupId>
                                  <artifactId>log4j-slf4j-impl</artifactId>
                                  <version>%1$s</version>
                              </dependency>
                          </dependencies>
                      </project>
                      """.formatted(version);
                })
              )
            )
          )
        );
    }
}
