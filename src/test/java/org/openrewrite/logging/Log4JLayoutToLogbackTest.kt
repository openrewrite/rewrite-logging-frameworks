package org.openrewrite.logging

import org.junit.jupiter.api.Test
import org.openrewrite.java.JavaParser

class Log4JLayoutToLogbackTest : JavaParser(dependenciesFromClasspath("log4j")) {
    @Test
    fun layoutMigration() {
        val a = parse("""
            import org.apache.log4j.Layout;
            import org.apache.log4j.spi.LoggingEvent;

            public class TrivialLayout extends Layout {

              public void activateOptions() {
                // there are no options to activate
              }

              public String format(LoggingEvent loggingEvent) {
                return loggingEvent.getRenderedMessage();
              }

              public boolean ignoresThrowable() {
                return true;
              }
            }
        """.trimIndent())

        val fixed = a.refactor().visit(Log4jLayoutToLogback()).fix().fixed

        assertRefactored(fixed, """
            import ch.qos.logback.classic.spi.ILoggingEvent;
            import ch.qos.logback.core.LayoutBase;

            public class TrivialLayout extends LayoutBase<ILoggingEvent> {

              public String doLayout(ILoggingEvent loggingEvent) {
                return loggingEvent.getMessage();
              }
            }
        """)
    }

    @Test
    fun layoutMigrationWithLifeCycle() {
        val a = parse("""
            import org.apache.log4j.Layout;
            import org.apache.log4j.spi.LoggingEvent;

            public class TrivialLayout extends Layout {

              public void activateOptions() {
                System.out.println("starting...");
              }

              public String format(LoggingEvent loggingEvent) {
                return loggingEvent.getRenderedMessage();
              }

              public boolean ignoresThrowable() {
                return true;
              }
            }
        """.trimIndent())

        val fixed = a.refactor().visit(Log4jLayoutToLogback()).fix().fixed

        assertRefactored(fixed, """
            import ch.qos.logback.classic.spi.ILoggingEvent;
            import ch.qos.logback.core.LayoutBase;
            import ch.qos.logback.core.spi.LifeCycle;

            public class TrivialLayout extends LayoutBase<ILoggingEvent> implements LifeCycle {

              public void start() {
                System.out.println("starting...");
              }
            
              public String doLayout(ILoggingEvent loggingEvent) {
                return loggingEvent.getMessage();
              }
            }
        """)
    }
}
