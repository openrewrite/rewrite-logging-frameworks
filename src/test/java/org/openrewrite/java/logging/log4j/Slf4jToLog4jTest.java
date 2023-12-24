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
package org.openrewrite.java.logging.log4j;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.maven.Assertions.pomXml;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

class Slf4jToLog4jTest implements RewriteTest {

    private static final Pattern VERSION_PATTERN = Pattern.compile("<version>([\\d.]+)</version>");

    @Override
    public void defaults(RecipeSpec spec) {
        // The Gradle test runner has SLF4J 1.7.x on the classpath,
        // we need to explicitly specify we want SLF4J 2.x.
        spec.recipeFromResource("/META-INF/rewrite/log4j.yml", "org.openrewrite.java.logging.log4j.Slf4jToLog4j")
          .parser(JavaParser.fromJavaVersion().classpath("log4j-api", "slf4j-api-2[\\d.]+", "lombok"));
    }

    @DocumentExample
    @Test
    void loggerUsage() {
        //language=java
        rewriteRun(java("""
          import org.slf4j.Logger;
          import org.slf4j.LoggerFactory;
          import org.slf4j.Marker;
          import org.slf4j.MarkerFactory;

          class Test {
              private static final Logger LOGGER = LoggerFactory.getLogger(Test.class);
              private static final Marker MARKER = MarkerFactory.getMarker("MARKER");

              public static void main(String[] args) {
                  if (LOGGER.isDebugEnabled()) {
                      LOGGER.debug("logger message");
                  }
                  LOGGER.warn(MARKER, "Hello {}!", "world");
              }
          }
          """, """
          import org.apache.logging.log4j.LogManager;
          import org.apache.logging.log4j.Logger;
          import org.apache.logging.log4j.Marker;
          import org.apache.logging.log4j.MarkerManager;

          class Test {
              private static final Logger LOGGER = LogManager.getLogger(Test.class);
              private static final Marker MARKER = MarkerManager.getMarker("MARKER");

              public static void main(String[] args) {
                  if (LOGGER.isDebugEnabled()) {
                      LOGGER.debug("logger message");
                  }
                  LOGGER.warn(MARKER, "Hello {}!", "world");
              }
          }
          """));
    }

    @Test
    void mdcConversion() {
        //language=java
        rewriteRun(java("""
          import org.slf4j.MDC;

          class Test {
              void method() {
                 MDC.put("key", "value");
                 try (MDC.MDCCloseable c = MDC.putCloseable("key2", "value2")) {
                     MDC.get("key2");
                 }
                 MDC.remove("key");
                 MDC.clear();
              }
          }
          """, """
          import org.apache.logging.log4j.CloseableThreadContext;
          import org.apache.logging.log4j.ThreadContext;

          class Test {
              void method() {
                 ThreadContext.put("key", "value");
                 try (CloseableThreadContext.Instance c = CloseableThreadContext.put("key2", "value2")) {
                     ThreadContext.get("key2");
                 }
                 ThreadContext.remove("key");
                 ThreadContext.clearAll();
              }
          }
          """));
    }

    @Test
    void logBuilder() {
        //language=java
        rewriteRun(java("""
          import org.slf4j.Logger;
          import org.slf4j.Marker;
          import org.slf4j.event.Level;

          class Test {
              void method(Logger logger, Marker marker, Throwable t) {
                 logger.atLevel(Level.INFO)
                     .addMarker(marker)
                     .setCause(t)
                     .log("Hello {}!", "world");
              }
          }
          """, """
          import org.apache.logging.log4j.Level;
          import org.apache.logging.log4j.Logger;
          import org.apache.logging.log4j.Marker;

          class Test {
              void method(Logger logger, Marker marker, Throwable t) {
                 logger.atLevel(Level.INFO)
                     .withMarker(marker)
                     .withThrowable(t)
                     .log("Hello {}!", "world");
              }
          }
          """));
    }

    @Test
    void mavenPom() {
        //language=xml
        rewriteRun(mavenProject("project", pomXml("""
          <project>
              <groupId>org.example</groupId>
              <artifactId>example-lib</artifactId>
              <version>1</version>
              <dependencies>
                  <dependency>
                      <groupId>org.slf4j</groupId>
                      <artifactId>slf4j-api</artifactId>
                      <version>2.0.9</version>
                  </dependency>
              </dependencies>
          </project>
          """, spec -> spec.after(actual -> {
            Matcher matcher = VERSION_PATTERN.matcher(actual);
            List<String> versions = new ArrayList<>();
            while (matcher.find()) {
                versions.add(matcher.group(1));
            }
            return String.format("""
              <project>
                  <groupId>org.example</groupId>
                  <artifactId>example-lib</artifactId>
                  <version>%s</version>
                  <dependencies>
                      <dependency>
                          <groupId>org.apache.logging.log4j</groupId>
                          <artifactId>log4j-api</artifactId>
                          <version>%s</version>
                      </dependency>
                  </dependencies>
              </project>
              """, versions.size() > 0 ? versions.get(0) : "", versions.size() > 1 ? versions.get(1) : "");
        }))));
    }

    @Test
    void changeLombokLogAnnotation() {
        //language=java
        rewriteRun(spec -> spec.typeValidationOptions(TypeValidation.builder()
          .identifiers(false)
          .methodInvocations(false)
          .build()), java("""
            import lombok.extern.slf4j.Slf4j;

            @Slf4j
            class Test {
                void method() {
                    log.info("uh oh");
                }
            }
          """, """
            import lombok.extern.log4j.Log4j2;

            @Log4j2
            class Test {
                void method() {
                    log.info("uh oh");
                }
            }
          """));
    }
}
