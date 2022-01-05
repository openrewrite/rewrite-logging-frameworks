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
package org.openrewrite.java.logging.slf4j;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.ChangeMethodName;
import org.openrewrite.java.ChangeMethodTargetToStatic;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.logging.ParameterizedLogging;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;

public class Log4jToSlf4j extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate Log4j 1.x to SLF4J";
    }

    @Override
    public String getDescription() {
        return "Transforms usages of Log4j 1.x to leveraging SLF4J directly. " +
                "Note, this currently does not modify `log4j.properties` files.";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                doAfterVisit(new UsesType<>("org.apache.log4j.LogManager"));
                doAfterVisit(new UsesType<>("org.apache.log4j.Logger"));
                doAfterVisit(new UsesType<>("org.apache.log4j.Category"));
                return cu;
            }
        };
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                J.CompilationUnit c = super.visitCompilationUnit(cu, ctx);
                doAfterVisit(new ChangeMethodTargetToStatic(
                        "org.apache.log4j.LogManager getLogger(..)",
                        "org.slf4j.LoggerFactory",
                        "org.slf4j.Logger",
                        null
                ));
                doAfterVisit(new ChangeMethodTargetToStatic(
                        "org.apache.log4j.Logger getLogger(..)",
                        "org.slf4j.LoggerFactory",
                        "org.slf4j.Logger",
                        null
                ));
                doAfterVisit(new ChangeMethodName(
                        "org.apache.log4j.Category fatal(..)",
                        "error",
                        null
                ));
                doAfterVisit(new ChangeType(
                        "org.apache.log4j.Logger",
                        "org.slf4j.Logger"
                ));
                doAfterVisit(new ChangeType(
                        "org.apache.log4j.Category",
                        "org.slf4j.Logger"
                ));
                doAfterVisit(new ChangeType(
                        "org.apache.log4j.MDC",
                        "org.slf4j.MDC"
                ));
                // refactor as a declarative recipe in order to prevent this parameterized logging chain todo
                doAfterVisit(new ParameterizedLogging("org.slf4j.Logger trace(..)"));
                doAfterVisit(new ParameterizedLogging("org.slf4j.Logger debug(..)"));
                doAfterVisit(new ParameterizedLogging("org.slf4j.Logger info(..)"));
                doAfterVisit(new ParameterizedLogging("org.slf4j.Logger warn(..)"));
                doAfterVisit(new ParameterizedLogging("org.slf4j.Logger error(..)"));
                return c;
            }

        };
    }

}
