/*
 * Copyright 2023 the original author or authors.
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
package org.openrewrite.java.logging.log4j;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.ArrayList;
import java.util.List;

import static org.openrewrite.Tree.randomId;

/**
 * This recipe rewrites JUL's {@link java.util.logging.Logger#entering} method.
 */
@Value
@EqualsAndHashCode(callSuper = false)
public class ConvertJulEntering extends Recipe {

    private static final MethodMatcher METHOD_MATCHER = new MethodMatcher("java.util.logging.Logger entering(String, String, ..)", false);

    @Override
    public String getDisplayName() {
        return "Rewrites JUL's Logger#entering method to Log4j API";
    }

    @Override
    public String getDescription() {
        return "Replaces JUL's Logger#entering method calls to Log4j API Logger#traceEntry calls.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(METHOD_MATCHER), new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                        if (METHOD_MATCHER.matches(m)) {
                            List<Expression> originalArgs = m.getArguments();
                            int originalArgCount = originalArgs.size();
                            if (3 < originalArgCount) {
                                return m;
                            }
                            List<Expression> modifiedArgs = new ArrayList<>();
                            List<JavaType> modifiedTypes = new ArrayList<>();
                            if (2 < originalArgCount) {
                                modifiedArgs.add(buildNullString());
                                modifiedTypes.add(JavaType.Primitive.String);
                                modifiedArgs.add(originalArgs.get(2));
                                modifiedTypes.add(JavaType.buildType("java.lang.Object[]"));
                            }
                            JavaType.Method mt = m.getMethodType().withParameterTypes(modifiedTypes);
                            JavaType.FullyQualified dt = mt.getDeclaringType().withFullyQualifiedName("org.apache.logging.log4j.Logger");
                            return m.withMethodType(mt)
                                    .withName(m.getName().withSimpleName("traceEntry"))
                                    .withArguments(modifiedArgs)
                                    .withDeclaringType(dt);
                        }
                        return m;
                    }

                    private J.Literal buildNullString() {
                        return new J.Literal(randomId(), Space.EMPTY, Markers.EMPTY, null, "null", null, JavaType.Primitive.String);
                    }
                }
        );
    }
}