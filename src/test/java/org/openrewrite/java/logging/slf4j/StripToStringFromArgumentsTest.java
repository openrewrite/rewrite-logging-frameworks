package org.openrewrite.java.logging.slf4j;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.ArrayList;
import java.util.List;

import static org.openrewrite.java.Assertions.java;

public class StripToStringFromArgumentsTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new StripToStringFromArguments()).parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "slf4j-api-2.1.+"));
    }

    private static List<Arguments> stripToStringFromLogMethodArguments() {
        List<Arguments> arguments = new ArrayList<>();
        for (String marker : List.of("", "marker, ")) {
            for (String method : List.of("trace", "debug", "info", "warn", "error")) {
                arguments.addAll(List.of(
                  Arguments.of(method, marker,
                    List.of("o1.toString()"),
                    List.of("o1")),
                  Arguments.of(method, marker,
                    List.of("o1.toString()", "o2.toString()"),
                    List.of("o1", "o2")),
                  Arguments.of(method, marker,
                    List.of("o1.toString()", "o2.toString()", "o3.toString()"),
                    List.of("o1", "o2", "o3")),
                  Arguments.of(method, marker,
                    List.of("o1.toString()", "o2", "o3.toString()", "o1", "o3", "o1.toString()", "o2"),
                    List.of("o1", "o2", "o3", "o1", "o3", "o1", "o2")),
                  Arguments.of(method, marker,
                    List.of("exception.toString()"),
                    List.of("exception.toString()"))
                ));
            }
        }
        return arguments;
    }

    @ParameterizedTest
    @MethodSource
    void stripToStringFromLogMethodArguments(String method, String marker, List<String> arguments, List<String> expectedArguments) {
        //language=java
        String testTemplate = """
            import org.slf4j.Logger;
            import org.slf4j.Marker;

            class A {
              void method(Logger log, Object o1, Object o2, Object o3, Exception exception, Marker marker) {
                  log.%s(%s"Hello", %s);
              }
            }
          """;
        String before = String.format(testTemplate, method, marker, String.join(", ", arguments));
        String after = String.format(testTemplate, method, marker, String.join(", ", expectedArguments));

        // Ideally we'd only call `rewriteRun(java(before, after));` but the only way to expect a no-change is to call `java(before)`
        if (before.equals(after)) {
            rewriteRun(java(before));
        } else {
            rewriteRun(java(before, after));
        }
    }
}
