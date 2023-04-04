/*
 * Copyright 2022 the original author or authors.
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

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Javadoc;

public class LoggersNamedForEnclosingClass extends Recipe {

    private static final MethodMatcher LOGGERFACTORY_GETLOGGER = new MethodMatcher(
            "org.slf4j.LoggerFactory getLogger(Class)");

    @Override
    public String getDisplayName() {
        return "Loggers should be named for their enclosing classes";
    }

    @Override
    public String getDescription() {
        return "Ensure `LoggerFactory#getLogger(Class)` is called with the enclosing class as argument.";
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(1);
    }

    @Override
    protected UsesType<ExecutionContext> getSingleSourceApplicableTest() {
        return new UsesType<>("org.slf4j.LoggerFactory", null);
    }

    @Override
    public Set<String> getTags() {
        return new HashSet<>(Arrays.asList("RSPEC-3416", "logging", "slf4j"));
    }

    @Override
    protected JavaVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            protected JavadocVisitor<ExecutionContext> getJavadocVisitor() {
                return new JavadocVisitor<ExecutionContext>(this) {
                    @Override
                    public Javadoc visitDocComment(Javadoc.DocComment javadoc, ExecutionContext executionContext) {
                        return javadoc;
                    }
                };
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext p) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, p);
                if (!LOGGERFACTORY_GETLOGGER.matches(mi)) {
                    return mi;
                }

                J.ClassDeclaration firstEnclosingClass = getCursor().firstEnclosing(J.ClassDeclaration.class);
                if (firstEnclosingClass == null) {
                    return mi;
                }

                String enclosingClazzName = firstEnclosingClass.getSimpleName() + ".class";
                if (mi.getArguments().get(0) instanceof J.FieldAccess) {
                    String argumentClazzName = ((J.FieldAccess) mi.getArguments().get(0)).toString();
                    if (enclosingClazzName.equals(argumentClazzName)) {
                        return mi;
                    }
                }

                return mi.withTemplate(JavaTemplate.builder(this::getCursor, "LoggerFactory.getLogger(#{})")
                        .javaParser(() -> JavaParser.fromJavaVersion().classpath("slf4j-api").build())
                        .build(),
                        mi.getCoordinates().replace(),
                        enclosingClazzName);
            }
        };
    }
}
