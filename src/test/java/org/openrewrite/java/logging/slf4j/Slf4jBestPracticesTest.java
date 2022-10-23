/*
 * Copyright 2022 the original author or authors.
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
package org.openrewrite.java.logging.slf4j;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class Slf4jBestPracticesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(Environment.builder()
            .scanRuntimeClasspath("org.openrewrite.java.logging.slf4j")
            .build()
            .activateRecipes("org.openrewrite.java.logging.slf4j.Slf4jBestPractices"))
          .parser(JavaParser.fromJavaVersion().classpath("slf4j-api"));
    }

    @SuppressWarnings("RedundantSlf4jDefinition")
    @Test
    void applyBestPractices() {
        //language=java
        rewriteRun(
          java(
            """
              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;
              class Test {
                  Logger logger = LoggerFactory.getLogger(String.class);
                  void test() {
                      Object obj1 = new Object();
                      Object obj2 = new Object();
                      logger.info("Hello " + obj1 + ", " + obj2);
                      Exception e = new Exception();
                      logger.warn(String.valueOf(e));
                      logger.error(e.getMessage());
                      logger.error(e.getLocalizedMessage());
                  }
              }
              """,
            """
              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;
              class Test {
                  Logger logger = LoggerFactory.getLogger(Test.class);
                  void test() {
                      Object obj1 = new Object();
                      Object obj2 = new Object();
                      logger.info("Hello {}, {}", obj1, obj2);
                      Exception e = new Exception();
                      logger.warn("Exception", e);
                      logger.error("", e);
                      logger.error("", e);
                  }
              }
              """
          )
        );
    }
}
