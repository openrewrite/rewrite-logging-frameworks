/*
 * Copyright 2021 the original author or authors.
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
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings("RedundantSlf4jDefinition")
class LoggersNamedForEnclosingClassTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new LoggersNamedForEnclosingClass())
          .parser(JavaParser.fromJavaVersion().classpath("slf4j-api"));
    }

    @Issue("https://github.com/openrewrite/rewrite-logging-frameworks/issues/65")
    @Test
    void shouldRenameLogger() {
        //language=java
        rewriteRun(
          java(
            """
              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;
              class WrongClass {}
              class A {
                  Logger log = LoggerFactory.getLogger(WrongClass.class);
              }
              """,
            """
              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;
              class WrongClass {}
              class A {
                  Logger log = LoggerFactory.getLogger(A.class);
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-logging-frameworks/issues/69")
    @Test
    void shouldRenameLoggerFromMethodInvocationToClassForFinalClass() {
        //language=java
        rewriteRun(
          java(
            """
              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;
              final class A {
                  Logger log = LoggerFactory.getLogger(getClass());
              }
              """,
            """
              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;
              final class A {
                  Logger log = LoggerFactory.getLogger(A.class);
              }
              """
          )
        );
    }

    @Test
    void shouldNotChangeCorrectLoggerName() {
        //language=java
        rewriteRun(
          java(
            """
              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;
              class A {
                  Logger log = LoggerFactory.getLogger(A.class);
              }
              """
          )
        );
    }

    @Test
    void doNotChangeJavaDoc() {
        //language=java
        rewriteRun(
          java(
            """
              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;

              class A {
                  /**
                   * @see org.slf4j.LoggerFactory#getLogger(Class)
                   */
                  void method() {
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-logging-frameworks/issues/101")
    @Test
    void doNotChangeGetClassOnNonFinalClass(){
        //language=java
        rewriteRun(
          java(
            """
              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;
              class A {
                  Logger log = LoggerFactory.getLogger(getClass());
              }
              """
          )
        );
    }
}
