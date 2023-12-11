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
import org.openrewrite.DocumentExample;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings("EmptyTryBlock")
class Slf4jLogShouldBeConstantTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new Slf4jLogShouldBeConstant())
          .parser(JavaParser.fromJavaVersion().classpath("slf4j-api"));
    }

    @Test
    void firstParameterIsNotLiteral() {
        //language=java
        rewriteRun(
          java(
            """
              import org.slf4j.Logger;
              class A {
                  Logger log;
                  public void inconsistent(String message, Object... args) {
                      log.warn(String.format(message, args));
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-logging-frameworks/issues/41")
    @Test
    void doNotUseStringFormat() {
        //language=java
        rewriteRun(
          java(
            """
              import org.slf4j.Logger;
              class Test {
                  Logger log;
                  void test() {
                      Object obj1 = new Object();
                      Object obj2 = new Object();
                      log.info(String.format("Object 1 is %s and Object 2 is %s", obj1, obj2));
                  }
              }
              """,
            """
              import org.slf4j.Logger;
              class Test {
                  Logger log;
                  void test() {
                      Object obj1 = new Object();
                      Object obj2 = new Object();
                      log.info("Object 1 is {} and Object 2 is {}", obj1, obj2);
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-logging-frameworks/issues/41")
    @Test
    void doNotUseValueOfException() {
        //language=java
        rewriteRun(
          java(
            """
              import org.slf4j.Logger;
              class Test {
                  Logger log;
                  void test() {
                      try {
                      } catch(Exception e) {
                          log.warn(String.valueOf(e));
                      }
                  }
              }
              """,
            """
              import org.slf4j.Logger;
              class Test {
                  Logger log;
                  void test() {
                      try {
                      } catch(Exception e) {
                          log.warn("Exception", e);
                      }
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-logging-frameworks/issues/41")
    @Test
    void doNotUseToString() {
        //language=java
        rewriteRun(
          java(
            """
              import org.slf4j.Logger;
              class Test {
                  Logger log;
                  void test() {
                      Object obj1 = new Object();
                      log.info(obj1.toString());
                  }
              }
              """,
            """
              import org.slf4j.Logger;
              class Test {
                  Logger log;
                  void test() {
                      Object obj1 = new Object();
                      log.info("{}", obj1);
                  }
              }
              """
          )
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite-logging-frameworks/issues/89")
    void toStringWithoutSelect() {
        //language=java
        rewriteRun(
          java(
               """
            import org.slf4j.Logger;
            import org.slf4j.LoggerFactory;
            class A {
                private static final Logger log = LoggerFactory.getLogger(A.class);
            
                public void foo() {
                    log.error(toString());
                }
            }
            """)
        );
    }

    @Test
    void noChangeWithIndexSpecifier() {
        //language=java
        rewriteRun(
          java(
            """
              import org.slf4j.Logger;
              class A {
                  Logger log;
                  void method() {
                      log.info(String.format("The the second argument is '%2$s', and the first argument is '%1$s'.", "foo", "bar"));
                  }
              }
              """
          )
        );
    }

    @DocumentExample
    @Test
    void differentFormatSpecifier() {
        //language=java
        rewriteRun(
          java(
            """
              import org.slf4j.Logger;
              class A {
                  Logger log;
                  void method() {
                      log.info(String.format("The first argument is '%d', and the second argument is '%.2f'.", 1, 2.3333));
                  }
              }
              """,
            """
              import org.slf4j.Logger;
              class A {
                  Logger log;
                  void method() {
                      log.info("The first argument is '{}', and the second argument is '{}'.", 1, 2.3333);
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-logging-frameworks/issues/95")
    @Test
    void doNotUseStringFormatAndKeepPassedException() {
        //language=java
        rewriteRun(
          java(
            """
              import org.slf4j.Logger;
              class A {
                  Logger log;
                  void method() {
                      Exception ex = new Exception();
                      log.warn(String.format("Unexpected exception: %s", ex.getClass().getName()), ex);
                  }
              }
              """,
            """
              import org.slf4j.Logger;
              class A {
                  Logger log;
                  void method() {
                      Exception ex = new Exception();
                      log.warn("Unexpected exception: {}", ex.getClass().getName(), ex);
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-logging-frameworks/issues/95")
    @Test
    void noChangeWithCombinedSpecifier() {
        //language=java
        rewriteRun(
          java(
            """
              import org.slf4j.Logger;
              class A {
                  Logger log;
                  void method() {
                      Exception ex = new Exception();
                      log.warn(String.format("Message: {} from Unexpected exception: %s", ex.getClass().getName()), ex.getMessage(), ex);
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotUseStringFormatWithoutArgs() {
        //language=java
        rewriteRun(
          java(
            """
              import org.slf4j.Logger;
              class A {
                  Logger log;
                  void method() {
                      log.info(String.format("hi"));
                  }
              }
              """,
            """
              import org.slf4j.Logger;
              class A {
                  Logger log;
                  void method() {
                      log.info("hi");
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotUseStringFormatForBlankLog() {
        //language=java
        rewriteRun(
          java(
            """
              import org.slf4j.Logger;
              class A {
                  Logger log;
                  void method() {
                      log.info(String.format("    "));
                  }
              }
              """,
            """
              import org.slf4j.Logger;
              class A {
                  Logger log;
                  void method() {
                      log.info("    ");
                  }
              }
              """
          )
        );
    }

    @Test
    void notObjectToString() {
        //language=java
        rewriteRun(
          java(
               """
            import org.slf4j.Logger;
            import org.slf4j.LoggerFactory;
            import java.util.Arrays;
            class A {
                private static final Logger log = LoggerFactory.getLogger(A.class);
                String[] values = new String[]{"test1", "test2"};
                public void foo() {
                    log.error(Arrays.toString(values));
                }
            }
            """)
        );
    }

    @Test
    void doNotUseToStringOnAnyClass() {
        //language=java
        rewriteRun(
          java(
            """
              import org.slf4j.Logger;
              class Test {
                  Logger log;
                  void test() {
                      Test t = new Test();
                      log.info(t.toString());
                  }
              }
              """,
            """
              import org.slf4j.Logger;
              class Test {
                  Logger log;
                  void test() {
                      Test t = new Test();
                      log.info("{}", t);
                  }
              }
              """
          )
        );
    }

}
