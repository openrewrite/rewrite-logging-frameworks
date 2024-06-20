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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.*;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.ChangeMethodName;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.List;

@Value
@EqualsAndHashCode(callSuper = false)
public class ChangeLogLevel extends Recipe {
    @Override
    public String getDisplayName() {
        return "Change SLF4J log level";
    }

    @Override
    public String getDescription() {
        return "Change the log level of SLF4J log statements.";
    }

    @Option(displayName = "From",
            description = "The log level to change from.",
            example = "INFO")
    Level from;

    @Option(displayName = "To",
            description = "The log level to change to.",
            example = "DEBUG")
    Level to;

    @Option(displayName = "Starts with",
            description = "Only change log statements that start with this string. When omitted all log statements of " +
                          "the specified level are changed.",
            example = "LaunchDarkly",
            required = false)
    @Nullable
    String startsWith;

    public enum Level {
        TRACE,
        DEBUG,
        INFO,
        WARN,
        ERROR
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        String methodPattern = "org.slf4j.Logger " + from.name().toLowerCase() + "(..)";
        return Preconditions.check(new UsesMethod<>(methodPattern), new JavaIsoVisitor<ExecutionContext>() {
            final MethodMatcher logMatcher = new MethodMatcher(methodPattern);
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                if (!logMatcher.matches(m)) {
                    return m;
                }
                List<Expression> args = m.getArguments();
                if (args.isEmpty()) {
                    return m;
                }
                J.Literal lit = leftMostLiteral(args.get(0));
                if (lit == null || lit.getValue() == null) {
                    return m;
                }
                if (!StringUtils.isBlank(startsWith) && !lit.getValue().toString().startsWith(startsWith)) {
                    return m;
                }
                m = (J.MethodInvocation) new ChangeMethodName(methodPattern, to.name().toLowerCase(), true, null)
                        .getVisitor()
                        .visitNonNull(m, ctx);
                return m;
            }
        });
    }

    @Nullable
    J.Literal leftMostLiteral(Expression arg) {
        if (arg instanceof J.Literal) {
            return (J.Literal) arg;
        }
        if (arg instanceof J.Binary) {
            return leftMostLiteral(((J.Binary) arg).getLeft());
        }
        return null;
    }
}
