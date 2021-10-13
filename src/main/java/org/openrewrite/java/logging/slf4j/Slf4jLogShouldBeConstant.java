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
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;

public class Slf4jLogShouldBeConstant extends Recipe {
    private static final MethodMatcher SLF4J_LOG = new MethodMatcher("org.slf4j.Logger *(..)");
    private static final MethodMatcher STRING_FORMAT = new MethodMatcher("java.lang.String format(..)");
    private static final MethodMatcher STRING_VALUE_OF = new MethodMatcher("java.lang.String valueOf(..)");

    @Override
    public String getDisplayName() {
        return "SLF4J logging statements should begin with constants.";
    }

    @Override
    public String getDescription() {
        return "Logging statements shouldn't begin with `String#format`, calls to `toString()`, etc.";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new UsesMethod<>(SLF4J_LOG);
    }

    @Override
    protected JavaVisitor<ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                if (SLF4J_LOG.matches(method)) {
                    String name = method.getSimpleName();
                    if (name.equals("trace") || name.equals("debug") || name.equals("info") || name.equals("warn") || name.equals("error")) {
                        List<Expression> args = method.getArguments();
                        if (STRING_FORMAT.matches(args.get(0))) {
                            J.MethodInvocation stringFormat = (J.MethodInvocation) args.get(0);
                            return method.withArguments(ListUtils.map(stringFormat.getArguments(), (n, arg) -> {
                                if (n == 0) {
                                    J.Literal str = (J.Literal) arg;
                                    if (str.getValue() != null && str.getValueSource() != null) {
                                        return str
                                                .withValue(str.getValue().toString().replace("%s", "{}"))
                                                .withValueSource(str.getValueSource().replace("%s", "{}"));
                                    }
                                }
                                return arg;
                            }));
                        } else if (STRING_VALUE_OF.matches(args.get(0))) {
                            Expression valueOf = ((J.MethodInvocation) args.get(0)).getArguments().get(0);
                            if(TypeUtils.isAssignableTo(JavaType.Class.build("java.lang.Throwable"), valueOf.getType())) {
                                J.MethodInvocation m = method.withTemplate(JavaTemplate.builder(this::getCursor, "\"Exception\", #{any()}").build(),
                                        method.getCoordinates().replaceArguments(), valueOf);
                                m = m.withSelect(method.getSelect());
                                return m;
                            }
                        } else if(args.get(0) instanceof J.MethodInvocation && ((J.MethodInvocation) args.get(0)).getSimpleName().equals("toString")) {
                            Expression valueOf = ((J.MethodInvocation) args.get(0)).getSelect();
                            J.MethodInvocation m = method.withTemplate(JavaTemplate.builder(this::getCursor, "\"{}\", #{any()}").build(),
                                    method.getCoordinates().replaceArguments(), valueOf);
                            m = m.withSelect(method.getSelect());
                            return m;
                        }
                    }
                }
                return super.visitMethodInvocation(method, executionContext);
            }
        };
    }
}
