/*
 * Copyright 2026 the original author or authors.
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.kotlin.KotlinParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.*;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.kotlin.Assertions.kotlin;
import static org.openrewrite.maven.Assertions.pomXml;

class Slf4jFluentToSlf4jTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new Slf4jFluentToSlf4j())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "slf4j-fluent", "slf4j-api-2"))
          .afterParser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "slf4j-fluent", "slf4j-api-2"));
    }

    @DocumentExample
    @Test
    void logLevelsAndFactory() {
        rewriteRun(
          java(
            """
              import org.fissore.slf4j.FluentLogger;
              import org.fissore.slf4j.FluentLoggerFactory;

              class Example {
                  private final FluentLogger log = FluentLoggerFactory.getLogger(Example.class);

                  void log() {
                      log.trace().log("trace");
                      log.debug().log("debug");
                      log.info().log("info");
                      log.warn().log("warn");
                      log.error().log("error");
                  }
              }
              """,
            """
              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;

              class Example {
                  private final Logger log = LoggerFactory.getLogger(Example.class);

                  void log() {
                      log.atTrace().log("trace");
                      log.atDebug().log("debug");
                      log.atInfo().log("info");
                      log.atWarn().log("warn");
                      log.atError().log("error");
                  }
              }
              """
          )
        );
    }

    @Test
    void lazyMessagesArgumentsAndCauses() {
        rewriteRun(
          java(
            """
              import org.fissore.slf4j.FluentLogger;
              import org.fissore.slf4j.FluentLoggerFactory;
              import org.fissore.slf4j.Util;
              import java.util.function.Supplier;
              import static org.fissore.slf4j.Util.lazy;

              class Example {
                  FluentLogger log = FluentLoggerFactory.getLogger("example");

                  void log(Throwable cause, Supplier<String> message, Supplier<?> value) {
                      log.debug().log("Value {} {}", 1, lazy(() -> "expensive"));
                      log.info().log("{}", Util.lazy(() -> 1));
                      log.info().log("{}", org.fissore.slf4j.Util.lazy(() -> 2));
                      log.info().log("Value {}", value);
                      log.warn().log(message);
                      log.error().withCause(cause).log(() -> "Value {}", lazy(this::value));
                      log.error().withCause(cause).log("failure");
                  }

                  String value() { return "value"; }
              }
              """,
            """
              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;

              import java.util.function.Supplier;

              class Example {
                  Logger log = LoggerFactory.getLogger("example");

                  void log(Throwable cause, Supplier<String> message, Supplier<?> value) {
                      log.atDebug().setMessage("Value {} {}").addArgument(1).addArgument(() -> "expensive").log();
                      log.atInfo().setMessage("{}").addArgument(() -> 1).log();
                      log.atInfo().setMessage("{}").addArgument(() -> 2).log();
                      log.atInfo().setMessage("Value {}").addArgument(value).log();
                      log.atWarn().log(message);
                      log.atError().setCause(cause).setMessage(() -> "Value {}").addArgument(this::value).log();
                      log.atError().setCause(cause).log("failure");
                  }

                  String value() { return "value"; }
              }
              """
          )
        );
    }

    @Test
    void storedBuildersAndWrappedLoggers() {
        rewriteRun(
          java(
            """
              import org.fissore.slf4j.FluentLogger;
              import org.fissore.slf4j.LoggerAtLevel;
              import org.slf4j.Logger;

              class Example {
                  FluentLogger wrap(Logger logger) {
                      return new FluentLogger(logger);
                  }

                  void log(FluentLogger logger, Throwable cause) {
                      LoggerAtLevel builder = logger.error();
                      builder.withCause(cause);
                      builder.log("failure");
                  }
              }
              """,
            """
              import org.slf4j.Logger;
              import org.slf4j.spi.LoggingEventBuilder;

              class Example {
                  Logger wrap(Logger logger) {
                      return logger;
                  }

                  void log(Logger logger, Throwable cause) {
                      LoggingEventBuilder builder = logger.atError();
                      builder.setCause(cause);
                      builder.log("failure");
                  }
              }
              """
          )
        );
    }

    @Test
    void staticFactoryAndInlineMarker() {
        rewriteRun(
          java(
            """
              import org.fissore.slf4j.FluentLogger;
              import org.slf4j.Marker;
              import static org.fissore.slf4j.FluentLoggerFactory.getLogger;

              class Example {
                  FluentLogger log = getLogger(Example.class);

                  void log(Marker marker, Throwable cause) {
                      log.info().withMarker(marker).log("message");
                      log.error().withCause(cause).withMarker(marker).log("failure");
                  }
              }
              """,
            """
              import org.slf4j.Logger;
              import org.slf4j.Marker;
              import static org.slf4j.LoggerFactory.getLogger;

              class Example {
                  Logger log = getLogger(Example.class);

                  void log(Marker marker, Throwable cause) {
                      log.atInfo().addMarker(marker).log("message");
                      log.atError().setCause(cause).addMarker(marker).log("failure");
                  }
              }
              """
          )
        );
    }

    @Test
    void unrelatedSlf4jCalls() {
        rewriteRun(
          java(
            """
              import org.slf4j.Logger;

              class Example {
                  void log(Logger logger) {
                      logger.info("message");
                      logger.atDebug().log("debug");
                  }
              }
              """
          )
        );
    }

    @Test
    void rateLimitingWithholdsMigrationAcrossFilesAndBuild() {
        rewriteRun(
          java(
            """
              import org.fissore.slf4j.FluentLogger;
              import java.time.temporal.ChronoUnit;

              class Limited {
                  void log(FluentLogger logger) {
                      logger.info().every(5).log("count");
                      logger.warn().every(1, ChronoUnit.SECONDS).log("time");
                  }
              }
              """,
            """
              import org.fissore.slf4j.FluentLogger;
              import java.time.temporal.ChronoUnit;

              class Limited {
                  void log(FluentLogger logger) {
                      /*~~(This slf4j-fluent usage requires manual migration before rerunning the recipe)~~>*/logger.info().every(5).log("count");
                      /*~~(This slf4j-fluent usage requires manual migration before rerunning the recipe)~~>*/logger.warn().every(1, ChronoUnit.SECONDS).log("time");
                  }
              }
              """
          ),
          java(
            """
              import org.fissore.slf4j.FluentLogger;
              class Example {
                  void log(FluentLogger logger) {
                      logger.info().log("unchanged");
                  }
              }
              """
          ),
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId>
                  <artifactId>example</artifactId>
                  <version>1.0</version>
                  <dependencies>
                      <dependency>
                          <groupId>org.fissore</groupId>
                          <artifactId>slf4j-fluent</artifactId>
                          <version>0.14.0</version>
                      </dependency>
                  </dependencies>
              </project>
              """
          )
        );
    }

    @Test
    void mavenDependency() {
        rewriteRun(
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId>
                  <artifactId>example</artifactId>
                  <version>1.0</version>
                  <dependencies>
                      <dependency>
                          <groupId>org.fissore</groupId>
                          <artifactId>slf4j-fluent</artifactId>
                          <version>0.14.0</version>
                      </dependency>
                  </dependencies>
              </project>
              """,
            spec -> spec.after(after -> assertThat(after)
              .contains("<groupId>org.slf4j</groupId>")
              .contains("<artifactId>slf4j-api</artifactId>")
              .containsPattern("<version>2\\.0\\.\\d+</version>")
              .doesNotContain("org.fissore", "slf4j-fluent")
              .actual())
          )
        );
    }

    @Test
    void upgradesExistingSlf4jDependencyWithoutDuplicates() {
        rewriteRun(
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId>
                  <artifactId>example</artifactId>
                  <version>1.0</version>
                  <dependencies>
                      <dependency>
                          <groupId>org.fissore</groupId>
                          <artifactId>slf4j-fluent</artifactId>
                          <version>0.14.0</version>
                      </dependency>
                      <dependency>
                          <groupId>org.slf4j</groupId>
                          <artifactId>slf4j-api</artifactId>
                          <version>1.7.36</version>
                      </dependency>
                  </dependencies>
              </project>
              """,
            spec -> spec.after(after -> assertThat(after)
              .containsOnlyOnce("<artifactId>slf4j-api</artifactId>")
              .containsPattern("<version>2\\.0\\.\\d+</version>")
              .doesNotContain("slf4j-fluent", "1.7.36")
              .actual())
          )
        );
    }

    @Test
    void gradleDependency() {
        rewriteRun(
          spec -> spec.beforeRecipe(withToolingApi()),
          buildGradle(
            """
              plugins { id 'java' }
              repositories { mavenCentral() }
              dependencies {
                  implementation 'org.fissore:slf4j-fluent:0.14.0'
              }
              """,
            spec -> spec.after(after -> assertThat(after)
              .containsPattern("org.slf4j:slf4j-api:2\\.0\\.\\d+")
              .doesNotContain("slf4j-fluent")
              .actual())
          )
        );
    }

    @Test
    void factoryMethodReference() {
        rewriteRun(
          java(
            """
              import org.fissore.slf4j.FluentLogger;
              import org.fissore.slf4j.FluentLoggerFactory;
              import java.util.function.Function;

              class Example {
                  Function<String, FluentLogger> factory = FluentLoggerFactory::getLogger;
              }
              """,
            """
              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;

              import java.util.function.Function;

              class Example {
                  Function<String, Logger> factory = LoggerFactory::getLogger;
              }
              """
          )
        );
    }

    @Test
    void wrappedConditionalLogger() {
        rewriteRun(
          java(
            """
              import org.fissore.slf4j.FluentLogger;
              import org.slf4j.Logger;

              class Example {
                  void log(boolean enabled, Logger first, Logger second) {
                      new FluentLogger(enabled ? first : second).info().log("message");
                  }
              }
              """,
            """
              import org.slf4j.Logger;

              class Example {
                  void log(boolean enabled, Logger first, Logger second) {
                      (enabled ? first : second).atInfo().log("message");
                  }
              }
              """
          )
        );
    }

    @Test
    void wrappedConditionalLoggerInConcatenation() {
        rewriteRun(
          java(
            """
              import org.fissore.slf4j.FluentLogger;
              import org.slf4j.Logger;

              class Example {
                  String describe(boolean enabled, Logger first, Logger second) {
                      return "logger: " + new FluentLogger(enabled ? first : second);
                  }
              }
              """,
            """
              import org.slf4j.Logger;

              class Example {
                  String describe(boolean enabled, Logger first, Logger second) {
                      return "logger: " + (enabled ? first : second);
                  }
              }
              """
          )
        );
    }

    @Test
    void eagerCallBeforeLazyArgument() {
        rewriteRun(
          java(
            """
              import org.fissore.slf4j.FluentLogger;
              import static org.fissore.slf4j.Util.lazy;

              class Example {
                  void log(FluentLogger logger) {
                      logger.info().log("{} {}", value(), lazy(() -> 1));
                  }

                  String value() { return "value"; }
              }
              """,
            """
              import org.slf4j.Logger;

              class Example {
                  void log(Logger logger) {
                      logger.atInfo().setMessage("{} {}").addArgument(value()).addArgument(() -> 1).log();
                  }

                  String value() { return "value"; }
              }
              """
          )
        );
    }

    @Test
    void parenthesizedNullArgument() {
        rewriteRun(
          java(
            """
              import org.fissore.slf4j.FluentLogger;
              class Example {
                  void log(FluentLogger logger) {
                      logger.info().log("{} {}", (null), 1);
                  }
              }
              """,
            """
              import org.slf4j.Logger;

              class Example {
                  void log(Logger logger) {
                      logger.atInfo().setMessage("{} {}").addArgument((Object) (null)).addArgument(1).log();
                  }
              }
              """
          )
        );
    }

    @Test
    void kotlinUsageWithholdsMigration() {
        rewriteRun(
          spec -> spec.parser(KotlinParser.builder().classpathFromResources(new InMemoryExecutionContext(), "slf4j-fluent", "slf4j-api-2")),
          kotlin(
            """
              import org.fissore.slf4j.FluentLogger

              class Kt(val logger: FluentLogger) {
                  fun log() {
                      logger.info().log("message")
                  }
              }
              """,
            spec -> spec.after(after -> assertThat(after)
              .contains("/*~~(This slf4j-fluent usage requires manual migration before rerunning the recipe)~~>*/")
              .actual())
          ),
          java(
            """
              import org.fissore.slf4j.FluentLogger;
              class Example {
                  void log(FluentLogger logger) {
                      logger.info().log("unchanged");
                  }
              }
              """
          )
        );
    }

    @Test
    void directDependencyWithJavaSources() {
        rewriteRun(
          mavenProject(
            "example",
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>example</groupId>
                    <artifactId>example</artifactId>
                    <version>1.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.fissore</groupId>
                            <artifactId>slf4j-fluent</artifactId>
                            <version>0.14.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """,
              spec -> spec.after(after -> assertThat(after)
                .containsOnlyOnce("<artifactId>slf4j-api</artifactId>")
                .containsPattern("<version>2\\.0\\.\\d+</version>")
                .doesNotContain("slf4j-fluent")
                .actual())
            ),
            srcMainJava(
              java(
                """
                  import org.fissore.slf4j.FluentLogger;
                  class Example {
                      void log(FluentLogger logger) {
                          logger.info().log("message");
                      }
                  }
                  """,
                """
                  import org.slf4j.Logger;

                  class Example {
                      void log(Logger logger) {
                          logger.atInfo().log("message");
                      }
                  }
                  """
              )
            )
          )
        );
    }

    @Test
    void manyArguments() {
        rewriteRun(
          java(
            """
              import org.fissore.slf4j.FluentLogger;
              class Example {
                  void log(FluentLogger logger) {
                      logger.info().log("{} {} {} {} {} {}", 1, 2, 3, 4, 5, 6);
                      logger.info().log("{} {}", null, 1);
                  }
              }
              """,
            """
              import org.slf4j.Logger;

              class Example {
                  void log(Logger logger) {
                      logger.atInfo().setMessage("{} {} {} {} {} {}").addArgument(1).addArgument(2).addArgument(3).addArgument(4).addArgument(5).addArgument(6).log();
                      logger.atInfo().setMessage("{} {}").addArgument((Object) null).addArgument(1).log();
                  }
              }
              """
          )
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
      "logger.info(marker).log(\"message\");",
      "builder.withMarker(marker);",
      "Class<?> type = org.fissore.slf4j.Util.class;",
      "org.fissore.slf4j.Util unused = null;",
      "Object custom = new org.fissore.slf4j.FluentLogger(org.slf4j.LoggerFactory.getLogger(\"example\")) {};",
      "logger.info().withMarker(marker).withMarker(marker).log(\"message\");",
      "logger.info().log(\"{}\", values);",
      "logger.info().log(() -> \"{}\", values[0] = 1);",
      "logger.info().log(\"{} {}\", org.fissore.slf4j.Util.lazy(() -> values[0]), values[0] = 1);",
      "java.util.function.Supplier<?> supplier = org.fissore.slf4j.Util.lazy(() -> 1);",
      "java.util.function.Supplier<org.fissore.slf4j.LoggerAtLevel> supplier = logger::info;",
      "builder.log(\"{}\", 1);",
      "logger.info().log(\"{}\", null);"
    })
    void unsupportedOperations(String statement) {
        String before = """
          import org.fissore.slf4j.FluentLogger;
          import org.fissore.slf4j.LoggerAtLevel;
          import org.slf4j.Marker;

          class Example {
              void log(FluentLogger logger, LoggerAtLevel builder, Marker marker, Object[] values) {
                  %s
              }
          }
          """.formatted(statement);
        rewriteRun(
          java(
            before,
            spec -> spec.after(after -> assertThat(after)
              .contains("/*~~(This slf4j-fluent usage requires manual migration before rerunning the recipe)~~>*/")
              .doesNotContain("atInfo", "addMarker", "addArgument")
              .actual())
          )
        );
    }

    @Test
    void subclass() {
        rewriteRun(
          java(
            """
              import org.fissore.slf4j.FluentLogger;
              import org.slf4j.Logger;

              class CustomLogger extends FluentLogger {
                  CustomLogger(Logger delegate) {
                      super(delegate);
                  }
              }
              """,
            """
              import org.fissore.slf4j.FluentLogger;
              import org.slf4j.Logger;

              /*~~(This slf4j-fluent usage requires manual migration before rerunning the recipe)~~>*/class CustomLogger extends FluentLogger {
                  CustomLogger(Logger delegate) {
                      /*~~(This slf4j-fluent usage requires manual migration before rerunning the recipe)~~>*/super(delegate);
                  }
              }
              """
          )
        );
    }

    @Test
    void addsDirectApiWhenFluentWasTransitive() {
        rewriteRun(
          mavenProject(
            "example",
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>example</groupId>
                    <artifactId>example</artifactId>
                    <version>1.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.slf4j</groupId>
                            <artifactId>slf4j-api</artifactId>
                            <version>1.7.36</version>
                        </dependency>
                    </dependencies>
                </project>
                """,
              spec -> spec.after(after -> assertThat(after)
                .containsOnlyOnce("<artifactId>slf4j-api</artifactId>")
                .containsPattern("<version>2\\.0\\.\\d+</version>")
                .doesNotContain("1.7.36")
                .actual())
            ),
            srcMainJava(
              java(
                """
                  import org.fissore.slf4j.FluentLogger;
                  class Example {
                      void log(FluentLogger logger) {
                          logger.info().log("message");
                      }
                  }
                  """,
                """
                  import org.slf4j.Logger;

                  class Example {
                      void log(Logger logger) {
                          logger.atInfo().log("message");
                      }
                  }
                  """
              )
            )
          ),
          mavenProject(
            "unrelated",
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>example</groupId>
                    <artifactId>unrelated</artifactId>
                    <version>1.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.slf4j</groupId>
                            <artifactId>slf4j-api</artifactId>
                            <version>1.7.36</version>
                        </dependency>
                    </dependencies>
                </project>
                """
            )
          )
        );
    }
}
