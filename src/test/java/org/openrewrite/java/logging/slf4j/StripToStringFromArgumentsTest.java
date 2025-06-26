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
package org.openrewrite.java.logging.slf4j;

import org.intellij.lang.annotations.Language;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.List;
import java.util.stream.Stream;

import static org.openrewrite.java.Assertions.java;

class StripToStringFromArgumentsTest implements RewriteTest {
    private static Stream<Arguments> stripToStringFromLogMethodArguments() {
        record TestCase(String originalArgs, String expectedArgs) {
        }
        List<TestCase> cases = List.of(
                new TestCase(
                        "",
                        ""),
                new TestCase(
                        ", o1.toString()",
                        ", o1"),
                new TestCase(
                        ", o1.toString(), o2.toString()",
                        ", o1, o2"),
                new TestCase(
                        ", o1.toString(), o2.toString(), o3.toString()",
                        ", o1, o2, o3"),
                new TestCase(
                        ", o1.toString(), o2, o3.toString(), o1, o3, o1.toString(), o2",
                        ", o1, o2, o3, o1, o3, o1, o2"),
                new TestCase(
                        ", exception.toString()",
                        ", exception.toString()"),
                new TestCase(
                        ", exception.toString(), o1",
                        ", exception, o1"),
                new TestCase(
                        ", o1, exception.toString()",
                        ", o1, exception.toString()")
        );

        return Stream.of("trace", "debug", "info", "warn", "error")
                .flatMap(method -> cases.stream().flatMap(testCase ->
                        Stream.of(
                                Arguments.of(method, "message" + testCase.originalArgs, "message" + testCase.expectedArgs),
                                Arguments.of(method, "marker, message" + testCase.originalArgs, "marker, message" + testCase.expectedArgs)
                        )
                ));
    }

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new StripToStringFromArguments()).parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "slf4j-api-2.1.+"));
    }

    @ParameterizedTest
    @MethodSource
    void stripToStringFromLogMethodArguments(String method, String arguments, String expectedArguments) {
        String testTemplate = """
                  import org.slf4j.Logger;
                  import org.slf4j.Marker;

                  class A {
                    void method(Logger log, Marker marker, String message, Object o1, Object o2, Object o3, Exception exception) {
                        log.%s(%s);
                    }
                  }
                """;
        @Language("java") String before = String.format(testTemplate, method, arguments);
        @Language("java") String after = String.format(testTemplate, method, expectedArguments);

        // Ideally we'd only call `rewriteRun(java(before, after));` but the only way to expect a no-change is to call `rewrite(java(before))`
        if (before.equals(after)) {
            rewriteRun(java(before));
        } else {
            rewriteRun(java(before, after));
        }
    }
}
