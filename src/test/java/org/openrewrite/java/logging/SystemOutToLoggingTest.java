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
package org.openrewrite.java.logging;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class SystemOutToLoggingTest implements RewriteTest {

    @Test
    void useSlf4j() {
        rewriteRun(
          spec -> spec.recipe(new SystemOutToLogging(null, "LOGGER", null, "debug"))
            .parser(JavaParser.fromJavaVersion().classpath("slf4j-api")),
          //language=java
          java(
            """
              import org.slf4j.Logger;
              class Test {
                  int n;
                  Logger logger;
                  
                  void test() {
                      System.out.println("Oh " + n + " no");
                  }
              }
              """,
            """
              import org.slf4j.Logger;
              class Test {
                  int n;
                  Logger logger;
                  
                  void test() {
                      logger.debug("Oh {} no", n);
                  }
              }
              """
          )
        );
    }

    @Test
    void inRunnable() {
        rewriteRun(
          spec -> spec.recipe(new SystemOutToLogging(null, "LOGGER", null, "debug"))
            .parser(JavaParser.fromJavaVersion().classpath("slf4j-api")),
          //language=java
          java(
            """
              import org.slf4j.Logger;
              class Test {
                  Logger logger;
                  
                  void test() {
                      Runnable r = () -> System.out.println("single");
                  }
              }
              """,
            """
              import org.slf4j.Logger;
              class Test {
                  Logger logger;
                  
                  void test() {
                      Runnable r = () -> logger.debug("single");
                  }
              }
              """
          )
        );
    }
}
