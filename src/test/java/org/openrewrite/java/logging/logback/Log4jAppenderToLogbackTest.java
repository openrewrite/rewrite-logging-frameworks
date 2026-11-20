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
package org.openrewrite.java.logging.logback;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeQualityValidation;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class Log4jAppenderToLogbackTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new Log4jAppenderToLogback())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "log4j-1.2.+"))
          .afterParser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "log4j-1.2.+", "logback-classic-1.3", "logback-core-1.3"));
    }

    @DocumentExample
    @Test
    void appenderMigration() {
        //language=java
        rewriteRun(
          // The references to `layout` keep the types of Log4j 1's field and its Layout.format(..)
          spec -> spec.recipeQualityValidation(RecipeQualityValidation.builder().parserTypedTrees(false).build()),
          java(
            """
              import org.apache.log4j.AppenderSkeleton;
              import org.apache.log4j.spi.LoggingEvent;

              class TrivialAppender extends AppenderSkeleton {
                  @Override
                  protected void append(LoggingEvent event) {
                      String s = this.layout.format(event);
                      System.out.println(s);
                  }

                  @Override
                  public void close() {
                      // nothing to do
                  }

                  @Override
                  public boolean requiresLayout() {
                      return true;
                  }
              }
              """,
            """
              import ch.qos.logback.classic.spi.ILoggingEvent;
              import ch.qos.logback.core.AppenderBase;
              import ch.qos.logback.core.LayoutBase;

              class TrivialAppender extends AppenderBase<ILoggingEvent> {
                  protected LayoutBase<ILoggingEvent> layout;

                  @Override
                  protected void append(ILoggingEvent event) {
                      String s = this.layout.doLayout(event);
                      System.out.println(s);
                  }

                  public LayoutBase<ILoggingEvent> getLayout() {
                      return layout;
                  }

                  public void setLayout(LayoutBase<ILoggingEvent> layout) {
                      this.layout = layout;
                  }
              }
              """
          )
        );
    }

    @Test
    void appenderWithoutLayout() {
        //language=java
        rewriteRun(
          java(
            """
              import org.apache.log4j.AppenderSkeleton;
              import org.apache.log4j.spi.LoggingEvent;

              class CountingAppender extends AppenderSkeleton {
                  int count;

                  @Override
                  protected void append(LoggingEvent event) {
                      count++;
                  }

                  @Override
                  public void close() {
                  }

                  @Override
                  public boolean requiresLayout() {
                      return false;
                  }
              }
              """,
            """
              import ch.qos.logback.classic.spi.ILoggingEvent;
              import ch.qos.logback.core.AppenderBase;

              class CountingAppender extends AppenderBase<ILoggingEvent> {
                  int count;

                  @Override
                  protected void append(ILoggingEvent event) {
                      count++;
                  }
              }
              """
          )
        );
    }
}
