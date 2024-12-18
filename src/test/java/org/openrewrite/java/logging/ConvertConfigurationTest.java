package org.openrewrite.java.logging;

import static org.openrewrite.test.SourceSpecs.text;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RewriteTest;

class ConvertConfigurationTest implements RewriteTest {

    @DocumentExample
    @Test
    void convertsLog4j1ToLog4j2Configuration() {
        rewriteRun(spec -> spec.recipe(new ConvertConfiguration("file.txt", "v1:properties", "v2:xml")),
                text("""
                                # Console appender
                                log4j.appender.CONSOLE = org.apache.log4j.ConsoleAppender
                                log4j.appender.CONSOLE.Follow = true
                                log4j.appender.CONSOLE.Target = System.err
                                log4j.appender.CONSOLE.layout = org.apache.log4j.PatternLayout
                                log4j.appender.CONSOLE.layout.ConversionPattern = %d [%t] %-5p %c - %m%n%ex
                                # Rolling file appender
                                log4j.appender.ROLLING = org.apache.log4j.RollingFileAppender
                                log4j.appender.ROLLING.File = file.log
                                log4j.appender.ROLLING.MaxBackupIndex = 30
                                # Exactly 10 GiB
                                log4j.appender.ROLLING.MaxFileSize = 10737418240
                                log4j.appender.ROLLING.layout = org.apache.log4j.SimpleLayout
                                
                                # Loggers
                                log4j.rootLogger = INFO, CONSOLE
                                
                                log4j.logger.org.openrewrite = DEBUG, CONSOLE, ROLLING
                                log4j.additivity.org.openrewrite = false
                                """,
                        """
                                <?xml version='1.0' encoding='UTF-8'?>
                                <Configuration xmlns="https://logging.apache.org/xml/ns" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="https://logging.apache.org/xml/ns https://logging.apache.org/xml/ns/log4j-config-2.xsd">
                                  <Properties/>
                                  <Appenders>
                                    <RollingFile append="true" bufferSize="8192" bufferedIo="false" fileName="file.log" filePattern="file.log.%i" immediateFlush="true" name="ROLLING">
                                      <PatternLayout alwaysWriteExceptions="false" pattern="%p - %m%n"/>
                                      <SizeBasedTriggeringPolicy size="10.00 GB"/>
                                      <DefaultRolloverStrategy fileIndex="min" max="30"/>
                                    </RollingFile>
                                    <Console follow="true" immediateFlush="true" name="CONSOLE" target="SYSTEM_ERR">
                                      <PatternLayout pattern="%d [%t] %-5p %c - %m%n%ex"/>
                                    </Console>
                                  </Appenders>
                                  <Loggers>
                                    <Root level="INFO">
                                      <AppenderRef ref="CONSOLE"/>
                                    </Root>
                                    <Logger additivity="false" level="DEBUG" name="org.openrewrite">
                                      <AppenderRef ref="CONSOLE"/>
                                      <AppenderRef ref="ROLLING"/>
                                    </Logger>
                                  </Loggers>
                                </Configuration>
                                """));
    }
}
