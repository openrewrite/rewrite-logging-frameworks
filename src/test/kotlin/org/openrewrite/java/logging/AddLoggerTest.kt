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
package org.openrewrite.java.logging

import org.junit.jupiter.api.Test
import org.openrewrite.ExecutionContext
import org.openrewrite.Recipe
import org.openrewrite.TreeVisitor
import org.openrewrite.java.JavaIsoVisitor
import org.openrewrite.java.JavaParser
import org.openrewrite.java.JavaRecipeTest
import org.openrewrite.java.tree.J

class AddLoggerTest : JavaRecipeTest {
    override val parser: JavaParser
        get() = JavaParser.fromJavaVersion()
            .classpath("slf4j-api")
            .build()

    @Test
    fun addLogger() = assertChanged(
        recipe = toRecipe { AddLogger.addSlf4jLogger("LOGGER") },
        before = """
            package test;
            class Test {
            }
        """,
        after = """
            package test;
            
            import org.slf4j.Logger;
            import org.slf4j.LoggerFactory;
            
            class Test {
                private static final Logger LOGGER = LoggerFactory.getLogger(Test.class);
            }
        """,
        cycles = 1, // because we aren't conditionally adding the logger in this test
        expectedCyclesThatMakeChanges = 1
    )

    @Test
    fun onlyOne() = assertChanged(
        recipe = object : Recipe() {
            override fun getDisplayName(): String = "Only one"

            override fun getVisitor(): TreeVisitor<*, ExecutionContext> = object: JavaIsoVisitor<ExecutionContext>() {
                override fun visitClassDeclaration(classDecl: J.ClassDeclaration, p: ExecutionContext): J.ClassDeclaration {
                    doAfterVisit(AddLogger.addSlf4jLogger("LOGGER"))
                    doAfterVisit(AddLogger.addSlf4jLogger("LOGGER"))
                    return super.visitClassDeclaration(classDecl, p)
                }
            }
        },
        before = """
            package test;
            class Test {
            }
        """,
        after = """
            package test;
            
            import org.slf4j.Logger;
            import org.slf4j.LoggerFactory;
            
            class Test {
                private static final Logger LOGGER = LoggerFactory.getLogger(Test.class);
            }
        """,
        cycles = 1, // because we aren't conditionally adding the logger in this test
        expectedCyclesThatMakeChanges = 1
    )

    @Test
    fun notIfExistingLogger() = assertUnchanged(
        recipe = MaybeAddLoggerToClass("Test"),
        before = """
            package test;
            
            import org.slf4j.Logger;
            import org.slf4j.LoggerFactory;
            
            class Test {
                private static final Logger LOGGER = LoggerFactory.getLogger(Inner.class);
            }
        """
    )

    @Test
    fun notIfExistingInheritedLogger() = assertUnchanged(
        recipe = MaybeAddLoggerToClass("Test"),
        dependsOn = arrayOf(
            """
                import org.slf4j.Logger;
                import org.slf4j.LoggerFactory;
                
                class Base {
                    protected static final Logger LOGGER = LoggerFactory.getLogger(Inner.class);
                }
            """
        ),
        before = """
            class Test extends Base {
            }
        """
    )

    class MaybeAddLoggerToClass(val simpleName: String) : Recipe() {
        override fun getDisplayName() = "Add logger to class"

        override fun getVisitor() = object : JavaIsoVisitor<ExecutionContext>() {
            override fun visitClassDeclaration(classDecl: J.ClassDeclaration, p: ExecutionContext): J.ClassDeclaration {
                if (classDecl.simpleName == simpleName) {
                    doAfterVisit(AddLogger.addSlf4jLogger("LOGGER"))
                }
                return super.visitClassDeclaration(classDecl, p)
            }
        }
    }
}
