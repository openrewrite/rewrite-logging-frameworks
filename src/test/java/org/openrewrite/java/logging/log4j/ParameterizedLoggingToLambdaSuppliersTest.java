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
package org.openrewrite.java.logging.log4j;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.kotlin.KotlinParser;
import org.openrewrite.test.RecipeQualityValidation;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.kotlin.Assertions.kotlin;

class ParameterizedLoggingToLambdaSuppliersTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ParameterizedLoggingToLambdaSuppliers(null))
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "log4j-api"));
    }

    @DocumentExample
    @Test
    void wrapParametersInSuppliers() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.logging.log4j.Logger;

              class Test {
                  void test(Logger logger, Order order) {
                      logger.debug("Order {} costs {}", order.getId(), order.computeTotal());
                  }

                  interface Order {
                      String getId();
                      long computeTotal();
                  }
              }
              """,
            """
              import org.apache.logging.log4j.Logger;

              class Test {
                  void test(Logger logger, Order order) {
                      logger.debug("Order {} costs {}", () -> order.getId(), () -> order.computeTotal());
                  }

                  interface Order {
                      String getId();
                      long computeTotal();
                  }
              }
              """
          )
        );
    }

    @Test
    void everyLevel() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.logging.log4j.Logger;

              class Test {
                  void test(Logger logger) {
                      logger.trace("{}", compute());
                      logger.debug("{}", compute());
                      logger.info("{}", compute());
                      logger.warn("{}", compute());
                      logger.error("{}", compute());
                      logger.fatal("{}", compute());
                  }

                  String compute() {
                      return "";
                  }
              }
              """,
            """
              import org.apache.logging.log4j.Logger;

              class Test {
                  void test(Logger logger) {
                      logger.trace("{}", () -> compute());
                      logger.debug("{}", () -> compute());
                      logger.info("{}", () -> compute());
                      logger.warn("{}", () -> compute());
                      logger.error("{}", () -> compute());
                      logger.fatal("{}", () -> compute());
                  }

                  String compute() {
                      return "";
                  }
              }
              """
          )
        );
    }

    @Test
    void onlySelectedLevels() {
        rewriteRun(
          spec -> spec.recipe(new ParameterizedLoggingToLambdaSuppliers(List.of("trace", "DEBUG"))),
          //language=java
          java(
            """
              import org.apache.logging.log4j.Level;
              import org.apache.logging.log4j.Logger;

              class Test {
                  void test(Logger logger) {
                      logger.trace("{}", compute());
                      logger.debug("{}", compute());
                      logger.info("{}", compute());
                      logger.log(Level.DEBUG, "{}", compute());
                      logger.log(Level.ERROR, "{}", compute());
                  }

                  String compute() {
                      return "";
                  }
              }
              """,
            """
              import org.apache.logging.log4j.Level;
              import org.apache.logging.log4j.Logger;

              class Test {
                  void test(Logger logger) {
                      logger.trace("{}", () -> compute());
                      logger.debug("{}", () -> compute());
                      logger.info("{}", compute());
                      logger.log(Level.DEBUG, "{}", () -> compute());
                      logger.log(Level.ERROR, "{}", compute());
                  }

                  String compute() {
                      return "";
                  }
              }
              """
          )
        );
    }

    @Test
    void markerAndLevel() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.logging.log4j.Level;
              import org.apache.logging.log4j.Logger;
              import org.apache.logging.log4j.Marker;

              class Test {
                  void test(Logger logger, Marker marker) {
                      logger.info(marker, "{}", compute());
                      logger.log(Level.WARN, "{}", compute());
                      logger.log(Level.WARN, marker, "{}", compute());
                  }

                  String compute() {
                      return "";
                  }
              }
              """,
            """
              import org.apache.logging.log4j.Level;
              import org.apache.logging.log4j.Logger;
              import org.apache.logging.log4j.Marker;

              class Test {
                  void test(Logger logger, Marker marker) {
                      logger.info(marker, "{}", () -> compute());
                      logger.log(Level.WARN, "{}", () -> compute());
                      logger.log(Level.WARN, marker, "{}", () -> compute());
                  }

                  String compute() {
                      return "";
                  }
              }
              """
          )
        );
    }

    @Test
    void manyParameters() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.logging.log4j.Logger;

              class Test {
                  void test(Logger logger, String a, int b) {
                      logger.debug("{} {} {} {} {} {} {} {} {} {} {}", a, b, a, b, a, b, a, b, a, b, String.valueOf(b));
                  }
              }
              """,
            """
              import org.apache.logging.log4j.Logger;

              class Test {
                  void test(Logger logger, String a, int b) {
                      logger.debug("{} {} {} {} {} {} {} {} {} {} {}", () -> a, () -> b, () -> a, () -> b, () -> a, () -> b, () -> a, () -> b, () -> a, () -> b, () -> String.valueOf(b));
                  }
              }
              """
          )
        );
    }

    @Test
    void keepLineBreaksAndComments() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.logging.log4j.Logger;

              class Test {
                  void test(Logger logger) {
                      logger.debug("Values {} and {}",
                              compute(),
                              /* total */ compute());
                      logger.debug("Values {} and {}",
                              compute() /* first */,
                              compute() // second
                      );
                  }

                  String compute() {
                      return "";
                  }
              }
              """,
            """
              import org.apache.logging.log4j.Logger;

              class Test {
                  void test(Logger logger) {
                      logger.debug("Values {} and {}",
                              () -> compute(),
                              /* total */ () -> compute());
                      logger.debug("Values {} and {}",
                              () -> compute() /* first */,
                              () -> compute() // second
                      );
                  }

                  String compute() {
                      return "";
                  }
              }
              """
          )
        );
    }

    @Test
    void levelVariable() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.logging.log4j.Level;
              import org.apache.logging.log4j.Logger;

              class Test {
                  void test(Logger logger, Level level) {
                      logger.log(level, "{}", compute());
                  }

                  String compute() {
                      return "";
                  }
              }
              """,
            """
              import org.apache.logging.log4j.Level;
              import org.apache.logging.log4j.Logger;

              class Test {
                  void test(Logger logger, Level level) {
                      logger.log(level, "{}", () -> compute());
                  }

                  String compute() {
                      return "";
                  }
              }
              """
          )
        );
    }

    @Test
    void callsSupplierOverload() {
        rewriteRun(
          spec -> spec.afterRecipe(run -> {
              J.CompilationUnit cu = (J.CompilationUnit) run.getChangeset().getAllResults().get(0).getAfter();
              new JavaIsoVisitor<Integer>() {
                  @Override
                  public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, Integer p) {
                      if ("debug".equals(method.getSimpleName())) {
                          JavaType.Method type = method.getMethodType();
                          assertThat(type).isNotNull();
                          assertThat(TypeUtils.isOfClassType(type.getDeclaringType(), "org.apache.logging.log4j.Logger")).isTrue();
                          assertThat(type.getParameterTypes()).last()
                            .matches(t -> t instanceof JavaType.Array &&
                                          TypeUtils.isOfClassType(((JavaType.Array) t).getElemType(), "org.apache.logging.log4j.util.Supplier"));
                      }
                      return super.visitMethodInvocation(method, p);
                  }
              }.visit(cu, 0);
          }),
          //language=java
          java(
            """
              import org.apache.logging.log4j.Logger;

              class Test {
                  void test(Logger logger) {
                      logger.debug("{}", compute());
                  }

                  String compute() {
                      return "";
                  }
              }
              """,
            """
              import org.apache.logging.log4j.Logger;

              class Test {
                  void test(Logger logger) {
                      logger.debug("{}", () -> compute());
                  }

                  String compute() {
                      return "";
                  }
              }
              """
          )
        );
    }

    @Test
    void supplierGet() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.logging.log4j.Logger;
              import org.apache.logging.log4j.util.Supplier;

              class Test {
                  void test(Logger logger, Supplier<String> log4jSupplier, java.util.function.Supplier<String> javaSupplier) {
                      logger.debug("{}", log4jSupplier.get());
                      logger.debug("{}", javaSupplier.get());
                      logger.debug("{} {}", javaSupplier.get(), compute());
                  }

                  String compute() {
                      return "";
                  }
              }
              """,
            """
              import org.apache.logging.log4j.Logger;
              import org.apache.logging.log4j.util.Supplier;

              class Test {
                  void test(Logger logger, Supplier<String> log4jSupplier, java.util.function.Supplier<String> javaSupplier) {
                      logger.debug("{}", () -> log4jSupplier.get());
                      logger.debug("{}", () -> javaSupplier.get());
                      logger.debug("{} {}", () -> javaSupplier.get(), () -> compute());
                  }

                  String compute() {
                      return "";
                  }
              }
              """
          )
        );
    }

    @Test
    void composedMessages() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.logging.log4j.Level;
              import org.apache.logging.log4j.Logger;
              import org.apache.logging.log4j.Marker;

              class Test {
                  void test(Logger logger, Marker marker, String name, Exception e) {
                      logger.debug("Hello " + name);
                      logger.debug(String.format("Hello %s", name));
                      logger.debug(name.trim());
                      logger.debug(marker, "Hello " + name);
                      logger.log(Level.DEBUG, "Hello " + name);
                      logger.log(Level.DEBUG, marker, "Hello " + name);
                      logger.error("Failed " + name, e);
                      logger.error(marker, "Failed " + name, e);
                  }
              }
              """,
            """
              import org.apache.logging.log4j.Level;
              import org.apache.logging.log4j.Logger;
              import org.apache.logging.log4j.Marker;

              class Test {
                  void test(Logger logger, Marker marker, String name, Exception e) {
                      logger.debug(() -> "Hello " + name);
                      logger.debug(() -> String.format("Hello %s", name));
                      logger.debug(() -> name.trim());
                      logger.debug(marker, () -> "Hello " + name);
                      logger.log(Level.DEBUG, () -> "Hello " + name);
                      logger.log(Level.DEBUG, marker, () -> "Hello " + name);
                      logger.error(() -> "Failed " + name, e);
                      logger.error(marker, () -> "Failed " + name, e);
                  }
              }
              """
          )
        );
    }

    @Test
    void expressionsWithExpensiveParts() {
        rewriteRun(
          // A parser types the ternary in a `Supplier<?>` lambda as `Object`, the recipe keeps its `String` type
          spec -> spec.recipeQualityValidation(RecipeQualityValidation.builder().parserTypedTrees(false).build()),
          //language=java
          java(
            """
              import org.apache.logging.log4j.Logger;

              class Test {
                  void test(Logger logger, String name, String[] names) {
                      logger.debug(name == null ? "No name" : name.trim());
                      logger.debug("{}", name == null ? "none" : name.trim());
                      logger.debug("{}", (String) compute());
                      logger.debug("{}", names[name.length()]);
                      logger.debug("{}", name.length() * 2);
                  }

                  Object compute() {
                      return null;
                  }
              }
              """,
            """
              import org.apache.logging.log4j.Logger;

              class Test {
                  void test(Logger logger, String name, String[] names) {
                      logger.debug(() -> name == null ? "No name" : name.trim());
                      logger.debug("{}", () -> name == null ? "none" : name.trim());
                      logger.debug("{}", () -> (String) compute());
                      logger.debug("{}", () -> names[name.length()]);
                      logger.debug("{}", () -> name.length() * 2);
                  }

                  Object compute() {
                      return null;
                  }
              }
              """
          )
        );
    }

    @Nested
    class NoChange {

        @Test
        void simpleArguments() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.apache.logging.log4j.Logger;

                  class Test {
                      static final String CONSTANT = "c";
                      String name;

                      void test(Logger logger, String id, Object value, String[] ids, int count) {
                          logger.debug("{} {} {} {} {}", id, 42, CONSTANT, this.name, getName());
                          logger.debug("{} {} {} {}", (String) value, ids[count - 1], id == null ? "none" : id, count * 2);
                      }

                      String getName() {
                          return name;
                      }
                  }
                  """
              )
            );
        }

        @Test
        void trailingThrowable() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.apache.logging.log4j.Logger;

                  class Test {
                      void test(Logger logger, Exception e) {
                          logger.error("Failed {}", compute(), e);
                      }

                      String compute() {
                          return "";
                      }
                  }
                  """
              )
            );
        }

        @Test
        void loopVariable() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.apache.logging.log4j.Logger;

                  class Test {
                      void test(Logger logger) {
                          for (int i = 0; i < 3; i++) {
                              logger.debug("{}", String.valueOf(i));
                          }
                      }
                  }
                  """
              )
            );
        }

        @Test
        void reassignedParameter() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.apache.logging.log4j.Logger;

                  class Test {
                      void test(Logger logger, String s) {
                          s = s.trim();
                          logger.debug("{}", s.toUpperCase());
                      }
                  }
                  """
              )
            );
        }

        @Test
        void sideEffect() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.apache.logging.log4j.Logger;

                  class Test {
                      int counter;

                      void test(Logger logger) {
                          logger.debug("{}", String.valueOf(counter++));
                      }
                  }
                  """
              )
            );
        }

        @Test
        void checkedException() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.apache.logging.log4j.Logger;

                  class Test {
                      void test(Logger logger) throws Exception {
                          logger.debug("{}", load());
                      }

                      String load() throws Exception {
                          return "";
                      }
                  }
                  """
              )
            );
        }

        @Test
        void explicitArray() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.apache.logging.log4j.Logger;

                  class Test {
                      void test(Logger logger, Object[] params) {
                          logger.debug("{} {}", params);
                      }
                  }
                  """
              )
            );
        }

        @Test
        void lambdasAndMethodReferences() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.apache.logging.log4j.Level;
                  import org.apache.logging.log4j.Logger;
                  import org.apache.logging.log4j.Marker;

                  class Test {
                      void test(Logger logger, Marker marker, java.util.function.Supplier<String> javaSupplier) {
                          logger.debug("{}", this::compute);
                          logger.debug("{}", javaSupplier::get);
                          logger.debug("{}", () -> {
                              return compute();
                          });
                          logger.debug("{} {}", () -> compute(), () -> compute());
                          logger.debug(marker, "{}", () -> compute());
                          logger.log(Level.DEBUG, "{}", () -> compute());
                          logger.log(Level.DEBUG, marker, "{}", () -> compute());
                      }

                      String compute() {
                          return "";
                      }
                  }
                  """
              )
            );
        }

        @Test
        void supplierValues() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.apache.logging.log4j.Logger;
                  import org.apache.logging.log4j.util.Supplier;

                  class Test {
                      void test(Logger logger, Supplier<String> supplier, Supplier<?>[] suppliers) {
                          logger.debug("{} {}", supplier, supplier());
                          logger.debug("{} {}", suppliers);
                          logger.debug("{}", (Supplier<?>) this::compute);
                          logger.debug("{}", (Supplier<?>) null);
                      }

                      Supplier<String> supplier() {
                          return this::compute;
                      }

                      String compute() {
                          return "";
                      }
                  }
                  """
              )
            );
        }

        @Test
        void javaUtilSupplierValues() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.apache.logging.log4j.Logger;

                  import java.util.function.Supplier;

                  class Test {
                      void test(Logger logger, Supplier<String> supplier) {
                          logger.debug("{} {}", supplier, supplier());
                      }

                      Supplier<String> supplier() {
                          return () -> "";
                      }
                  }
                  """
              )
            );
        }

        @Test
        void supplierAsMessage() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.apache.logging.log4j.Logger;
                  import org.apache.logging.log4j.util.Supplier;

                  class Test {
                      void test(Logger logger, Supplier<String> supplier, Exception e) {
                          logger.debug(() -> "Total " + compute());
                          logger.debug(supplier);
                          logger.debug(supplier, e);
                      }

                      String compute() {
                          return "";
                      }
                  }
                  """
              )
            );
        }

        @Test
        void messagesThatAreNotComposed() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.apache.logging.log4j.Logger;

                  class Test {
                      void test(Logger logger, String name, Object message, String[] names, Exception e) {
                          logger.debug("Constant message");
                          logger.debug("A long constant message " +
                                  "that is wrapped");
                          logger.debug(name);
                          logger.debug(message);
                          logger.debug(name == null ? "No name" : name);
                          logger.debug((String) message);
                          logger.debug(names[0]);
                          logger.error("Failed", e);
                          logger.debug("Name {}", name);
                          logger.debug(() -> "Hello " + name);
                          logger.debug("Name {}", () -> name);
                          for (int i = 0; i < 3; i++) {
                              logger.debug("Round " + i);
                          }
                      }
                  }
                  """
              )
            );
        }

        @Test
        void kotlinSources() {
            rewriteRun(
              spec -> spec.parser(KotlinParser.builder().classpathFromResources(new InMemoryExecutionContext(), "log4j-api")),
              //language=kotlin
              kotlin(
                """
                  import org.apache.logging.log4j.Logger

                  class Test {
                      fun test(logger: Logger, name: String) {
                          logger.debug("Name {}", name.trim())
                      }
                  }
                  """
              )
            );
        }

        @Test
        void levelVariableWithLevelsSelected() {
            rewriteRun(
              spec -> spec.recipe(new ParameterizedLoggingToLambdaSuppliers(List.of("debug"))),
              //language=java
              java(
                """
                  import org.apache.logging.log4j.Level;
                  import org.apache.logging.log4j.Logger;

                  class Test {
                      void test(Logger logger, Level level) {
                          logger.log(level, "{}", compute());
                      }

                      String compute() {
                          return "";
                      }
                  }
                  """
              )
            );
        }
    }
}
