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
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.java.Assertions.mavenProject;
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

    @Test
    void mavenPom() {
        rewriteRun(
          mavenProject(
            "project",
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
                            <version>latest</version>
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
    void gradle() {
        rewriteRun(recipeSpec -> recipeSpec.beforeRecipe(withToolingApi()),
          //language=gradle
          buildGradle(
            """
              plugins { id "java" }
              repositories { mavenCentral() }
              dependencies {
                  implementation("org.jboss.logmanager:jboss-logmanager:latest.release")
                  implementation("org.jboss.logging:jboss-logging:latest.release")
              }
              """,
            """
              plugins { id "java" }
              repositories { mavenCentral() }
              dependencies {
                  implementation("org.jboss.logmanager:jboss-logmanager:latest.release")
                  implementation("org.jboss.slf4j:slf4j-jboss-logmanager:latest.release")
              }
              """
          )
        );
    }
}
