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
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class CommonsLoggingToLog4jTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResource("/META-INF/rewrite/log4j.yml",
            "org.openrewrite.java.logging.log4j.CommonsLoggingToLog4j")
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "log4j-api-2", "commons-logging-1.3", "lombok-1.18"));
    }

    @DocumentExample
    @Test
    void loggerFactoryToLogManager() {
        // language=java
        rewriteRun(
          java(
            """
              import org.apache.commons.logging.LogFactory;
              import org.apache.commons.logging.Log;

              class Test {
                  Log log1 = LogFactory.getLog(Test.class);
                  Log log2 = LogFactory.getLog("Test");
                  Log log3 = LogFactory.getFactory().getInstance(Test.class);
                  Log log4 = LogFactory.getFactory().getInstance("Test");
              }
              """,
            """
              import org.apache.logging.log4j.LogManager;
              import org.apache.logging.log4j.Logger;

              class Test {
                  Logger log1 = LogManager.getLogger(Test.class);
                  Logger log2 = LogManager.getLogger("Test");
                  Logger log3 = LogManager.getLogger(Test.class);
                  Logger log4 = LogManager.getLogger("Test");
              }
              """
          )
        );
    }

    @Test
    void changeLombokLogAnnotation() {
        // language=java
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.builder()
            .identifiers(false)
            .methodInvocations(false)
            .build()),
          java(
            """
              import lombok.extern.apachecommons.CommonsLog;

              @CommonsLog
              class Test {
                  void method() {
                      log.info("uh oh");
                  }
              }
              """,
            """
              import lombok.extern.log4j.Log4j2;

              @Log4j2
              class Test {
                  void method() {
                      log.info("uh oh");
                  }
              }
              """
          )
        );
    }
}
