package org.openrewrite.java.logging.slf4j;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

import java.util.ArrayList;
import java.util.List;

public class StripToStringFromArguments extends Recipe {
    @Override
    public String getDisplayName() {
        return "Strip `toString()` from arguments";
    }

    @Override
    public String getDescription() {
        return "Remove `.toString()` from logger call arguments; SLF4J will automatically call `toString()` on an argument when not a string, and do so only if the log level is enabled.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.or(
                        new UsesMethod<>("org.slf4j.Logger trace(..)"),
                        new UsesMethod<>("org.slf4j.Logger debug(..)"),
                        new UsesMethod<>("org.slf4j.Logger info(..)"),
                        new UsesMethod<>("org.slf4j.Logger warn(..)"),
                        new UsesMethod<>("org.slf4j.Logger error(..)")
                ),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation mi, ExecutionContext ctx) {
                        int firstFormatArgIndex = TypeUtils.isOfClassType(mi.getArguments().get(0).getType(), "org.slf4j.Marker") ? 2 : 1;
                        List<Expression> newArguments = new ArrayList<>(mi.getArguments().subList(0, firstFormatArgIndex));
                        for (int i = firstFormatArgIndex; i < mi.getArguments().size(); i++) {
                            Expression arg = mi.getArguments().get(i);
                            Expression toAdd = arg;
                            if (arg instanceof J.MethodInvocation) {
                                J.MethodInvocation toStringInvocation = (J.MethodInvocation) arg;
                                if (toStringInvocation.getSimpleName().equals("toString") &&
                                        toStringInvocation.getSelect() != null &&
                                        !TypeUtils.isAssignableTo("java.lang.Throwable", toStringInvocation.getSelect().getType())) {
                                    toAdd = toStringInvocation.getSelect().withPrefix(toStringInvocation.getPrefix());
                                }
                            }
                            newArguments.add(toAdd);
                        }
                        return mi.withArguments(newArguments);
                    }
                });
    }
}
