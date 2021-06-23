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
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;

/**
 * @see <a href="http://www.slf4j.org/migrator.html">SLF4J Migrator</a>
 */
public class JULToSlf4j extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate `java.util.logging` (JUL) to SLF4J";
    }

    @Override
    public String getDescription() {
        return "SLF4J allows the end-user the liberty to choose the underlying logging framework.";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new UsesType<>("java.util.logging.Logger");
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JULToSLF4JVisitor();
    }

    private static class JULToSLF4JVisitor extends JavaIsoVisitor<ExecutionContext> {
    }

}
