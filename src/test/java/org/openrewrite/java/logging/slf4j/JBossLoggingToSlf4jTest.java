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
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.config.Environment;
import org.openrewrite.maven.MavenExecutionContextView;
import org.openrewrite.maven.MavenParser;
import org.openrewrite.maven.cache.InMemoryMavenPomCache;
import org.openrewrite.maven.tree.Pom;
import org.openrewrite.maven.tree.ResolvedGroupArtifactVersion;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.time.Duration;

import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.java.Assertions.*;
import static org.openrewrite.maven.Assertions.pomXml;

public class JBossLoggingToSlf4jTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(Environment.builder()
          .scanRuntimeClasspath("org.openrewrite.java.migrate.javax")
          .build()
          .activateRecipes("org.openrewrite.java.logging.slf4j.JBossLoggingToSlf4j")
        );
    }

    @Nested
    class DependenciesTest implements RewriteTest {
        @Override
        public void defaults(RecipeSpec spec) {
            spec.recipe(Environment.builder()
              .scanRuntimeClasspath("org.openrewrite.java.migrate.javax")
              .build()
              .activateRecipes("org.openrewrite.java.logging.slf4j.JBossLoggingToSlf4jUpdateDependencies")
            );
        }

        @Test
        void stillUsingJBossLoggingKeepsTheDependencyUsingMaven() {
            ExecutionContext ctx = new InMemoryExecutionContext();
            InMemoryMavenPomCache cache = new InMemoryMavenPomCache();
            cache.putPom(new ResolvedGroupArtifactVersion("https://repo.maven.apache.org/maven2", "org.jboss.slf4j", "slf4j-jboss-logmanager", "8.999.1", null), Pom.builder().build());
            ctx.putMessage("org.openrewrite.maven.pomCache", cache);
            ExecutionContext ctx2 = new InMemoryExecutionContext();
            ctx2.putMessage("org.openrewrite.maven.pomCache", cache);
            rewriteRun(spec -> spec.executionContext(ctx).recipeExecutionContext(ctx2),
              mavenProject(
                "project",
                srcMainJava(
                  //language=java
                  java(
                    """
                      import org.jboss.logging.Logger;

                      public class A {
                          private static final Logger LOGGER = Logger.getLogger(A.class);
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
                                <groupId>org.jboss.logging</groupId>
                                <artifactId>jboss-logging</artifactId>
                                <version>3.6.1.Final</version>
                            </dependency>
                            <dependency>
                                <groupId>org.jboss.logmanager</groupId>
                                <artifactId>jboss-logmanager</artifactId>
                                <version>3.1.2.Final</version>
                            </dependency>
                            <dependency>
                                <groupId>org.jboss.slf4j</groupId>
                                <artifactId>slf4j-jboss-logmanager</artifactId>
                                <version>2.888.1</version>
                            </dependency>
                        </dependencies>
                    </project>
                    """
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

                      public class A {
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
                      public class A {
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
