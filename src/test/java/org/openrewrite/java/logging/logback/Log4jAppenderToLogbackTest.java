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

class Log4jAppenderToLogbackTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new Log4jAppenderToLogback())
          .parser(JavaParser.fromJavaVersion().classpath("log4j"));
    }

    @DocumentExample
    @Test
    void appenderMigration() {
        //language=java
        rewriteRun(
          java(
            """
              import org.apache.log4j.AppenderSkeleton;
              import org.apache.log4j.spi.LoggingEvent;

              class TrivialAppender extends AppenderSkeleton {
                  @Override
                  protected void append(LoggingEvent loggingEvent) {
                      String s = this.layout.format(loggingEvent);
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

              class TrivialAppender extends AppenderBase<ILoggingEvent> {
                  @Override
                  protected void append(ILoggingEvent loggingEvent) {
                      String s = this.layout.doLayout(loggingEvent);
                      System.out.println(s);
                  }
              }
              """
          )
        );
    }
}
