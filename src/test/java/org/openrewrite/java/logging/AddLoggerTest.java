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
package org.openrewrite.java.logging;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.Test;
import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.tree.J;
import org.openrewrite.kotlin.KotlinParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.kotlin.Assertions.kotlin;

class AddLoggerTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion()
          .classpathFromResources(new InMemoryExecutionContext(), "slf4j-api-2"));
    }

    @DocumentExample
    @Test
    void addLogger() {
        rewriteRun(
          spec -> spec.recipe(new MaybeAddLoggerToClass("Test")),
          //language=java
          java(
            """
              package test;
              class Test {
              }
              """,
            """
              package test;

              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;

              class Test {
                  private static final Logger LOGGER = LoggerFactory.getLogger(Test.class);
              }
              """
          )
        );
    }

    @Test
    void onlyOne() {
        rewriteRun(
          spec -> spec.recipe(new MaybeAddLoggerToClass("Test", 2)),
          //language=java
          java(
            """
              package test;
              class Test {
              }
              """,
            """
              package test;

              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;

              class Test {
                  private static final Logger LOGGER = LoggerFactory.getLogger(Test.class);
              }
              """
          )
        );
    }

    @Test
    void notIfExistingLogger() {
        rewriteRun(
          spec -> spec.recipe(new MaybeAddLoggerToClass("Test")),
          //language=java
          java(
            """
              package test;

              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;

              class Test {
                  private static final Logger LOGGER = LoggerFactory.getLogger(Test.class);
              }
              """
          )
        );
    }

    @Test
    void nameTakenByOtherField() {
        rewriteRun(
          spec -> spec.recipe(new MaybeAddLoggerToClass("Test")),
          //language=java
          java(
            """
              class Test {
                  String LOGGER = "audit";
              }
              """,
            """
              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;

              class Test {
                  private static final Logger LOGGER1 = LoggerFactory.getLogger(Test.class);
                  String LOGGER = "audit";
              }
              """
          )
        );
    }

    @Test
    void keepNameThatCodeOnlyRefersTo() {
        rewriteRun(
          // the references are written ahead of the field, as a recipe calling `AddLogger` may do
          spec -> spec.recipe(new MaybeAddLoggerToClass("Test")).typeValidationOptions(TypeValidation.none()),
          //language=java
          java(
            """
              class Test {
                  void test() {
                      LOGGER.info("Oh no");
                  }
              }
              """,
            """
              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;

              class Test {
                  private static final Logger LOGGER = LoggerFactory.getLogger(Test.class);

                  void test() {
                      LOGGER.info("Oh no");
                  }
              }
              """
          )
        );
    }

    @Test
    void nameTakenByInheritedField() {
        rewriteRun(
          spec -> spec.recipe(new MaybeAddLoggerToClass("Test")),
          //language=java
          java(
            """
              class Base {
                  protected String LOGGER = "audit";
              }
              """
          ),
          //language=java
          java(
            """
              class Test extends Base {
              }
              """,
            """
              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;

              class Test extends Base {
                  private static final Logger LOGGER1 = LoggerFactory.getLogger(Test.class);
              }
              """
          )
        );
    }

    @Test
    void loggerNamesAnotherImportedType() {
        rewriteRun(
          spec -> spec.recipe(new MaybeAddLoggerToClass("Test")),
          //language=java
          java(
            """
              import java.util.logging.Logger;

              class Test {
                  Logger julLogger = Logger.getLogger("x");
              }
              """,
            """
              import org.slf4j.LoggerFactory;

              import java.util.logging.Logger;

              class Test {
                  private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(Test.class);
                  Logger julLogger = Logger.getLogger("x");
              }
              """
          )
        );
    }

    @Test
    void loggerNamesAnotherStarImportedType() {
        rewriteRun(
          spec -> spec.recipe(new MaybeAddLoggerToClass("Test")),
          //language=java
          java(
            """
              import java.util.logging.*;

              class Test {
                  Logger julLogger = Logger.getLogger("x");
              }
              """,
            """
              import org.slf4j.LoggerFactory;

              import java.util.logging.*;

              class Test {
                  private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(Test.class);
                  Logger julLogger = Logger.getLogger("x");
              }
              """
          )
        );
    }

    @Test
    void notIfExistingInheritedLogger() {
        rewriteRun(
          spec -> spec.recipe(new MaybeAddLoggerToClass("Test")),
          //language=java
          java(
            """
              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;

              class Base {
                  protected static final Logger LOGGER = LoggerFactory.getLogger(Base.class);
              }
              """
          ),
          java(
            """
              class Test extends Base {
              }
              """
          )
        );
    }

    @Test
    void notIfKotlinCompanionObjectHasLogger() {
        rewriteRun(
          spec -> spec.recipe(new MaybeAddLoggerToClass("Test"))
            .parser(KotlinParser.builder().classpathFromResources(new InMemoryExecutionContext(), "slf4j-api-2")),
          //language=kotlin
          kotlin(
            """
              import org.slf4j.Logger
              import org.slf4j.LoggerFactory

              class Test {
                  companion object {
                      private val logger: Logger = LoggerFactory.getLogger(Test::class.java)
                  }
              }
              """
          )
        );
    }

    @Test
    void addWhereOnlyNestedClassHasLogger() {
        rewriteRun(
          spec -> spec.recipe(new MaybeAddLoggerToClass("Test")),
          //language=java
          java(
            """
              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;

              class Test {
                  static class Nested {
                      private static final Logger LOGGER = LoggerFactory.getLogger(Nested.class);
                  }
              }
              """,
            """
              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;

              class Test {
                  private static final Logger LOGGER1 = LoggerFactory.getLogger(Test.class);

                  static class Nested {
                      private static final Logger LOGGER = LoggerFactory.getLogger(Nested.class);
                  }
              }
              """
          )
        );
    }

    @Test
    void dontAddToInnerClass() {
        rewriteRun(
          spec -> spec.recipe(new MaybeAddLoggerToClass("Test")),
          //language=java
          java(
            """
              class Test {
                  enum Status { TRUE, FALSE, FILE_NOT_FOUND }
              }
              """,
            """
              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;

              class Test {
                  private static final Logger LOGGER = LoggerFactory.getLogger(Test.class);

                  enum Status { TRUE, FALSE, FILE_NOT_FOUND }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-logging-frameworks/issues/137")
    @Test
    void addLoggerInEnum() {
        rewriteRun(
          spec -> spec.recipe(new MaybeAddLoggerToClass("Test")),
          //language=java
          java(
            """
              enum Test {
                  One,
                  Two;
              }
              """,
            """
              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;

              enum Test {
                  One,
                  Two;
                  private static final Logger LOGGER = LoggerFactory.getLogger(Test.class);
              }
              """
          )
        );
    }

    @Test
    void innerClassNonStatic() {
        rewriteRun(
          spec -> spec.recipe(new MaybeAddLoggerToClass("Inner")),
          //language=java
          java(
            """
              package test;
              class Outer {
                  class Inner {
                  }
              }
              """,
            """
              package test;

              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;

              class Outer {
                  class Inner {
                      private final Logger LOGGER = LoggerFactory.getLogger(Inner.class);
                  }
              }
              """
          )
        );
    }

    @NoArgsConstructor(force = true)
    public static final class MaybeAddLoggerToClass extends Recipe {
        private final String simpleName;
        private final int times;

        private MaybeAddLoggerToClass(String simpleName) {
            this(simpleName, 1);
        }

        @JsonCreator
        private MaybeAddLoggerToClass(@JsonProperty("simpleName") String simpleName,
                                      @JsonProperty(value = "times", defaultValue = "1") int times) {
            this.simpleName = simpleName;
            this.times = times;
        }

        @Getter
        final String displayName = "Add logger to class";

        @Getter
        final String description = "Test recipe.";

        @Override
        public TreeVisitor<?, ExecutionContext> getVisitor() {
            return new JavaIsoVisitor<>() {
                @Override
                public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                    if (classDecl.getSimpleName().equals(simpleName)) {
                        for (var i = 0; i < times; i++) {
                            doAfterVisit(AddLogger.addSlf4jLogger(classDecl, "LOGGER", ctx));
                        }
                    }
                    return super.visitClassDeclaration(classDecl, ctx);
                }
            };
        }
    }
}
