/*
 * Copyright 2023 the original author or authors.
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

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class ConvertJulEnteringTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipes(new ConvertJulEntering(),
            new ChangeType("java.util.logging.Logger", "org.apache.logging.log4j.Logger", true))
          .parser(JavaParser.fromJavaVersion().classpath("log4j-api"));
    }

    @Test
    @DocumentExample
    void enteringToTraceEntry() {
        rewriteRun(
          // language=java
          java(
            """
              import java.util.logging.Logger;

              class Test {
                  void method(Logger logger) {
                    logger.entering("Test", "method");
                    logger.entering("Test", "method", "param");
                    logger.entering("Test", "method", new Object[]{"param1", "param2"});
                  }
              }
              """,
            """
              import org.apache.logging.log4j.Logger;

              class Test {
                  void method(Logger logger) {
                    logger.traceEntry(null);
                    logger.traceEntry(null, "param");
                    logger.traceEntry(null, new Object[]{"param1", "param2"});
                  }
              }
              """
          )
        );
    }
}
