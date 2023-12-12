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

import static org.openrewrite.java.Assertions.java;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

public class ConvertJulExitingTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipes(new ConvertJulExiting(),
            new ChangeType("java.util.logging.Logger", "org.apache.logging.log4j.Logger", true))
          .parser(JavaParser.fromJavaVersion().classpath("log4j-api"));
    }

    @Test
    @DocumentExample
    void exitingToTraceExit() {
        // language=java
        rewriteRun(java("""
          import java.util.logging.Logger;

          class Test {
              void method(Logger logger) {
                logger.exiting("Test", "method");
                logger.exiting("Test", "method", "result");
              }
          }
          """, """
          import org.apache.logging.log4j.Logger;

          class Test {
              void method(Logger logger) {
                logger.traceExit();
                logger.traceExit("result");
              }
          }
          """));
    }
}
