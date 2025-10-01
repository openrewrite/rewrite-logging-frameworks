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
import org.openrewrite.internal.StringUtils;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.*;
import java.util.function.Function;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
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
        return "Optimize log statements";
    }

    @Override
    public String getDescription() {
        return "When trace, debug and info log statements use methods for constructing log messages, " +
                "those methods are called regardless of whether the log level is enabled. " +
                "This recipe optimizes these statements by either wrapping them in if-statements (SLF4J 1.x) " +
                "or converting them to fluent API calls (SLF4J 2.0+) to ensure expensive methods are only called when necessary.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                or(new UsesMethod<>(infoMatcher), new UsesMethod<>(debugMatcher), new UsesMethod<>(traceMatcher)),
                new OptimizeLogStatementsVisitor());
    }


private static class OptimizeLogStatementsVisitor extends JavaVisitor<ExecutionContext> {

        final Set<UUID> visitedBlocks = new HashSet<>();

        private boolean supportsFluentApi(J.MethodInvocation logMethod) {
            // Check if the logger type supports fluent API by looking for atInfo/atDebug/atTrace methods
            if (logMethod.getSelect() == null || logMethod.getMethodType() == null) {
                return false;
            }

            JavaType.FullyQualified loggerType = TypeUtils.asFullyQualified(logMethod.getMethodType().getDeclaringType());
            if (loggerType == null) {
                return false;
            }

            // Check if the logger type has the fluent API methods (introduced in SLF4J 2.0)
            return loggerType.getMethods().stream()
                    .anyMatch(m -> "atInfo".equals(m.getName()) ||
                                  "atDebug".equals(m.getName()) ||
                                  "atTrace".equals(m.getName()));
        }

        @Override
        public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            J.MethodInvocation m = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
            if (m.getSelect() != null &&
                    (infoMatcher.matches(m) || debugMatcher.matches(m) || traceMatcher.matches(m)) &&
                    !isInIfStatementWithLogLevelCheck(getCursor(), m) &&
                    !isAlreadyUsingFluentApi(getCursor()) &&
                    isAnyArgumentExpensive(m)) {

                // Check if we should use fluent API (SLF4J 2.0+) or if-statements (SLF4J 1.x)
                if (supportsFluentApi(m)) {
                    return convertToFluentApi(m, ctx);
                }
                // Use the traditional if-statement approach for SLF4J 1.x
                J container = getCursor().getParentTreeCursor().getValue();
                if (container instanceof J.Block) {
                    UUID id = container.getId();
                    J.If if_ = ((J.If) JavaTemplate
                            .builder("if(#{logger:any(org.slf4j.Logger)}.is#{}Enabled()) {}")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "slf4j-api-1.+"))
                            .build()
                            .apply(getCursor(), m.getCoordinates().replace(),
                                    m.getSelect(), StringUtils.capitalize(m.getSimpleName())))
                            .withThenPart(m.withPrefix(m.getPrefix().withWhitespace("\n" + m.getPrefix().getWhitespace().replace("\n", ""))))
                            .withPrefix(m.getPrefix().withComments(emptyList()));
                    visitedBlocks.add(id);
                    return if_;
                }
            }
            return m;
        }

        private J.MethodInvocation convertToFluentApi(J.MethodInvocation m, ExecutionContext ctx) {
            String logLevel = m.getSimpleName();
            String fluentLevel = "at" + StringUtils.capitalize(logLevel);

            List<Expression> args = m.getArguments();
            if (!args.isEmpty()) {
                if (args.size() > 1) {
                    // First argument is the message template
                    Expression messageTemplate = args.get(0);

                    // Build fluent API with addArgument() calls for each parameter
                    StringBuilder templateStr = new StringBuilder();
                    templateStr.append("#{logger:any(org.slf4j.Logger)}.").append(fluentLevel).append("()");

                    // Add each parameter as an argument
                    // Use lambda for expensive operations, direct value for cheap ones
                    List<Object> templateArgs = new ArrayList<>();
                    //noinspection DataFlowIssue
                    templateArgs.add(m.getSelect());

                    for (int i = 1; i < args.size(); i++) {
                        Expression arg = args.get(i);
                        if (isExpensiveArgument(arg)) {
                            // Use supplier lambda for expensive operations
                            templateStr.append(".addArgument(() -> #{any()})");
                        } else {
                            // Use direct value for cheap operations
                            templateStr.append(".addArgument(#{any()})");
                        }
                        templateArgs.add(arg);
                    }
                    templateStr.append(".log(#{any()})");
                    templateArgs.add(messageTemplate);

                    JavaTemplate template = JavaTemplate
                            .builder(templateStr.toString())
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "slf4j-api-2.+"))
                            .build();

                    return template.apply(getCursor(), m.getCoordinates().replace(), templateArgs.toArray());
                }
                // Simple case with just a message
                Expression arg = args.get(0);
                if (isExpensiveArgument(arg)) {
                    // Use supplier lambda for expensive message
                    JavaTemplate template = JavaTemplate
                            .builder("#{logger:any(org.slf4j.Logger)}.#{}().log(() -> #{any()})")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "slf4j-api-2.+"))
                            .build();

                    //noinspection DataFlowIssue
                    return template.apply(getCursor(), m.getCoordinates().replace(),
                            m.getSelect(), fluentLevel, arg);
                }
                // Use direct value for cheap message
                JavaTemplate template = JavaTemplate
                        .builder("#{logger:any(org.slf4j.Logger)}.#{}().log(#{any()})")
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "slf4j-api-2.+"))
                        .build();

                //noinspection DataFlowIssue
                return template.apply(getCursor(), m.getCoordinates().replace(),
                        m.getSelect(), fluentLevel, arg);
            }
            return m;
        }

        private boolean isExpensiveArgument(Expression arg) {
            return !(arg instanceof J.MethodInvocation && isSimpleGetter((J.MethodInvocation) arg) ||
                    arg instanceof J.Literal ||
                    arg instanceof J.Identifier ||
                    arg instanceof J.FieldAccess ||
                    arg instanceof J.Binary && isOnlyLiterals((J.Binary) arg));
        }

        private boolean isAlreadyUsingFluentApi(Cursor cursor) {
            // Check if we're already in a fluent API chain
            J.MethodInvocation parent = cursor.firstEnclosing(J.MethodInvocation.class);
            if (parent != null && "log".equals(parent.getSimpleName())) {
                Expression select = parent.getSelect();
                if (select instanceof J.MethodInvocation) {
                    J.MethodInvocation selectMethod = (J.MethodInvocation) select;
                    return "addArgument".equals(selectMethod.getSimpleName()) ||
                           "addParameter".equals(selectMethod.getSimpleName()) ||
                           selectMethod.getSimpleName().startsWith("at");
                }
            }
            return false;
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

        private boolean isAnyArgumentExpensive(J.MethodInvocation m) {
            return m
                    .getArguments()
                    .stream()
                    .anyMatch(arg ->
                            !(arg instanceof J.MethodInvocation && isSimpleGetter((J.MethodInvocation) arg) ||
                                    arg instanceof J.Literal ||
                                    arg instanceof J.Identifier ||
                                    arg instanceof J.FieldAccess ||
                                    arg instanceof J.Binary && isOnlyLiterals((J.Binary) arg))
                    );
        }

        private static boolean isSimpleGetter(J.MethodInvocation mi) {
            // Consider it a simple getter if it follows getter naming convention and has no parameters
            return ((mi.getSimpleName().startsWith("get") && mi.getSimpleName().length() > 3) ||
                    (mi.getSimpleName().startsWith("is") && mi.getSimpleName().length() > 2)) &&
                    mi.getMethodType() != null &&
                    mi.getMethodType().getParameterNames().isEmpty() &&
                    ((mi.getSelect() == null || mi.getSelect() instanceof J.Identifier) &&
                            !mi.getMethodType().hasFlags(Flag.Static));
        }

        private static boolean isOnlyLiterals(J.Binary binary) {
            return isLiteralOrBinary(binary.getLeft()) && isLiteralOrBinary(binary.getRight());
        }

        private static boolean isLiteralOrBinary(J expression) {
            return expression instanceof J.Literal ||
                    isSimpleBooleanGetter(expression) ||
                    isBooleanIdentifier(expression) ||
                    expression instanceof J.Binary && isOnlyLiterals((J.Binary) expression);
        }

        private static boolean isSimpleBooleanGetter(J expression) {
            if (expression instanceof J.MethodInvocation) {
                J.MethodInvocation mi = (J.MethodInvocation) expression;
                return isSimpleGetter(mi) && mi.getMethodType() != null && isTypeBoolean(mi.getMethodType().getReturnType());
            }
            return false;
        }

        private static boolean isBooleanIdentifier(J expression) {
            return expression instanceof J.Identifier && isTypeBoolean(((J.Identifier) expression).getType());
        }

        private static boolean isTypeBoolean(@Nullable JavaType type) {
            return type == JavaType.Primitive.Boolean || TypeUtils.isAssignableTo("java.lang.Boolean", type);
        }
    }

    @EqualsAndHashCode(callSuper = false)
    @Value
    private static class MergeLogStatementsInCheck extends JavaIsoVisitor<ExecutionContext> {

        Set<UUID> blockIds;

        @Override
        public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
            J.Block b = super.visitBlock(block, ctx);
            if (blockIds.contains(b.getId())) {
                StatementAccumulator acc = new StatementAccumulator((J j) -> autoFormat(j, ctx, getCursor()));
                for (Statement statement : b.getStatements()) {
                    acc.push(statement);
                }
                return b.withStatements(acc.pull());
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

        private final Function<J, J> formatter;
        private final List<Statement> statements = new ArrayList<>();
        private final List<Statement> logStatementsCache = new ArrayList<>();
        private AccumulatorKind accumulatorKind = AccumulatorKind.NONE;
        private J.@Nullable If ifCache = null;

        public StatementAccumulator(Function<J, J> formatter) {
            this.formatter = formatter;
        }

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
                }
                if (if_.getThenPart() instanceof J.Block) {
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
                }
                if (if_.getThenPart() instanceof J.Block &&
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
                J.If anIf = ifCache.withThenPart(new J.Block(randomId(), Space.EMPTY, Markers.EMPTY, JRightPadded.build(false), logStatementsCache.stream().map(JRightPadded::build).collect(toList()), Space.EMPTY));
                statements.add((Statement) formatter.apply(anIf));
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
