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
package org.openrewrite.java.logging.logback;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.logging.logback.table.UnmigratedJaninoConditions;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.xml.Assertions.xml;

class ConditionAttributeToConditionElementTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ConditionAttributeToConditionElement(null));
    }

    @DocumentExample
    @Test
    void propertyEquality() {
        rewriteRun(
          xml(//language=xml
            """
              <configuration>
                  <if condition='property("pretty").equals("false")'>
                      <then>
                          <root level="INFO">
                              <appender-ref ref="STDOUT"/>
                          </root>
                      </then>
                  </if>
              </configuration>
              """,
            //language=xml
            """
              <configuration>
                  <condition class="ch.qos.logback.core.boolex.PropertyEqualityCondition">
                      <key>pretty</key>
                      <value>false</value>
                  </condition>
                  <if>
                      <then>
                          <root level="INFO">
                              <appender-ref ref="STDOUT"/>
                          </root>
                      </then>
                  </if>
              </configuration>
              """,
            spec -> spec.path("logback.xml"))
        );
    }

    @Test
    void propertyEqualityWithLiteralOnTheLeft() {
        rewriteRun(
          xml(//language=xml
            """
              <configuration>
                  <if condition='"torino".equals(p("HOSTNAME"))'>
                      <then/>
                  </if>
              </configuration>
              """,
            //language=xml
            """
              <configuration>
                  <condition class="ch.qos.logback.core.boolex.PropertyEqualityCondition">
                      <key>HOSTNAME</key>
                      <value>torino</value>
                  </condition>
                  <if>
                      <then/>
                  </if>
              </configuration>
              """,
            spec -> spec.path("logback.xml"))
        );
    }

    @Test
    void isDefined() {
        rewriteRun(
          xml(//language=xml
            """
              <configuration>
                  <if condition='isDefined("WMCLOUD_PLATFORM")'>
                      <then/>
                      <else/>
                  </if>
              </configuration>
              """,
            //language=xml
            """
              <configuration>
                  <condition class="ch.qos.logback.core.boolex.IsPropertyDefinedCondition">
                      <key>WMCLOUD_PLATFORM</key>
                  </condition>
                  <if>
                      <then/>
                      <else/>
                  </if>
              </configuration>
              """,
            spec -> spec.path("logback.xml"))
        );
    }

    @Test
    void isNull() {
        rewriteRun(
          xml(//language=xml
            """
              <configuration>
                  <if condition='isNull("environment")'>
                      <then/>
                  </if>
              </configuration>
              """,
            //language=xml
            """
              <configuration>
                  <condition class="ch.qos.logback.core.boolex.IsPropertyNullCondition">
                      <key>environment</key>
                  </condition>
                  <if>
                      <then/>
                  </if>
              </configuration>
              """,
            spec -> spec.path("logback.xml"))
        );
    }

    @Test
    void contains() {
        rewriteRun(
          xml(//language=xml
            """
              <configuration>
                  <if condition='property("WMCLOUD_PLATFORM").contains("mks")'>
                      <then/>
                  </if>
              </configuration>
              """,
            //language=xml
            """
              <configuration>
                  <condition class="ch.qos.logback.core.boolex.ExpressionPropertyCondition">
                      <expression>propertyContains("WMCLOUD_PLATFORM", "mks")</expression>
                  </condition>
                  <if>
                      <then/>
                  </if>
              </configuration>
              """,
            spec -> spec.path("logback.xml"))
        );
    }

    @Test
    void conjunctionEscapesAmpersands() {
        rewriteRun(
          xml(//language=xml
            """
              <configuration>
                  <if condition='isDefined("WMCLOUD_PLATFORM") &amp;&amp; property("WMCLOUD_PLATFORM").contains("mks")'>
                      <then/>
                  </if>
              </configuration>
              """,
            //language=xml
            """
              <configuration>
                  <condition class="ch.qos.logback.core.boolex.ExpressionPropertyCondition">
                      <expression>isDefined("WMCLOUD_PLATFORM") &amp;&amp; propertyContains("WMCLOUD_PLATFORM", "mks")</expression>
                  </condition>
                  <if>
                      <then/>
                  </if>
              </configuration>
              """,
            spec -> spec.path("logback.xml"))
        );
    }

    @Test
    void negationAndDisjunction() {
        rewriteRun(
          xml(//language=xml
            """
              <configuration>
                  <if condition='isDefined("environment") || !property("HOSTNAME").equals("192.168.1.2")'>
                      <then/>
                  </if>
              </configuration>
              """,
            //language=xml
            """
              <configuration>
                  <condition class="ch.qos.logback.core.boolex.ExpressionPropertyCondition">
                      <expression>isDefined("environment") || !propertyEquals("HOSTNAME", "192.168.1.2")</expression>
                  </condition>
                  <if>
                      <then/>
                  </if>
              </configuration>
              """,
            spec -> spec.path("logback.xml"))
        );
    }

    @Test
    void retainsParenthesesRequiredByPrecedence() {
        rewriteRun(
          xml(//language=xml
            """
              <configuration>
                  <if condition='(isNull("a") || isNull("b")) &amp;&amp; !(isDefined("c") &amp;&amp; isDefined("d"))'>
                      <then/>
                  </if>
              </configuration>
              """,
            //language=xml
            """
              <configuration>
                  <condition class="ch.qos.logback.core.boolex.ExpressionPropertyCondition">
                      <expression>(isNull("a") || isNull("b")) &amp;&amp; !(isDefined("c") &amp;&amp; isDefined("d"))</expression>
                  </condition>
                  <if>
                      <then/>
                  </if>
              </configuration>
              """,
            spec -> spec.path("logback.xml"))
        );
    }

    @Test
    void dropsParenthesesThatPrecedenceMakesRedundant() {
        rewriteRun(
          xml(//language=xml
            """
              <configuration>
                  <if condition='isNull("a") || (isDefined("b") &amp;&amp; isDefined("c"))'>
                      <then/>
                  </if>
              </configuration>
              """,
            //language=xml
            """
              <configuration>
                  <condition class="ch.qos.logback.core.boolex.ExpressionPropertyCondition">
                      <expression>isNull("a") || isDefined("b") &amp;&amp; isDefined("c")</expression>
                  </condition>
                  <if>
                      <then/>
                  </if>
              </configuration>
              """,
            spec -> spec.path("logback.xml"))
        );
    }

    @Test
    void nestedConditionals() {
        rewriteRun(
          xml(//language=xml
            """
              <configuration>
                  <if condition='isDefined("env")'>
                      <then>
                          <if condition='property("env").equals("prod")'>
                              <then>
                                  <root level="WARN"/>
                              </then>
                          </if>
                      </then>
                  </if>
              </configuration>
              """,
            //language=xml
            """
              <configuration>
                  <condition class="ch.qos.logback.core.boolex.IsPropertyDefinedCondition">
                      <key>env</key>
                  </condition>
                  <if>
                      <then>
                          <condition class="ch.qos.logback.core.boolex.PropertyEqualityCondition">
                              <key>env</key>
                              <value>prod</value>
                          </condition>
                          <if>
                              <then>
                                  <root level="WARN"/>
                              </then>
                          </if>
                      </then>
                  </if>
              </configuration>
              """,
            spec -> spec.path("logback.xml"))
        );
    }

    @Test
    void doubleQuotedAttribute() {
        rewriteRun(
          xml(//language=xml
            """
              <configuration>
                  <if condition="isDefined(&quot;env&quot;)">
                      <then/>
                  </if>
              </configuration>
              """,
            //language=xml
            """
              <configuration>
                  <condition class="ch.qos.logback.core.boolex.IsPropertyDefinedCondition">
                      <key>env</key>
                  </condition>
                  <if>
                      <then/>
                  </if>
              </configuration>
              """,
            spec -> spec.path("logback.xml"))
        );
    }

    @Test
    void migratesEveryConditionalInTheFile() {
        rewriteRun(
          xml(//language=xml
            """
              <configuration>
                  <if condition='isDefined("a")'>
                      <then/>
                  </if>
                  <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender"/>
                  <if condition='isNull("b")'>
                      <then/>
                  </if>
              </configuration>
              """,
            //language=xml
            """
              <configuration>
                  <condition class="ch.qos.logback.core.boolex.IsPropertyDefinedCondition">
                      <key>a</key>
                  </condition>
                  <if>
                      <then/>
                  </if>
                  <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender"/>
                  <condition class="ch.qos.logback.core.boolex.IsPropertyNullCondition">
                      <key>b</key>
                  </condition>
                  <if>
                      <then/>
                  </if>
              </configuration>
              """,
            spec -> spec.path("logback.xml"))
        );
    }

    @Test
    void leavesConditionsRequiringCustomJavaUnchanged() {
        rewriteRun(
          spec -> spec.dataTable(UnmigratedJaninoConditions.Row.class, rows ->
            assertThat(rows).containsExactly(new UnmigratedJaninoConditions.Row(
              "logback.xml", "property(\"APP_NAME\").toLowerCase().contains(\"ivp\")"))),
          xml(//language=xml
            """
              <configuration>
                  <if condition='property("APP_NAME").toLowerCase().contains("ivp")'>
                      <then/>
                  </if>
              </configuration>
              """,
            spec -> spec.path("logback.xml"))
        );
    }

    @Test
    void migratesAnInnerConditionalNestedInAnUnmigratableOne() {
        rewriteRun(
          spec -> spec.dataTable(UnmigratedJaninoConditions.Row.class, rows ->
            assertThat(rows).containsExactly(new UnmigratedJaninoConditions.Row(
              "logback.xml", "property(\"APP_NAME\").toLowerCase().contains(\"ivp\")"))),
          xml(//language=xml
            """
              <configuration>
                  <if condition='property("APP_NAME").toLowerCase().contains("ivp")'>
                      <then>
                          <if condition='isDefined("env")'>
                              <then/>
                          </if>
                      </then>
                  </if>
              </configuration>
              """,
            //language=xml
            """
              <configuration>
                  <if condition='property("APP_NAME").toLowerCase().contains("ivp")'>
                      <then>
                          <condition class="ch.qos.logback.core.boolex.IsPropertyDefinedCondition">
                              <key>env</key>
                          </condition>
                          <if>
                              <then/>
                          </if>
                      </then>
                  </if>
              </configuration>
              """,
            spec -> spec.path("logback.xml"))
        );
    }

    @Test
    void leavesNumericComparisonsUnchanged() {
        rewriteRun(
          xml(//language=xml
            """
              <configuration>
                  <if condition='property("port").length() != 4'>
                      <then/>
                  </if>
              </configuration>
              """,
            spec -> spec.path("logback.xml"))
        );
    }

    @Test
    void leavesEscapedStringLiteralsUnchanged() {
        rewriteRun(
          xml(//language=xml
            """
              <configuration>
                  <if condition='property("path").equals("c:\\\\logs")'>
                      <then/>
                  </if>
              </configuration>
              """,
            spec -> spec.path("logback.xml"))
        );
    }

    @Test
    void leavesAlreadyMigratedConfigurationUnchanged() {
        rewriteRun(
          xml(//language=xml
            """
              <configuration>
                  <condition class="ch.qos.logback.core.boolex.IsPropertyDefinedCondition">
                      <key>env</key>
                  </condition>
                  <if>
                      <then/>
                  </if>
              </configuration>
              """,
            spec -> spec.path("logback.xml"))
        );
    }

    @Test
    void leavesIfPrecededByAConditionElementUnchanged() {
        rewriteRun(
          xml(//language=xml
            """
              <configuration>
                  <condition class="com.example.CustomCondition"/>
                  <if condition='isDefined("env")'>
                      <then/>
                  </if>
              </configuration>
              """,
            spec -> spec.path("logback.xml"))
        );
    }

    @Test
    void leavesOtherXmlFilesUnchanged() {
        rewriteRun(
          xml(//language=xml
            """
              <configuration>
                  <if condition='isDefined("env")'>
                      <then/>
                  </if>
              </configuration>
              """,
            spec -> spec.path("pom.xml"))
        );
    }

    @Test
    void leavesNonLogbackConfigurationUnchanged() {
        rewriteRun(
          xml(//language=xml
            """
              <beans>
                  <if condition='isDefined("env")'>
                      <then/>
                  </if>
              </beans>
              """,
            spec -> spec.path("logback-extras.xml"))
        );
    }

    @Test
    void numericCharacterReferencesInTheAttribute() {
        rewriteRun(
          xml(//language=xml
            """
              <configuration>
                  <if condition="isDefined(&#34;env&#34;)">
                      <then/>
                  </if>
              </configuration>
              """,
            //language=xml
            """
              <configuration>
                  <condition class="ch.qos.logback.core.boolex.IsPropertyDefinedCondition">
                      <key>env</key>
                  </condition>
                  <if>
                      <then/>
                  </if>
              </configuration>
              """,
            spec -> spec.path("logback.xml"))
        );
    }

    @Test
    void booleanLiteral() {
        rewriteRun(
          xml(//language=xml
            """
              <configuration>
                  <if condition='true'>
                      <then/>
                  </if>
              </configuration>
              """,
            //language=xml
            """
              <configuration>
                  <condition class="ch.qos.logback.core.boolex.ExpressionPropertyCondition">
                      <expression>true</expression>
                  </condition>
                  <if>
                      <then/>
                  </if>
              </configuration>
              """,
            spec -> spec.path("logback.xml"))
        );
    }

    @Test
    void conditionalNestedInsideAnotherElement() {
        rewriteRun(
          xml(//language=xml
            """
              <configuration>
                  <root level="INFO">
                      <if condition='property("pretty").equals("false")'>
                          <then>
                              <appender-ref ref="STDOUT"/>
                          </then>
                      </if>
                  </root>
              </configuration>
              """,
            //language=xml
            """
              <configuration>
                  <root level="INFO">
                      <condition class="ch.qos.logback.core.boolex.PropertyEqualityCondition">
                          <key>pretty</key>
                          <value>false</value>
                      </condition>
                      <if>
                          <then>
                              <appender-ref ref="STDOUT"/>
                          </then>
                      </if>
                  </root>
              </configuration>
              """,
            spec -> spec.path("logback.xml"))
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
      "logback.xml",
      "logback-test.xml",
      "logback-spring.xml",
      "logback-access.xml",
      "src/main/resources/logback.xml"
    })
    void migratesTheConventionalLogbackFileNames(String path) {
        rewriteRun(
          xml(//language=xml
            """
              <configuration>
                  <if condition='isNull("environment")'>
                      <then/>
                  </if>
              </configuration>
              """,
            //language=xml
            """
              <configuration>
                  <condition class="ch.qos.logback.core.boolex.IsPropertyNullCondition">
                      <key>environment</key>
                  </condition>
                  <if>
                      <then/>
                  </if>
              </configuration>
              """,
            spec -> spec.path(path))
        );
    }

    @Test
    void escapesKeysAndValuesInTheGeneratedElement() {
        rewriteRun(
          xml(//language=xml
            """
              <configuration>
                  <if condition='property("a&amp;b").equals("x&lt;y")'>
                      <then/>
                  </if>
              </configuration>
              """,
            //language=xml
            """
              <configuration>
                  <condition class="ch.qos.logback.core.boolex.PropertyEqualityCondition">
                      <key>a&amp;b</key>
                      <value>x&lt;y</value>
                  </condition>
                  <if>
                      <then/>
                  </if>
              </configuration>
              """,
            spec -> spec.path("logback.xml"))
        );
    }

    @Test
    void keepsTheBlankLineAheadOfTheConditionalBlock() {
        rewriteRun(
          xml(//language=xml
            """
              <configuration>
                  <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender"/>

                  <if condition='isDefined("env")'>
                      <then/>
                  </if>
              </configuration>
              """,
            //language=xml
            """
              <configuration>
                  <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender"/>

                  <condition class="ch.qos.logback.core.boolex.IsPropertyDefinedCondition">
                      <key>env</key>
                  </condition>
                  <if>
                      <then/>
                  </if>
              </configuration>
              """,
            spec -> spec.path("logback.xml"))
        );
    }

    @Test
    void matchesTheSurroundingIndentation() {
        rewriteRun(
          xml(//language=xml
            """
              <configuration>
                <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender"/>
                <if condition='property("env").equals("prod")'>
                  <then>
                    <root level="WARN">
                      <appender-ref ref="STDOUT"/>
                    </root>
                  </then>
                  <else>
                    <root level="DEBUG">
                      <appender-ref ref="STDOUT"/>
                    </root>
                  </else>
                </if>
              </configuration>
              """,
            //language=xml
            """
              <configuration>
                <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender"/>
                <condition class="ch.qos.logback.core.boolex.PropertyEqualityCondition">
                  <key>env</key>
                  <value>prod</value>
                </condition>
                <if>
                  <then>
                    <root level="WARN">
                      <appender-ref ref="STDOUT"/>
                    </root>
                  </then>
                  <else>
                    <root level="DEBUG">
                      <appender-ref ref="STDOUT"/>
                    </root>
                  </else>
                </if>
              </configuration>
              """,
            spec -> spec.path("logback-spring.xml"))
        );
    }

    @Test
    void honorsFilePattern() {
        rewriteRun(
          spec -> spec.recipe(new ConditionAttributeToConditionElement("**/logging/*.xml")),
          xml(//language=xml
            """
              <configuration>
                  <if condition='isNull("environment")'>
                      <then/>
                  </if>
              </configuration>
              """,
            //language=xml
            """
              <configuration>
                  <condition class="ch.qos.logback.core.boolex.IsPropertyNullCondition">
                      <key>environment</key>
                  </condition>
                  <if>
                      <then/>
                  </if>
              </configuration>
              """,
            spec -> spec.path("src/main/resources/logging/config.xml"))
        );
    }
}
