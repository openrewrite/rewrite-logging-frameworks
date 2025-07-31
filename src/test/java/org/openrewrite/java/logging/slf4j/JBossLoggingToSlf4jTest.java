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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.java.Assertions.*;
import static org.openrewrite.maven.Assertions.pomXml;

class JBossLoggingToSlf4jTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.logging.slf4j.JBossLoggingToSlf4j");
    }

    @DocumentExample
    @Test
    void simpleLogInvocationMapping() {
        rewriteRun(
          //language=java
          java(
            """
              import org.jboss.logging.Logger;

              class A {
                  void doLog(Logger logger, String msg, Throwable t) {
                      logger.trace(msg);
                      logger.trace(msg, t);
                      logger.debug(msg);
                      logger.debug(msg, t);
                      logger.info(msg);
                      logger.info(msg, t);
                      logger.warn(msg);
                      logger.warn(msg, t);
                      logger.error(msg);
                      logger.error(msg, t);
                      logger.fatal(msg);
                      logger.fatal(msg, t);
                  }
              }
              """,
            """
              import org.slf4j.Logger;

              class A {
                  void doLog(Logger logger, String msg, Throwable t) {
                      logger.trace(msg);
                      logger.trace(msg, t);
                      logger.debug(msg);
                      logger.debug(msg, t);
                      logger.info(msg);
                      logger.info(msg, t);
                      logger.warn(msg);
                      logger.warn(msg, t);
                      logger.error(msg);
                      logger.error(msg, t);
                      logger.error(msg);
                      logger.error(msg, t);
                  }
              }
              """
          )
        );
    }

    @Nested
    class DependenciesTest implements RewriteTest {
        @Override
        public void defaults(RecipeSpec spec) {
            spec.recipeFromResources("org.openrewrite.java.logging.slf4j.JBossLoggingToSlf4jUpdateDependencies");
        }

        @Test
        void stillUsingJBossLoggingKeepsTheDependencyUsingMaven() {
            rewriteRun(
              mavenProject(
                "project",
                srcMainJava(
                  //language=java
                  java(
                    """
                      import org.jboss.logging.Logger;

                      class A {
                          Logger LOGGER = Logger.getLogger(A.class);
                      }
                      """
                  )
                ),
                //language=xml
                pomXml(
                  """
                    <project>
                        <groupId>org.example</groupId>
                        <artifactId>example-lib</artifactId>
                        <version>1</version>
                        <dependencies>
                            <dependency>
                                <groupId>org.jboss.logging</groupId>
                                <artifactId>jboss-logging</artifactId>
                                <version>3.6.1.Final</version>
                            </dependency>
                            <dependency>
                                <groupId>org.jboss.logmanager</groupId>
                                <artifactId>jboss-logmanager</artifactId>
                                <version>3.1.2.Final</version>
                            </dependency>
                        </dependencies>
                    </project>
                    """,
                  sourceSpecs -> sourceSpecs.after(after -> assertThat(after)
                    .contains("<artifactId>slf4j-jboss-logmanager</artifactId>")
                    .containsPattern("<version>(2.*)</version>")
                    .actual())
                )
              )
            );
        }

        @Test
        void noMoreJBossLoggingUsagesRemovesTheDependencyUsingMaven() {
            rewriteRun(
              mavenProject(
                "project",
                srcMainJava(
                  //language=java
                  java(
                    """
                      public class A {
                      }
                      """
                  )
                ),
                //language=xml
                pomXml(
                  """
                    <project>
                        <groupId>org.example</groupId>
                        <artifactId>example-lib</artifactId>
                        <version>1</version>
                        <dependencies>
                            <dependency>
                                <groupId>org.jboss.logging</groupId>
                                <artifactId>jboss-logging</artifactId>
                                <version>3.6.1.Final</version>
                            </dependency>
                            <dependency>
                                <groupId>org.jboss.logmanager</groupId>
                                <artifactId>jboss-logmanager</artifactId>
                                <version>3.1.2.Final</version>
                            </dependency>
                        </dependencies>
                    </project>
                    """,
                  """
                    <project>
                        <groupId>org.example</groupId>
                        <artifactId>example-lib</artifactId>
                        <version>1</version>
                        <dependencies>
                            <dependency>
                                <groupId>org.jboss.logmanager</groupId>
                                <artifactId>jboss-logmanager</artifactId>
                                <version>3.1.2.Final</version>
                            </dependency>
                            <dependency>
                                <groupId>org.jboss.slf4j</groupId>
                                <artifactId>slf4j-jboss-logmanager</artifactId>
                                <version>2.0.1.Final</version>
                            </dependency>
                        </dependencies>
                    </project>
                    """
                )
              )
            );
        }

        @Test
        void stillUsingJBossLoggingKeepsTheDependencyUsingGradle() {
            rewriteRun(
              spec -> spec.beforeRecipe(withToolingApi()),
              mavenProject(
                "project",
                srcMainJava(
                  //language=java
                  java(
                    """
                      import org.jboss.logging.Logger;

                      class A {
                          private static final Logger LOGGER = Logger.getLogger(A.class);
                      }
                      """
                  )
                ),
                //language=gradle
                buildGradle(
                  """
                    plugins { id "java" }
                    repositories { mavenCentral() }
                    dependencies {
                        implementation "org.jboss.logmanager:jboss-logmanager:3.1.2.Final"
                        implementation "org.jboss.logging:jboss-logging:3.6.1.Final"
                    }
                    """,
                  """
                    plugins { id "java" }
                    repositories { mavenCentral() }
                    dependencies {
                        implementation "org.jboss.logmanager:jboss-logmanager:3.1.2.Final"
                        implementation "org.jboss.slf4j:slf4j-jboss-logmanager:2.0.1.Final"
                        implementation "org.jboss.logging:jboss-logging:3.6.1.Final"
                    }
                    """
                )
              )
            );
        }

        @Test
        void noMoreJBossLoggingUsagesRemovesTheDependencyUsingGradle() {
            rewriteRun(
              spec -> spec.beforeRecipe(withToolingApi()),
              mavenProject(
                "project",
                srcMainJava(
                  //language=java
                  java(
                    """
                      class A {
                      }
                      """
                  )
                ),
                //language=gradle
                buildGradle(
                  """
                    plugins { id "java" }
                    repositories { mavenCentral() }
                    dependencies {
                        implementation "org.jboss.logmanager:jboss-logmanager:3.1.2.Final"
                        implementation "org.jboss.logging:jboss-logging:3.6.1.Final"
                    }
                    """,
                  """
                    plugins { id "java" }
                    repositories { mavenCentral() }
                    dependencies {
                        implementation "org.jboss.logmanager:jboss-logmanager:3.1.2.Final"
                        implementation "org.jboss.slf4j:slf4j-jboss-logmanager:2.0.1.Final"
                    }
                    """
                )
              )
            );
        }
    }
}
