package org.openrewrite.java.logging.slf4j;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.java.ChangeMethodName;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import javax.annotation.Nullable;
import java.util.List;

@Value
@EqualsAndHashCode(callSuper = true)
public class ChangeLogLevel extends Recipe {
    @Override
    public String getDisplayName() {
        return "Change slf4j log level";
    }

    @Override
    public String getDescription() {
        return "Change the log level of slf4j log statements.";
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
        MethodMatcher logMatcher = new MethodMatcher(methodPattern);
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                if(!logMatcher.matches(m)) {
                    return m;
                }
                List<Expression> args = m.getArguments();
                if(args.size() == 0) {
                    return m;
                }
                Expression arg = args.get(0);
                if(!(arg instanceof J.Literal)) {
                    return m;
                }
                J.Literal lit = (J.Literal) arg;
                if(lit.getValue() == null) {
                    return m;
                }
                if(!StringUtils.isBlank(startsWith) && !lit.getValue().toString().startsWith(startsWith)) {
                    return m;
                }
                m = (J.MethodInvocation) new ChangeMethodName(methodPattern, to.name().toLowerCase(), true, null)
                        .getVisitor()
                        .visitNonNull(m, ctx);
                return m;
            }
        };
    }
}
