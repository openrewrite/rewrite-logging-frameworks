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
package org.openrewrite.java.logging.logback

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.openrewrite.Recipe
import org.openrewrite.java.JavaParser
import org.openrewrite.java.JavaRecipeTest

@Disabled
class Log4jAppenderToLogbackTest : JavaRecipeTest {
    override val parser: JavaParser = JavaParser.fromJavaVersion()
        .logCompilationWarningsAndErrors(true)
        .classpath("log4j")
        .build()

    override val recipe: Recipe
        get() = Log4jAppenderToLogback()

    @Test
    fun appenderMigration() = assertChanged(
        before = """
            import org.apache.log4j.AppenderSkeleton;
            import org.apache.log4j.spi.LoggingEvent;

            class TrivialAppender extends AppenderSkeleton {

                @Override
                protected void append(LoggingEvent loggingevent) {
                    String s = this.layout.format(loggingevent);
                    System.out.println(s);
                }

                public void close() {
                    // nothing to do
                }

                public boolean requiresLayout() {
                    return true;
                }
            }
        """,
        after = """
            import ch.qos.logback.classic.spi.ILoggingEvent;
            import ch.qos.logback.core.AppenderBase;

            class TrivialAppender extends AppenderBase<ILoggingEvent> {

                @Override
                protected void append(ILoggingEvent loggingevent) {
                    String s = this.layout.doLayout(loggingevent);
                    System.out.println(s);
                }
            }
        """
    )

}
