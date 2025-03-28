/*
 * Copyright 2025 the original author or authors.
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
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.xml.Assertions.xml;

class ConfigureLoggerLevelTest implements RewriteTest {

    @DocumentExample
    @Test
    void editExistingLogger() {
        rewriteRun(
          spec -> spec.recipe(new ConfigureLoggerLevel("org.springframework", ConfigureLoggerLevel.LogLevel.off)),
          xml(//language=xml
            """
              <configuration>
                  <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
                      <layout class="ch.qos.logback.classic.PatternLayout">
                          <Pattern>
                              %d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n
                          </Pattern>
                      </layout>
                  </appender>

                  <logger name="org.springframework" level="error" additivity="false">
                      <appender-ref ref="STDOUT" />
                  </logger>
              </configuration>
              """,
            //language=xml
            """
              <configuration>
                  <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
                      <layout class="ch.qos.logback.classic.PatternLayout">
                          <Pattern>
                              %d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n
                          </Pattern>
                      </layout>
                  </appender>

                  <logger name="org.springframework" level="off" additivity="false">
                      <appender-ref ref="STDOUT" />
                  </logger>
              </configuration>
              """,
            spec -> spec.path("logback.xml"))
        );
    }

    @Test
    void addNewLogger() {
        rewriteRun(
            spec -> spec.recipe(new ConfigureLoggerLevel("com.example.MyClass", ConfigureLoggerLevel.LogLevel.off)),
          xml(//language=xml
            """
              <configuration>
                  <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
                      <layout class="ch.qos.logback.classic.PatternLayout">
                          <Pattern>
                              %d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n
                          </Pattern>
                      </layout>
                  </appender>

                  <logger name="org.springframework" level="error" additivity="false">
                      <appender-ref ref="STDOUT" />
                  </logger>
              </configuration>
              """,
            //language=xml
            """
              <configuration>
                  <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
                      <layout class="ch.qos.logback.classic.PatternLayout">
                          <Pattern>
                              %d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n
                          </Pattern>
                      </layout>
                  </appender>

                  <logger name="org.springframework" level="error" additivity="false">
                      <appender-ref ref="STDOUT" />
                  </logger>
                  <logger name="com.example.MyClass" level="off"/>
              </configuration>
              """,
            spec -> spec.path("logback.xml"))
        );
    }
}
