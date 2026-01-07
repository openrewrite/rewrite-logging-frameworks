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
package org.openrewrite.java.logging.log4j;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class ConvertJulEnteringTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipes(new ConvertJulEntering())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "log4j-api-2.+"));
    }

    @DocumentExample
    @Test
    void enteringToTraceEntry() {
        rewriteRun(
          // language=java
          java(
            """
              import java.util.logging.Logger; // ChangeType will handle import

              class Test {
                  void method(Logger logger) {
                    logger.entering("Test", "method");
                    logger.entering("Test", "method", "param");
                    logger.entering("Test", "method", new Object[]{"param1", "param2"});
                  }
              }
              """,
            """
              import java.util.logging.Logger; // ChangeType will handle import

              class Test {
                  void method(Logger logger) {
                    logger.traceEntry();
                    logger.traceEntry(null, "param");
                    logger.traceEntry(null, new Object[]{"param1", "param2"});
                  }
              }
              """
          )
        );
    }
}
