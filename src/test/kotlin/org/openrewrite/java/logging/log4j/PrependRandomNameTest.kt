package org.openrewrite.java.logging.log4j

import org.junit.jupiter.api.Test
import org.openrewrite.Recipe
import org.openrewrite.java.JavaParser
import org.openrewrite.java.JavaRecipeTest

class PrependRandomNameTest : JavaRecipeTest {
    override val parser: JavaParser = JavaParser.fromJavaVersion()
        .classpath("log4j")
        .build()

    override val recipe: Recipe
        get() = PrependRandomName(2048)

    @Test
    fun prependRandomName() = assertChanged(
        before = """
            import org.apache.log4j.Logger;

            class Test {
                Logger logger;
                void test() {
                    logger.info("test");
                }
            }
        """,
        after = """
            import org.slf4j.Logger;
            import org.slf4j.LoggerFactory;

            class Test {
                Logger logger;
                void test() {
                    logger.info("<chronic_contribution> test");
                }
            }
        """
    )
}
