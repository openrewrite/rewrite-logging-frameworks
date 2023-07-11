package org.openrewrite.java.logging.slf4j;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

public class ChangeLogLevelTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ChangeLogLevel(ChangeLogLevel.Level.INFO, ChangeLogLevel.Level.DEBUG, "LaunchDarkly"))
          .parser(JavaParser.fromJavaVersion().classpath("slf4j-api"));
    }

    @Test
    void basic() {
        rewriteRun(
          //language=java
          java(
            """
                import org.slf4j.Logger;
                import org.slf4j.LoggerFactory;
                
                class Test {
                    private static final Logger log = LoggerFactory.getLogger(Test.class);
                    
                    void test() {
                        log.info("LaunchDarkly Hello");
                    }
                }
            """,
            """
                import org.slf4j.Logger;
                import org.slf4j.LoggerFactory;
                
                class Test {
                    private static final Logger log = LoggerFactory.getLogger(Test.class);
                    
                    void test() {
                        log.debug("LaunchDarkly Hello");
                    }
                }
            """)
        );
    }
}
