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
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class PrependRandomNameTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new PrependRandomName(2048))
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "log4j-1.2.+"));
    }

    @DocumentExample
    @Test
    void prependRandomName() {
        //language=java
        rewriteRun(
          java(
            """
              import org.apache.log4j.Logger;

              class Test {
                  Logger logger;
                  void test() {
                      logger.info("test");
                  }
              }
              """,
            """
              import org.apache.log4j.Logger;

              class Test {
                  Logger logger;
                  void test() {
                      logger.info("<rural_crew> test");
                  }
              }
              """
          )
        );
    }
}
