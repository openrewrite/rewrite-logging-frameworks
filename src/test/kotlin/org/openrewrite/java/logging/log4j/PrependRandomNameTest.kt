/*
 * Copyright 2021 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.logging.log4j

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.openrewrite.Recipe
import org.openrewrite.java.JavaParser
import org.openrewrite.java.JavaRecipeTest

class PrependRandomNameTest : JavaRecipeTest {
    override val parser: JavaParser = JavaParser.fromJavaVersion()
        .logCompilationWarningsAndErrors(true)
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
            import org.apache.log4j.Logger;

            class Test {
                Logger logger;
                void test() {
                    logger.info("<chronic_contribution> test");
                }
            }
        """
    )

    @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
    @Test
    fun checkValidation() {
        var valid = recipe.validate()
        Assertions.assertThat(valid.isValid).isTrue

        valid = PrependRandomName().validate()
        Assertions.assertThat(valid.isValid).isTrue
    }

}
