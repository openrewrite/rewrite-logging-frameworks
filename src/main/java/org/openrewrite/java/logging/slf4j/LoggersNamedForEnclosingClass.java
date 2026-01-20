/*
 * Copyright 2024 the original author or authors.
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

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavadocVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Javadoc;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class LoggersNamedForEnclosingClass extends Recipe {

    private static final MethodMatcher LOGGERFACTORY_GETLOGGER = new MethodMatcher(
            "org.slf4j.LoggerFactory getLogger(Class)");

    @Getter
    final String displayName = "Loggers should be named for their enclosing classes";

    @Getter
    final String description = "Ensure `LoggerFactory#getLogger(Class)` is called with the enclosing class as argument.";

    @Getter
    final Duration estimatedEffortPerOccurrence = Duration.ofMinutes(1);

    @Getter
    final Set<String> tags = new HashSet<>(Arrays.asList("RSPEC-S3416", "logging", "slf4j"));

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>("org.slf4j.LoggerFactory", null), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            protected JavadocVisitor<ExecutionContext> getJavadocVisitor() {
                return new JavadocVisitor<ExecutionContext>(this) {
                    @Override
                    public Javadoc visitDocComment(Javadoc.DocComment javadoc, ExecutionContext ctx) {
                        return javadoc;
                    }
                };
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                if (!LOGGERFACTORY_GETLOGGER.matches(mi)) {
                    return mi;
                }

                J.ClassDeclaration firstEnclosingClass = getCursor().firstEnclosing(J.ClassDeclaration.class);
                if (firstEnclosingClass == null) {
                    return mi;
                }

                J.Block block = getCursor().firstEnclosing(J.Block.class);
                if (block != null && block.isStatic()) {
                    return mi;
                }

                String enclosingClazzName = firstEnclosingClass.getSimpleName() + ".class";
                Expression firstArgument = mi.getArguments().get(0);
                if (firstArgument instanceof J.FieldAccess) {
                    String argumentClazzName = ((J.FieldAccess) firstArgument).toString();
                    if (argumentClazzName.endsWith(".class") && !enclosingClazzName.equals(argumentClazzName)) {
                        if (firstArgument.getType() instanceof JavaType.Parameterized) {
                            maybeRemoveImport(((JavaType.Parameterized) firstArgument.getType()).getTypeParameters().get(0).toString());
                        }
                        return replaceMethodArgument(mi, enclosingClazzName);
                    }
                } else if (firstArgument instanceof J.MethodInvocation &&
                           "getClass".equals(((J.MethodInvocation) firstArgument).getName().toString())) {
                    if (firstEnclosingClass.hasModifier(J.Modifier.Type.Final)) {
                        return replaceMethodArgument(mi, enclosingClazzName);
                    }
                }

                return mi;
            }

            private J.MethodInvocation replaceMethodArgument(J.MethodInvocation mi, String enclosingClazzName) {
                return JavaTemplate.builder("#{}").contextSensitive().build()
                        .apply(getCursor(), mi.getCoordinates().replaceArguments(), enclosingClazzName);
            }
        });
    }
}
