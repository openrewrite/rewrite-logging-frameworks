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
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.*;
import java.util.stream.Collectors;

import static org.openrewrite.Preconditions.or;
import static org.openrewrite.Tree.randomId;

public class InexpensiveSLF4JLoggers extends Recipe {

    static final MethodMatcher infoMethodMatcher = new MethodMatcher("org.slf4j.Logger info(..)");
    static final MethodMatcher debugMethodMatcher = new MethodMatcher("org.slf4j.Logger debug(..)");
    static final MethodMatcher traceMethodMatcher = new MethodMatcher("org.slf4j.Logger trace(..)");
    static final MethodMatcher errorMethodMatcher = new MethodMatcher("org.slf4j.Logger error(..)");
    static final MethodMatcher warnMethodMatcher = new MethodMatcher("org.slf4j.Logger warn(..)");

    static final MethodMatcher isInfoEnabledMethodMatcher = new MethodMatcher("org.slf4j.Logger isInfoEnabled()");
    static final MethodMatcher isDebugEnabledMethodMatcher = new MethodMatcher("org.slf4j.Logger isDebugEnabled()");
    static final MethodMatcher isTraceEnabledMethodMatcher = new MethodMatcher("org.slf4j.Logger isTraceEnabled()");
    static final MethodMatcher isErrorEnabledMethodMatcher = new MethodMatcher("org.slf4j.Logger isErrorEnabled()");
    static final MethodMatcher isWarnEnabledMethodMatcher = new MethodMatcher("org.slf4j.Logger isWarnEnabled()");

    @Override
    public String getDisplayName() {
        return "Inexpensive SLF4J loggers";
    }

    @Override
    public String getDescription() {
        return "When log statements use methods for constructing log messages those methods are called regardless of whether the log level is enabled. " +
              "This recipe encapsulates those log statements in an `if` statement that checks the log level before calling the log method. " +
              "It then bundles surrounding log statements with the same log level into the `if` statement to improve readability of the resulting code.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {

        Set<UUID> visitedBlocks = new HashSet<>();

        return Preconditions.check(or(new UsesMethod<>(infoMethodMatcher), new UsesMethod<>(debugMethodMatcher), new UsesMethod<>(traceMethodMatcher), new UsesMethod<>(errorMethodMatcher), new UsesMethod<>(warnMethodMatcher)), new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
                if (!isInIfStatementWithLogLevelCheck(getCursor(), m) && (
                      infoMethodMatcher.matches(m) ||
                            debugMethodMatcher.matches(m) ||
                            traceMethodMatcher.matches(m) ||
                            errorMethodMatcher.matches(m) ||
                            warnMethodMatcher.matches(m)
                )) {
                    List<Expression> arguments = ListUtils.filter(m.getArguments(), a -> a instanceof J.MethodInvocation);
                    if (m.getSelect() != null && !arguments.isEmpty()) {
                        UUID id = ((J.Block) getCursor().getParentTreeCursor().getValue()).getId();
                        J.If if_ = ((J.If) JavaTemplate
                              .builder("if(#{logger:any(org.slf4j.Logger)}.is#{}Enabled()) {}")
                              .javaParser(JavaParser.fromJavaVersion()
                                    .classpath("slf4j-api-2.1.+"))
                              .build()
                              .apply(getCursor(), m.getCoordinates().replace(),
                                    m.getSelect(), capitalizeFirstLetter(m.getSimpleName()))).withThenPart(m);
                        visitedBlocks.add(id);
                        return autoFormat(if_, ctx);
                    }
                }
                return m;
            }

            @Override
            public J visitCompilationUnit(J.CompilationUnit cu, ExecutionContext executionContext) {
                doAfterVisit(new MergeLogStatementsInCheck(visitedBlocks));
                return super.visitCompilationUnit(cu, executionContext);
            }
        });
    }

    private boolean isInIfStatementWithLogLevelCheck(Cursor cursor, J.MethodInvocation m) {
        try {
            Cursor enclosingIf = cursor.dropParentUntil(elem -> elem instanceof J.If);
            return isInIfStatementWithLogLevelCheck((J.If) enclosingIf.getValue(), m);
        } catch (Exception ignore) {
            return false;
        }
    }

    private boolean isInIfStatementWithLogLevelCheck(J.If if_, J.MethodInvocation m) {
        J.ControlParentheses<Expression> ifCondition = if_.getIfCondition();
        if ((infoMethodMatcher.matches(m) && ifCondition.getSideEffects().stream().allMatch(e -> e instanceof J.MethodInvocation && isInfoEnabledMethodMatcher.matches((J.MethodInvocation) e))) ||
              (debugMethodMatcher.matches(m) && ifCondition.getSideEffects().stream().allMatch(e -> e instanceof J.MethodInvocation && isDebugEnabledMethodMatcher.matches((J.MethodInvocation) e))) ||
              (traceMethodMatcher.matches(m) && ifCondition.getSideEffects().stream().allMatch(e -> e instanceof J.MethodInvocation && isTraceEnabledMethodMatcher.matches((J.MethodInvocation) e))) ||
              (errorMethodMatcher.matches(m) && ifCondition.getSideEffects().stream().allMatch(e -> e instanceof J.MethodInvocation && isErrorEnabledMethodMatcher.matches((J.MethodInvocation) e))) ||
              (warnMethodMatcher.matches(m) && ifCondition.getSideEffects().stream().allMatch(e -> e instanceof J.MethodInvocation && isWarnEnabledMethodMatcher.matches((J.MethodInvocation) e)))) {
            return true;
        }
        return false;
    }

    private String capitalizeFirstLetter(String str) {
        if (StringUtils.isNullOrEmpty(str)) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    @Value
    @EqualsAndHashCode(callSuper = false)
    private static class MergeLogStatementsInCheck extends JavaIsoVisitor<ExecutionContext> {

        Set<UUID> blockIds;

        @Override
        public boolean isAcceptable(SourceFile sourceFile, ExecutionContext executionContext) {
            if (blockIds.isEmpty()) {
                return false;
            }
            return super.isAcceptable(sourceFile, executionContext);
        }

        @Override
        public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
            J.Block b = super.visitBlock(block, ctx);
            if (blockIds.contains(b.getId())) {
                StatementAccumulator acc = new StatementAccumulator();
                for (Statement statement : block.getStatements()) {
                    acc.push(statement);
                }
                return autoFormat(b.withStatements(acc.pull()), ctx);
            }
            return b;
        }
    }

    private static class StatementAccumulator {

        AccumulatorKind accumulatorKind = AccumulatorKind.NONE;
        List<Statement> statements = new ArrayList<>();
        List<Statement> logStatementsCache = new ArrayList<>();
        J.If ifCache = null;

        public void push(Statement statement) {
            AccumulatorKind newKind = getKind(statement);
            if (newKind != accumulatorKind && accumulatorKind != AccumulatorKind.NONE) {
                handleLogStatements();
            }
            accumulatorKind = newKind;
            if (statement instanceof J.If) {
                J.If if_ = (J.If) statement;
                if (if_.getThenPart() instanceof J.MethodInvocation) {
                    if (newKind != AccumulatorKind.NONE) {
                        logStatementsCache.add(if_.getThenPart());
                        ifCache = if_;
                    } else {
                        statements.add(if_.getThenPart());
                    }
                } else if (if_.getThenPart() instanceof J.Block) {
                    if (!((J.Block) if_.getThenPart()).getStatements().isEmpty() &&
                          ((J.Block) if_.getThenPart()).getStatements().stream().allMatch(
                                s -> s instanceof J.MethodInvocation && isInIfStatementWithOnlyLogLevelCheck(if_, (J.MethodInvocation) s))) {
                        if (newKind != AccumulatorKind.NONE) {
                            logStatementsCache.addAll(((J.Block) if_.getThenPart()).getStatements());
                            ifCache = if_;
                        } else {
                            statements.addAll(((J.Block) if_.getThenPart()).getStatements());
                        }
                    }
                }
            } else if (statement instanceof J.MethodInvocation) {
                if (newKind != AccumulatorKind.NONE) {
                    logStatementsCache.add(statement);
                } else {
                    statements.add(statement);
                }
            } else {
                statements.add(statement);
            }
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
                if (if_.getThenPart() instanceof J.MethodInvocation) {
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
            statements.add(ifCache.withThenPart(new J.Block(randomId(), Space.EMPTY, Markers.EMPTY, JRightPadded.build(false), logStatementsCache.stream().map(JRightPadded::build).collect(Collectors.toList()), Space.EMPTY)));
            logStatementsCache.clear();
            ifCache = null;
        }

        private boolean isInIfStatementWithOnlyLogLevelCheck(J.If if_, J.MethodInvocation m) {
            J.ControlParentheses<Expression> ifCondition = if_.getIfCondition();
            if (ifCondition.getTree() instanceof J.MethodInvocation && (
                  (infoMethodMatcher.matches(m) && isInfoEnabledMethodMatcher.matches(ifCondition.getTree())) ||
                        (debugMethodMatcher.matches(m) && isDebugEnabledMethodMatcher.matches(ifCondition.getTree())) ||
                        (traceMethodMatcher.matches(m) && isTraceEnabledMethodMatcher.matches(ifCondition.getTree())) ||
                        (errorMethodMatcher.matches(m) && isErrorEnabledMethodMatcher.matches(ifCondition.getTree())) ||
                        (warnMethodMatcher.matches(m) && isWarnEnabledMethodMatcher.matches(ifCondition.getTree())))) {
                return true;
            }
            return false;
        }

        private enum AccumulatorKind {
            NONE,
            INFO,
            DEBUG,
            TRACE,
            ERROR,
            WARN;

            public static AccumulatorKind fromMethodInvocation(J.MethodInvocation mi) {
                if (infoMethodMatcher.matches(mi))
                    return INFO;
                if (debugMethodMatcher.matches(mi))
                    return DEBUG;
                if (traceMethodMatcher.matches(mi))
                    return TRACE;
                if (errorMethodMatcher.matches(mi))
                    return ERROR;
                if (warnMethodMatcher.matches(mi))
                    return WARN;
                return NONE;
            }
        }
    }
}
