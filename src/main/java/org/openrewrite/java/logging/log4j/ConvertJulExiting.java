/*
 * Copyright 2020 the original author or authors.
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

import static java.util.Objects.requireNonNull;
import static org.openrewrite.Tree.randomId;

import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import java.util.ArrayList;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.*;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.*;
import org.openrewrite.java.tree.J.Identifier;
import org.openrewrite.java.tree.JavaType.Method;
import org.openrewrite.java.tree.JavaType.Primitive;
import org.openrewrite.marker.Markers;

/**
 * This recipe rewrites JUL's {@link java.util.logging.Logger#entering} method.
 */
@Value
@EqualsAndHashCode(callSuper = true)
public class ConvertJulExiting extends Recipe {

    private static final String METHOD_PATTERN = "java.util.logging.Logger exiting(..)";

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {
    }

    @Override
    public String getDisplayName() {
        return "Rewrites JUL's Logger#exiting method to Log4j API";
    }

    @Override
    public String getDescription() {
        return "Replaces JUL's Logger#exiting method calls to Log4j API Logger#traceEntry calls.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new JavaVisitor<ExecutionContext>() {
            @Override
            public J visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree instanceof JavaSourceFile) {
                    JavaSourceFile cu = (JavaSourceFile) requireNonNull(tree);
                    return new UsesMethod<>(METHOD_PATTERN).visitNonNull(cu, ctx);
                }
                return super.visit(tree, ctx);
            }
        }, new EnteringMethodVisitor(new MethodMatcher(METHOD_PATTERN, false)));
    }

    private class EnteringMethodVisitor extends JavaIsoVisitor<ExecutionContext> {
        private final MethodMatcher methodMatcher;

        private EnteringMethodVisitor(MethodMatcher methodMatcher) {
            this.methodMatcher = methodMatcher;
        }

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            return (J.MethodInvocation) visitMethodCall(super.visitMethodInvocation(method, ctx));
        }

        private J.MethodInvocation visitMethodCall(J.MethodInvocation m) {
            if (methodMatcher.matches(m) && m.getMethodType() != null) {
                final List<JRightPadded<Expression>> originalArgs = m.getPadding()
                        .getArguments()
                        .getPadding()
                        .getElements();
                final List<JavaType> originalTypes = m.getMethodType().getParameterTypes();
                final int originalArgCount = originalArgs.size();
                if (originalArgCount < 2 || 3 < originalArgCount) {
                    throw new IllegalArgumentException("Unsupported Logger#entering method: " + m.getMethodType());
                }
                final List<Expression> modifiedArgs = new ArrayList<>();
                final List<JavaType> modifiedTypes = new ArrayList<>();
                if (originalArgCount > 2) {
                    modifiedArgs.add(originalArgs.get(2).getElement().withPrefix(Space.EMPTY));
                    modifiedTypes.add(originalTypes.get(2));
                }
                final Identifier traceEntry = m.getName().withSimpleName("traceExit");
                final Method mt = m.getMethodType().withParameterTypes(modifiedTypes);
                return m.withMethodType(mt)
                        .withName(traceEntry)
                        .withDeclaringType(mt.getDeclaringType()
                                .withFullyQualifiedName("org.apache.logging.log4j.Logger"))
                        .withArguments(modifiedArgs);
            }
            return m;
        }
    }

    private static J.Literal buildNullString() {
        return new J.Literal(randomId(), Space.EMPTY, Markers.EMPTY, null, "null", null, Primitive.String);
    }
}