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
import org.openrewrite.DocumentExample;
package org.openrewrite.java.logging.slf4j;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class InexpensiveSLF4JLoggersTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new InexpensiveSLF4JLoggers())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "slf4j-api-2.1.+"));
    }

    @DocumentExample
    @Test
    void replaceGetMessageWithException() {
        //language=java
        rewriteRun(
          java(
            """
              import org.slf4j.Logger;

              class A {
                  void method(Logger LOG) {
                      LOG.debug("SomeString {}, {}", "some param", expensiveOp());
                  }

                  String expensiveOp() {
                      return "expensive";
                  }
              }
              """,
            """
              import org.slf4j.Logger;

              class A {
                  void method(Logger LOG) {
                      if (LOG.isDebugEnabled()) {
                          LOG.debug("SomeString {}, {}", "some param", expensiveOp());
                      }
                  }

                  String expensiveOp() {
                      return "expensive";
                  }
              }
              """
          )
        );
    }
}
