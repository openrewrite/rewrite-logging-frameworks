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
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.*;

import java.util.*;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

public class RemoveUnnecessaryLogLevelGuards extends Recipe {

    private static final Set<String> LOGGER_METHODS = new HashSet<>(Arrays.asList("trace", "debug", "info", "warn", "error"));
    private static final MethodMatcher IS_X_ENABLED = new MethodMatcher("org.slf4j.Logger is*Enabled(..)");
    private static final MethodMatcher GET_MESSAGE_MATCHER = new MethodMatcher("java.lang.Throwable getMessage()");

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
                return visited.withStatements(ListUtils.flatMap(visited.getStatements(), stmt -> {
                    if (stmt instanceof J.If && shouldRemoveGuard((J.If) stmt)) {
                        J.If ifStmt = (J.If) stmt;
                        String ifStatementWhitespace = ifStmt.getPrefix().getWhitespace();
                        String whitespace = ifStatementWhitespace.substring(ifStatementWhitespace.lastIndexOf('\n'));
                        List<Statement> bodyStatements = ListUtils.map(extractStatements(ifStmt.getThenPart()), st -> st.withPrefix(Space.build(whitespace, emptyList())));
                        return ListUtils.mapFirst(bodyStatements, first -> first.withPrefix(ifStmt.getPrefix()));
                    }
                    return stmt;

                }));
            }

            private boolean shouldRemoveGuard(J.If ifStatement) {
                if (ifStatement.getElsePart() == null && IS_X_ENABLED.matches(ifStatement.getIfCondition().getTree())) {
                    List<Statement> statements = extractStatements(ifStatement.getThenPart());
                    for (Statement stmt : statements) {
                        if (!(stmt instanceof J.MethodInvocation) ||
                                !isValidLoggingCall((J.MethodInvocation) stmt)) {
                            return false;
                        }
                    }
                    return true;
                }
                return false;
            }

            private List<Statement> extractStatements(Statement thenPart) {
                return thenPart instanceof J.Block ? ((J.Block) thenPart).getStatements() : singletonList(thenPart);
            }

            private boolean isValidLoggingCall(J.MethodInvocation logCall) {
                if (!LOGGER_METHODS.contains(logCall.getSimpleName()) ||
                        logCall.getSelect() == null ||
                        !TypeUtils.isOfClassType(logCall.getSelect().getType(), "org.slf4j.Logger")) {
                    return false;
                }
                return logCall.getArguments().stream().allMatch(this::isArgumentSafe);
            }

            private boolean isArgumentSafe(Expression argument) {
                if (argument instanceof J.Literal || argument instanceof J.Identifier || argument instanceof J.FieldAccess) {
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

                return GET_MESSAGE_MATCHER.matches(argument);
            }
        });
    }
}
