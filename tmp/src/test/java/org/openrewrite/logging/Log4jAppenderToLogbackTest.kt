package org.openrewrite.logging

import org.junit.jupiter.api.Test
import org.openrewrite.java.JavaParser

class Log4jAppenderToLogbackTest : JavaParser(dependenciesFromClasspath("log4j")) {
    @Test
    fun appenderMigration() {
        val a = parse("""
            import org.apache.log4j.AppenderSkeleton;
            import org.apache.log4j.spi.LoggingEvent;

            public class TrivialAppender extends AppenderSkeleton {
            
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
        """.trimIndent())

        val fixed = a.refactor().visit(Log4jAppenderToLogback()).fix().fixed

        assertRefactored(fixed, """
            import ch.qos.logback.classic.spi.ILoggingEvent;
            import ch.qos.logback.core.AppenderBase;
            
            public class TrivialAppender extends AppenderBase<ILoggingEvent> {
            
              @Override
              protected void append(ILoggingEvent loggingevent) {
                String s = this.layout.doLayout(loggingevent);
                System.out.println(s);
              }
            }
        """)
    }
}
