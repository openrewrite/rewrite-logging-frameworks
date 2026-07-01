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

class Log4j1MdcGetContextToCopyOfContextMapTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new Log4j1MdcGetContextToCopyOfContextMap())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "log4j-1.2.+"));
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
                  Hashtable field = MDC.getContext();

                  static void method() {
                      Hashtable local = MDC.getContext();
                      final Hashtable finalLocal = MDC.getContext();
                      Hashtable<String, String> parameterized = MDC.getContext();
                      Hashtable a = MDC.getContext(), b = a;
                      Hashtable assignedLater;
                      assignedLater = MDC.getContext();
                      Hashtable other = new Hashtable();
                  }

                  Hashtable returnsContext() {
                      return MDC.getContext();
                  }

                  static void reassignParam(Hashtable param) {
                      param = MDC.getContext();
                  }
              }
              """,
            """
              import org.apache.log4j.MDC;

              import java.util.Hashtable;
              import java.util.Map;

              class Test {
                  Map<String, String> field = MDC.getCopyOfContextMap();

                  static void method() {
                      Map<String, String> local = MDC.getCopyOfContextMap();
                      final Map<String, String> finalLocal = MDC.getCopyOfContextMap();
                      Map<String, String> parameterized = MDC.getCopyOfContextMap();
                      Map<String, String> a = MDC.getCopyOfContextMap(), b = a;
                      Map<String, String> assignedLater;
                      assignedLater = MDC.getCopyOfContextMap();
                      Hashtable other = new Hashtable();
                  }

                  Map<String, String> returnsContext() {
                      return MDC.getCopyOfContextMap();
                  }

                  static void reassignParam(Map<String, String> param) {
                      param = MDC.getCopyOfContextMap();
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
              import java.util.Hashtable;

              class Other {
                  Hashtable getContext() {
                      return new Hashtable();
                  }
              }

              class Test {
                  static void method(Other other) {
                      Hashtable fromOther = other.getContext();
                      Hashtable plain = new Hashtable();
                  }
              }
              """
          )
        );
    }

    /**
     * Known limitation, kept as a separate test so the documented behavior is pinned: the parameters and
     * return type of an overriding method are renamed but not retyped, because changing them would break
     * the override against the supertype (whose signature this recipe does not touch). The body then
     * needs a manual fix; this is preferable to silently breaking the override.
     */
    @Test
    void overriddenSignatureIsRenamedButNotRetyped() {
        //language=java
        rewriteRun(
          java(
            """
              import org.apache.log4j.MDC;

              import java.util.Hashtable;

              interface Context {
                  void handle(Hashtable ctx);

                  Hashtable current();
              }

              class Test implements Context {
                  public void handle(Hashtable ctx) {
                      ctx = MDC.getContext();
                  }

                  public Hashtable current() {
                      return MDC.getContext();
                  }
              }
              """,
            """
              import org.apache.log4j.MDC;

              import java.util.Hashtable;

              interface Context {
                  void handle(Hashtable ctx);

                  Hashtable current();
              }

              class Test implements Context {
                  public void handle(Hashtable ctx) {
                      ctx = MDC.getCopyOfContextMap();
                  }

                  public Hashtable current() {
                      return MDC.getCopyOfContextMap();
                  }
              }
              """
          )
        );
    }
}
