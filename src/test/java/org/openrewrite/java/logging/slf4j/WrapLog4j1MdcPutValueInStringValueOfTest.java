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
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class WrapLog4j1MdcPutValueInStringValueOfTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new WrapLog4j1MdcPutValueInStringValueOf())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "log4j-1.2.+"));
    }

    @DocumentExample
    @Test
    void replacePatterns() {
        //language=java
        rewriteRun(
          java(
            """
              import org.apache.log4j.MDC;

              import java.util.Map;
              import java.util.function.Supplier;

              class Test {
                  void method(Map<String, String> map, Object obj, Throwable t, Supplier<String> supplier, int count) {
                      MDC.put("map", map);
                      MDC.put("obj", obj);
                      MDC.put("throwable", t);
                      MDC.put("supplier", supplier);
                      MDC.put("count", count);
                      MDC.put("call", compute());
                  }

                  Object compute() {
                      return new Object();
                  }
              }
              """,
            """
              import org.apache.log4j.MDC;

              import java.util.Map;
              import java.util.function.Supplier;

              class Test {
                  void method(Map<String, String> map, Object obj, Throwable t, Supplier<String> supplier, int count) {
                      MDC.put("map", String.valueOf(map));
                      MDC.put("obj", String.valueOf(obj));
                      MDC.put("throwable", String.valueOf(t));
                      MDC.put("supplier", String.valueOf(supplier));
                      MDC.put("count", String.valueOf(count));
                      MDC.put("call", String.valueOf(compute()));
                  }

                  Object compute() {
                      return new Object();
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotReplaceInvalidPatterns() {
        //language=java
        rewriteRun(
          java(
            """
              import org.apache.log4j.MDC;

              class Other {
                  void put(String key, Object value) {
                  }
              }

              class Test {
                  void method(String value, Object obj, Other other) {
                      MDC.put("literal", "value");
                      MDC.put("typed", value);
                      MDC.put("null", null);
                      MDC.put("wrapped", String.valueOf(obj));
                      MDC.put("fqn", java.lang.String.valueOf(obj));
                      other.put("notMdc", obj);
                  }
              }
              """
          )
        );
    }
}
