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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;
import static org.openrewrite.Preconditions.or;
import static org.openrewrite.Tree.randomId;

public class WrapExpensiveLogStatementsInConditionals extends Recipe {

    // Only matching up to INFO, as WARN and ERROR are rarely disabled
    private static final MethodMatcher infoMatcher = new MethodMatcher("org.slf4j.Logger info(..)");
    private static final MethodMatcher debugMatcher = new MethodMatcher("org.slf4j.Logger debug(..)");
    private static final MethodMatcher traceMatcher = new MethodMatcher("org.slf4j.Logger trace(..)");

    private static final MethodMatcher isInfoEnabledMatcher = new MethodMatcher("org.slf4j.Logger isInfoEnabled()");
    private static final MethodMatcher isDebugEnabledMatcher = new MethodMatcher("org.slf4j.Logger isDebugEnabled()");
    private static final MethodMatcher isTraceEnabledMatcher = new MethodMatcher("org.slf4j.Logger isTraceEnabled()");

    @Override
    public String getDisplayName() {
        return "Wrap expensive log statements in conditionals";
    }

    @Override
    public String getDescription() {
        return "When trace, debug and info log statements use methods for constructing log messages, " +
                "those methods are called regardless of whether the log level is enabled. " +
                "This recipe encapsulates those log statements in an `if` statement that checks the log level before calling the log method. " +
                "It then bundles surrounding log statements with the same log level into the `if` statement to improve readability of the resulting code.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                or(new UsesMethod<>(infoMatcher), new UsesMethod<>(debugMatcher), new UsesMethod<>(traceMatcher)),
                new AddIfEnabledVisitor());
    }

    private static class AddIfEnabledVisitor extends JavaVisitor<ExecutionContext> {

        final Set<UUID> visitedBlocks = new HashSet<>();

        @Override
        public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            J.MethodInvocation m = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
            if ((infoMatcher.matches(m) || debugMatcher.matches(m) || traceMatcher.matches(m)) &&
                    !isInIfStatementWithLogLevelCheck(getCursor(), m)) {
                List<Expression> arguments = ListUtils.filter(m.getArguments(), a -> a instanceof J.MethodInvocation);
                if (m.getSelect() != null && !arguments.isEmpty()) {
                    J container = getCursor().getParentTreeCursor().getValue();
                    if (container instanceof J.Block) {
                        UUID id = container.getId();
                        J.If if_ = ((J.If) JavaTemplate
                                .builder("if(#{logger:any(org.slf4j.Logger)}.is#{}Enabled()) {}")
                                .javaParser(JavaParser.fromJavaVersion().classpath("slf4j-api-2.1.+"))
                                .build()
                                .apply(getCursor(), m.getCoordinates().replace(),
                                        m.getSelect(), StringUtils.capitalize(m.getSimpleName())))
                                .withThenPart(m.withPrefix(m.getPrefix().withWhitespace("\n" + m.getPrefix().getWhitespace().replace("\n", ""))))
                                .withPrefix(m.getPrefix().withComments(emptyList()));
                        visitedBlocks.add(id);
                        return autoFormat(if_, ctx);
                    }
                }
            }
            return m;
        }

        @Override
        public J visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
            J j = super.visitCompilationUnit(cu, ctx);
            if (j != cu && !visitedBlocks.isEmpty()) {
                doAfterVisit(new MergeLogStatementsInCheck(visitedBlocks));
            }
            return j;
        }

        private boolean isInIfStatementWithLogLevelCheck(Cursor cursor, J.MethodInvocation m) {
            J.If enclosingIf = cursor.firstEnclosing(J.If.class);
            if (enclosingIf == null) {
                return false;
            }
            List<J> sideEffects = enclosingIf.getIfCondition().getSideEffects();
            return (infoMatcher.matches(m) && sideEffects.stream().allMatch(e -> e instanceof J.MethodInvocation && isInfoEnabledMatcher.matches((J.MethodInvocation) e))) ||
                    (debugMatcher.matches(m) && sideEffects.stream().allMatch(e -> e instanceof J.MethodInvocation && isDebugEnabledMatcher.matches((J.MethodInvocation) e))) ||
                    (traceMatcher.matches(m) && sideEffects.stream().allMatch(e -> e instanceof J.MethodInvocation && isTraceEnabledMatcher.matches((J.MethodInvocation) e)));
        }
    }

    @Value
    @EqualsAndHashCode(callSuper = false)
    private static class MergeLogStatementsInCheck extends JavaIsoVisitor<ExecutionContext> {

        Set<UUID> blockIds;

        @Override
        public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
            J.Block b = super.visitBlock(block, ctx);
            if (blockIds.contains(b.getId())) {
                StatementAccumulator acc = new StatementAccumulator();
                for (Statement statement : b.getStatements()) {
                    acc.push(statement);
                }
                return autoFormat(b.withStatements(acc.pull()), ctx);
            }
            return b;
        }
    }

    /**
     * The Statement Accumulator receives statements in a J.Block.
     * It internally keeps track of the kind of statements it's collecting.
     * It differentiates between all different log statements (e.g. INFO is different from DEBUG) and NONE for any statement that isn't a logstatement.
     * <p>
     * Statements that aren't log statements are immediately added to the statements list.
     * <p>
     * While the Accumulator receives the same kind of log statements, or if-statements with only an is<kind>Enabled condition and only containing log statements matching that kind
     * it will cache the statements and the if-statement.
     * <p>
     * When the kind of statement changes, the Accumulator will bundle all cached log statements in the cached if, and add this newly created if to the statements list.
     */
    private static class StatementAccumulator {

        AccumulatorKind accumulatorKind = AccumulatorKind.NONE;
        List<Statement> statements = new ArrayList<>();
        List<Statement> logStatementsCache = new ArrayList<>();
        J.@Nullable If ifCache = null;

        public void push(Statement statement) {
            AccumulatorKind newKind = getKind(statement);
            if (newKind != accumulatorKind && accumulatorKind != AccumulatorKind.NONE) {
                handleLogStatements();
            }
            accumulatorKind = newKind;
            if (statement instanceof J.If) {
                J.If if_ = (J.If) statement;
                if (if_.getThenPart() instanceof J.MethodInvocation &&
                        isInIfStatementWithOnlyLogLevelCheck(if_, (J.MethodInvocation) if_.getThenPart())) {
                    if (newKind != AccumulatorKind.NONE) {
                        if (ifCache == null) {
                            ifCache = if_;
                            logStatementsCache.add(if_.getThenPart());
                        } else {
                            logStatementsCache.add(if_.getThenPart().withPrefix(if_.getThenPart().getPrefix().withWhitespace(if_.getPrefix().getWhitespace())));
                        }
                    } else {
                        statements.add(if_.getThenPart());
                    }
                    return;
                } else if (if_.getThenPart() instanceof J.Block) {
                    if (!((J.Block) if_.getThenPart()).getStatements().isEmpty() &&
                            ((J.Block) if_.getThenPart()).getStatements().stream().allMatch(
                                    s -> s instanceof J.MethodInvocation && isInIfStatementWithOnlyLogLevelCheck(if_, (J.MethodInvocation) s))) {
                        if (newKind != AccumulatorKind.NONE) {
                            ifCache = if_;
                            logStatementsCache.addAll(((J.Block) if_.getThenPart()).getStatements());
                        } else {
                            statements.addAll(((J.Block) if_.getThenPart()).getStatements());
                        }
                        return;
                    }
                }
            } else if (statement instanceof J.MethodInvocation) {
                if (newKind != AccumulatorKind.NONE) {
                    logStatementsCache.add(statement);
                    return;
                }
            }
            statements.add(statement);
        }

        public List<Statement> pull() {
            if (!logStatementsCache.isEmpty()) {
                handleLogStatements();
            }
            return statements;
        }

        private AccumulatorKind getKind(Statement statement) {
            if (statement instanceof J.If) {
                J.If if_ = (J.If) statement;
                if (if_.getThenPart() instanceof J.MethodInvocation &&
                        isInIfStatementWithOnlyLogLevelCheck(if_, (J.MethodInvocation) if_.getThenPart())) {
                    J.MethodInvocation mi = (J.MethodInvocation) if_.getThenPart();
                    return AccumulatorKind.fromMethodInvocation(mi);
                } else if (if_.getThenPart() instanceof J.Block &&
                        !((J.Block) if_.getThenPart()).getStatements().isEmpty() &&
                        ((J.Block) if_.getThenPart()).getStatements().stream().allMatch(
                                s -> s instanceof J.MethodInvocation && isInIfStatementWithOnlyLogLevelCheck(if_, (J.MethodInvocation) s))) {
                    return AccumulatorKind.fromMethodInvocation((J.MethodInvocation) ((J.Block) if_.getThenPart()).getStatements().get(0));
                }
            } else if (statement instanceof J.MethodInvocation) {
                J.MethodInvocation mi = (J.MethodInvocation) statement;
                return AccumulatorKind.fromMethodInvocation(mi);
            }
            return AccumulatorKind.NONE;
        }

        private void handleLogStatements() {
            if (ifCache == null) {
                statements.addAll(logStatementsCache);
            } else {
                statements.add(ifCache.withThenPart(new J.Block(randomId(), Space.EMPTY, Markers.EMPTY, JRightPadded.build(false), logStatementsCache.stream().map(JRightPadded::build).collect(Collectors.toList()), Space.EMPTY)));
            }
            logStatementsCache.clear();
            ifCache = null;
        }

        private boolean isInIfStatementWithOnlyLogLevelCheck(J.If if_, J.MethodInvocation m) {
            J.ControlParentheses<Expression> ifCondition = if_.getIfCondition();
            return ifCondition.getTree() instanceof J.MethodInvocation && (
                    (infoMatcher.matches(m) && isInfoEnabledMatcher.matches(ifCondition.getTree())) ||
                            (debugMatcher.matches(m) && isDebugEnabledMatcher.matches(ifCondition.getTree())) ||
                            (traceMatcher.matches(m) && isTraceEnabledMatcher.matches(ifCondition.getTree())));
        }

        private enum AccumulatorKind {
            NONE,
            INFO,
            DEBUG,
            TRACE;

            public static AccumulatorKind fromMethodInvocation(J.MethodInvocation mi) {
                if (infoMatcher.matches(mi)) {
                    return INFO;
                }
                if (debugMatcher.matches(mi)) {
                    return DEBUG;
                }
                if (traceMatcher.matches(mi)) {
                    return TRACE;
                }
                return NONE;
            }
        }
    }
}
