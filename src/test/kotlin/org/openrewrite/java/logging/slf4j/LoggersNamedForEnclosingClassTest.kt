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
package org.openrewrite.java.logging.slf4j

import org.junit.jupiter.api.Test
import org.openrewrite.Issue
import org.openrewrite.Recipe
import org.openrewrite.java.JavaParser
import org.openrewrite.java.JavaRecipeTest

@Suppress("RedundantSlf4jDefinition")
class LoggersNamedForEnclosingClassTest : JavaRecipeTest {
    override val parser: JavaParser
        get() = JavaParser.fromJavaVersion().classpath("slf4j-api").build()

    override val recipe: Recipe
        get() = LoggersNamedForEnclosingClass()

    @Issue("https://github.com/openrewrite/rewrite-logging-frameworks/issues/65")
    @Test
    fun shouldRenameLogger() = assertChanged(
        before = """
            import org.slf4j.Logger;
            import org.slf4j.LoggerFactory;
            class WrongClass {}
            class A {
                private final static Logger LOG = LoggerFactory.getLogger(WrongClass.class);
            }
        """,
        after = """
            import org.slf4j.Logger;
            import org.slf4j.LoggerFactory;
            class WrongClass {}
            class A {
                private final static Logger LOG = LoggerFactory.getLogger(A.class);
            }
        """
    )
   @Issue("https://github.com/openrewrite/rewrite-logging-frameworks/issues/69")
    @Test
    fun shouldRenameLoggerFromMethodInvocationToClass() = assertChanged(
        before = """
            import org.slf4j.Logger;
            import org.slf4j.LoggerFactory;
            class WrongClass {}
            class A {
                private Logger logger = LoggerFactory.getLogger(getClass());
            }
        """,
        after = """
            import org.slf4j.Logger;
            import org.slf4j.LoggerFactory;
            class WrongClass {}
            class A {
                private Logger logger = LoggerFactory.getLogger(A.class);
            }
        """
    )

    @Test
    fun shouldNotChangeCorrectLoggername() = assertUnchanged(
        before = """
            import org.slf4j.Logger;
            import org.slf4j.LoggerFactory;
            class A {
                private final static Logger LOG = LoggerFactory.getLogger(A.class);
            }
        """
    )
}
