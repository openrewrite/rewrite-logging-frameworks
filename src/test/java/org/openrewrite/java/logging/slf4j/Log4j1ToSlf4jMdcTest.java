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

class Log4j1ToSlf4jMdcTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.logging.slf4j.Log4j1ToSlf4jMdc")
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "log4j-1.2.+", "slf4j-api-2"));
    }

    @DocumentExample
    @Test
    void replacePatterns() {
        //language=java
        rewriteRun(
          java(
            """
              import org.apache.log4j.MDC;

              import java.util.Hashtable;

              class Test {
                  void method(Object value, String text) {
                      MDC.put("obj", value);
                      MDC.put("text", text);
                      MDC.put("literal", "v");
                      Hashtable context = MDC.getContext();
                      MDC.remove("obj");
                      MDC.clear();
                  }
              }
              """,
            """
              import org.slf4j.MDC;

              import java.util.Map;

              class Test {
                  void method(Object value, String text) {
                      MDC.put("obj", String.valueOf(value));
                      MDC.put("text", text);
                      MDC.put("literal", "v");
                      Map<String, String> context = MDC.getCopyOfContextMap();
                      MDC.remove("obj");
                      MDC.clear();
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
              import org.slf4j.MDC;

              import java.util.Map;

              class Test {
                  void method(String text) {
                      MDC.put("text", text);
                      Map<String, String> context = MDC.getCopyOfContextMap();
                      MDC.remove("text");
                      MDC.clear();
                  }
              }
              """
          )
        );
    }
}
