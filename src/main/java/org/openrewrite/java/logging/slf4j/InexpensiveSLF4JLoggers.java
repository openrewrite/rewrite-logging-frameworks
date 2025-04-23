/*
 * Copyright 2025 the original author or authors.
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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.List;

public class InexpensiveSLF4JLoggers extends Recipe {

    static final MethodMatcher infoMethodMatcher = new MethodMatcher("org.slf4j.Logger info(..)");
    static final MethodMatcher debugMethodMatcher = new MethodMatcher("org.slf4j.Logger debug(..)");
    static final MethodMatcher traceMethodMatcher = new MethodMatcher("org.slf4j.Logger trace(..)");
    static final MethodMatcher errorMethodMatcher = new MethodMatcher("org.slf4j.Logger error(..)");
    static final MethodMatcher warnMethodMatcher = new MethodMatcher("org.slf4j.Logger warn(..)");
    static final JavaTemplate ifEnabledThenLog = JavaTemplate
          .builder("if(#{logger:any(org.slf4j.Logger)}.is#{}Enabled()) {}")
          .imports("org.slf4j.Logger")
          .javaParser(JavaParser.fromJavaVersion()
                .classpath("slf4j-api-2.1.+"))
          .build();

    @Override
    public String getDisplayName() {
        return "Inexpensive SLF4J loggers";
    }

    @Override
    public String getDescription() {
        return ".";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(debugMethodMatcher), new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
                if (debugMethodMatcher.matches(m) && !(getCursor().getParentTreeCursor().getValue() instanceof J.If)) {
                    List<Expression> arguments = ListUtils.filter(m.getArguments(), a -> !(a instanceof J.Literal));
                    if(m.getSelect() != null && !arguments.isEmpty()) {
                        J.If if_ = ifEnabledThenLog.apply(getCursor(), m.getCoordinates().replace(),
                                m.getSelect(), capitalizeFirstLetter(m.getSimpleName()));
                        return if_.withThenPart(m);
                    }
                }
                return m;
            }
        });
    }

    private String capitalizeFirstLetter(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
