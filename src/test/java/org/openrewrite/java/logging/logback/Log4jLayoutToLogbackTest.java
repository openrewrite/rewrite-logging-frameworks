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
package org.openrewrite.java.logging.logback;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class Log4jLayoutToLogbackTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new Log4jLayoutToLogback())
          .parser(JavaParser.fromJavaVersion().classpath("log4j"));
    }

    @DocumentExample
    @Test
    void layoutMigration() {
        //language=java
        rewriteRun(
          java(
            """
              import org.apache.log4j.Layout;
              import org.apache.log4j.spi.LoggingEvent;

              class TrivialLayout extends Layout {

                  @Override
                  public void activateOptions() {
                      // there are no options to activate
                  }

                  @Override
                  public String format(LoggingEvent loggingEvent) {
                      return loggingEvent.getRenderedMessage();
                  }

                  @Override
                  public boolean ignoresThrowable() {
                      return true;
                  }
              }
              """,
            """
              import ch.qos.logback.classic.spi.ILoggingEvent;
              import ch.qos.logback.core.LayoutBase;

              class TrivialLayout extends LayoutBase<ILoggingEvent> {

                  @Override
                  public String doLayout(ILoggingEvent loggingEvent) {
                      return loggingEvent.getMessage();
                  }
              }
              """
          )
        );
    }

    @Test
    void layoutMigrationWithLifeCycle() {
        //language=java
        rewriteRun(
          java(
            """
              import org.apache.log4j.Layout;
              import org.apache.log4j.spi.LoggingEvent;

              class TrivialLayout extends Layout {
                  @Override
                  public void activateOptions() {
                      System.out.println("starting...");
                  }

                  @Override
                  public String format(LoggingEvent loggingEvent) {
                      return loggingEvent.getRenderedMessage();
                  }

                  @Override
                  public boolean ignoresThrowable() {
                      return true;
                  }
              }
              """,
            """
              import ch.qos.logback.classic.spi.ILoggingEvent;
              import ch.qos.logback.core.LayoutBase;
              import ch.qos.logback.core.spi.LifeCycle;

              class TrivialLayout extends LayoutBase<ILoggingEvent> implements LifeCycle {
                  @Override
                  public void start() {
                      System.out.println("starting...");
                  }

                  @Override
                  public String doLayout(ILoggingEvent loggingEvent) {
                      return loggingEvent.getMessage();
                  }
              }
              """
          )
        );
    }
}
