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
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.logging.logback.JaninoCondition.Type.*;

class JaninoConditionTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
      `property("k").equals("v")`      | k | v
      `p("k").equals("v")`             | k | v
      `"v".equals(property("k"))`      | k | v
      `"v".equals(p("k"))`             | k | v
      `property ( "k" ) . equals ( "v" )` | k | v
      `property("k").equals("")`       | k |
      """)
    void propertyEqualityCarriesKeyAndValue(String condition, String key, String value) {
        JaninoCondition parsed = JaninoCondition.parse(condition);
        assertThat(parsed).isNotNull();
        assertThat(parsed.getType()).isEqualTo(PROPERTY_EQUALS);
        assertThat(parsed.getKey()).isEqualTo(key);
        assertThat(parsed.getValue()).isEqualTo(value == null ? "" : value);
    }

    @Test
    void isDefinedCarriesKeyOnly() {
        JaninoCondition parsed = JaninoCondition.parse("isDefined( \"k\" )");
        assertThat(parsed).isNotNull();
        assertThat(parsed.getType()).isEqualTo(IS_DEFINED);
        assertThat(parsed.getKey()).isEqualTo("k");
    }

    @Test
    void isNullCarriesKeyOnly() {
        JaninoCondition parsed = JaninoCondition.parse("isNull(\"k\")");
        assertThat(parsed).isNotNull();
        assertThat(parsed.getType()).isEqualTo(IS_NULL);
        assertThat(parsed.getKey()).isEqualTo("k");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
      `property("k").contains("v")`    | `propertyContains("k", "v")`
      `p("k").contains("v")`           | `propertyContains("k", "v")`
      `!isDefined("a")`                | `!isDefined("a")`
      `!!isDefined("a")`               | `!!isDefined("a")`
      `isDefined("a") && isNull("b")`  | `isDefined("a") && isNull("b")`
      `isDefined("a")&&isNull("b")`    | `isDefined("a") && isNull("b")`
      `isDefined("a") || isNull("b")`  | `isDefined("a") || isNull("b")`
      `true`                           | `true`
      `false`                          | `false`
      `isDefined("a") && true`         | `isDefined("a") && true`
      `!false`                         | `!false`
      """)
    void rendersTheExpressionLanguage(String condition, String expression) {
        JaninoCondition parsed = JaninoCondition.parse(condition);
        assertThat(parsed).isNotNull();
        assertThat(parsed.toExpression()).isEqualTo(expression);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
      `isDefined("a") && isDefined("b") && isDefined("c")` | `isDefined("a") && isDefined("b") && isDefined("c")`
      `isDefined("a") || isDefined("b") || isDefined("c")` | `isDefined("a") || isDefined("b") || isDefined("c")`
      `isDefined("a") && (isDefined("b") && isDefined("c"))` | `isDefined("a") && isDefined("b") && isDefined("c")`
      """)
    void flattensRepeatedOperators(String condition, String expression) {
        JaninoCondition parsed = JaninoCondition.parse(condition);
        assertThat(parsed).isNotNull();
        assertThat(parsed.toExpression()).isEqualTo(expression);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
      `(isNull("a") || isNull("b")) && isNull("c")` | `(isNull("a") || isNull("b")) && isNull("c")`
      `isNull("a") && (isNull("b") || isNull("c"))` | `isNull("a") && (isNull("b") || isNull("c"))`
      `!(isNull("a") && isNull("b"))`               | `!(isNull("a") && isNull("b"))`
      `!(isNull("a") || isNull("b"))`               | `!(isNull("a") || isNull("b"))`
      `isNull("a") || (isNull("b") && isNull("c"))` | `isNull("a") || isNull("b") && isNull("c")`
      `(isNull("a"))`                               | `isNull("a")`
      """)
    void parenthesizesOnlyWherePrecedenceRequiresIt(String condition, String expression) {
        JaninoCondition parsed = JaninoCondition.parse(condition);
        assertThat(parsed).isNotNull();
        assertThat(parsed.toExpression()).isEqualTo(expression);
    }

    @ParameterizedTest
    @ValueSource(strings = {
      // Java that no shipped condition evaluates
      "property(\"k\").toLowerCase().contains(\"v\")",
      "property(\"k\").startsWith(\"v\")",
      "property(\"k\").length() != 4",
      "property(\"k\") != null",
      "\"a\".equals(\"b\")",
      "isDefined(\"a\") == true",
      "someFunction(\"a\")",
      "truthy",
      // Escapes cannot be reproduced as XML text
      "property(\"k\").equals(\"c:\\\\logs\")",
      "isDefined(\"a\\\"b\")",
      // Operators the expression language does not have
      "isDefined(\"a\") & isDefined(\"b\")",
      "isDefined(\"a\") | isDefined(\"b\")",
      "isDefined(\"a\") ^ isDefined(\"b\")",
      // Malformed
      "",
      "   ",
      "isDefined(\"a\") &&",
      "&& isDefined(\"a\")",
      "isDefined(\"a\") isDefined(\"b\")",
      "(isDefined(\"a\")",
      "isDefined(\"a\"))",
      "isDefined(a)",
      "isDefined(\"a\"",
      "!",
      "()",
    })
    void rejectsConditionsOutsideTheTranslatableSubset(String condition) {
        assertThat(JaninoCondition.parse(condition)).isNull();
    }
}
