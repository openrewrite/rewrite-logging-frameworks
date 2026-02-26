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

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.*;

import java.util.*;
import java.util.stream.IntStream;

public class RemoveUnnecessaryLogLevelGuards extends Recipe {

    private static final Set<String> LOGGER_METHODS = new HashSet<>(Arrays.asList("trace", "debug", "info", "warn", "error"));

    private static final MethodMatcher IS_X_ENABLED = new MethodMatcher("org.slf4j.Logger is*Enabled(..)");

    @Getter
    final String displayName = "Remove unnecessary log level guards";

    @Getter
    final String description = "Remove `if` statement guards around SLF4J logging calls when parameterized logging makes them unnecessary.";

    @Getter
    final Set<String> tags = new HashSet<>(Arrays.asList("logging", "slf4j"));

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(IS_X_ENABLED), new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
                J.Block visited = super.visitBlock(block, ctx);

                List<Statement> newStatements = new ArrayList<>();
                boolean modified = false;

                for (Statement stmt : visited.getStatements()) {
                    if (!(stmt instanceof J.If) || !shouldRemoveGuard((J.If) stmt)) {
                        newStatements.add(stmt);
                    } else {
                        J.If ifStmt = (J.If) stmt;
                        List<Statement> bodyStatements = extractStatements(ifStmt.getThenPart());

                        Space ifIndent = computeIndentationOnly(ifStmt.getPrefix());

                        IntStream.range(0, bodyStatements.size())
                                .mapToObj(i -> (Statement) bodyStatements.get(i).withPrefix(i == 0 ? ifStmt.getPrefix() : ifIndent))
                                .forEach(newStatements::add);
                        modified = true;
                    }
                }

                if (modified) {
                    return visited.withStatements(newStatements);
                }
                return visited;
            }

            private boolean shouldRemoveGuard(J.If ifStatement) {
                if (ifStatement.getElsePart() != null) {
                    return false;
                }

                Expression condition = ifStatement.getIfCondition().getTree();
                if (!isLogLevelGuardCondition(condition)) {
                    return false;
                }

                List<Statement> bodyStatements = extractStatements(ifStatement.getThenPart());
                return allStatementsAreSafeLoggingCalls(bodyStatements);
            }

            private boolean isLogLevelGuardCondition(Expression condition) {
                if (!(condition instanceof J.MethodInvocation)) {
                    return false;
                }
                J.MethodInvocation method = (J.MethodInvocation) condition;

                return IS_X_ENABLED.matches(method);
            }

            private List<Statement> extractStatements(Statement thenPart) {
                if (thenPart instanceof J.Block) {
                    return ((J.Block) thenPart).getStatements();
                }
                return Collections.singletonList(thenPart);
            }

            private Space computeIndentationOnly(Space prefix) {
                String whitespace = prefix.getWhitespace();
                int lastNewline = whitespace.lastIndexOf('\n');
                if (lastNewline >= 0) {
                    return Space.format("\n" + whitespace.substring(lastNewline + 1));
                }
                return Space.format(whitespace);
            }

            private boolean allStatementsAreSafeLoggingCalls(List<Statement> statements) {
                if (statements.isEmpty()) {
                    return false;
                }

                for (Statement stmt : statements) {
                    if (!isSafeLoggingCall(stmt)) {
                        return false;
                    }
                }
                return true;
            }

            private boolean isSafeLoggingCall(Statement stmt) {
                if (!(stmt instanceof J.MethodInvocation)) {
                    return false;
                }
                return isValidLoggingCall((J.MethodInvocation) stmt);
            }

            private boolean isValidLoggingCall(J.MethodInvocation logCall) {
                String methodName = logCall.getSimpleName();
                if (!LOGGER_METHODS.contains(methodName)) {
                    return false;
                }

                if (!isSlf4jLogger(logCall.getSelect())) {
                    return false;
                }

                return logCall.getArguments().stream().allMatch(this::isArgumentSafe);
            }

            private boolean isSlf4jLogger(Expression select) {
                JavaType.FullyQualified type = TypeUtils.asFullyQualified(select.getType());
                return type != null && "org.slf4j.Logger".equals(type.getFullyQualifiedName());
            }

            private boolean isArgumentSafe(Expression argument) {
                if (argument instanceof J.Literal || argument instanceof J.Identifier|| argument instanceof J.FieldAccess) {
                    return true;
                }

                if (argument instanceof J.Binary) {
                    J.Binary binary = (J.Binary) argument;
                    return isArgumentSafe(binary.getLeft()) && isArgumentSafe(binary.getRight());
                }

                if (argument instanceof J.Parentheses) {
                    J.Parentheses<?> parens = (J.Parentheses<?>) argument;
                    if (parens.getTree() instanceof Expression) {
                        return isArgumentSafe((Expression) parens.getTree());
                    }
                    return false;
                }

                if (argument instanceof J.MethodInvocation) {
                    return isSafeMethodInvocation((J.MethodInvocation) argument);
                }

                return false;
            }

            private boolean isSafeMethodInvocation(J.MethodInvocation method) {
                String methodName = method.getSimpleName();

                if (!"getMessage".equals(methodName)) {
                    return false;
                }

                Expression select = method.getSelect();
                if (select == null) {
                    return false;
                }

                JavaType type = select.getType();
                return TypeUtils.isAssignableTo("java.lang.Throwable", type);
            }
        });
    }
}
