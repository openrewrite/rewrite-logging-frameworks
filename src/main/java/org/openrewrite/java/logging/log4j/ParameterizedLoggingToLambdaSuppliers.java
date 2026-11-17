/*
 * Copyright 2026 the original author or authors.
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
package org.openrewrite.java.logging.log4j;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Arrays.asList;

@EqualsAndHashCode(callSuper = false)
@Value
public class ParameterizedLoggingToLambdaSuppliers extends Recipe {

    private static final String LOGGER = "org.apache.logging.log4j.Logger";
    private static final String LEVEL = "org.apache.logging.log4j.Level";
    private static final String MARKER = "org.apache.logging.log4j.Marker";
    private static final List<String> ALL_LEVELS = asList("trace", "debug", "info", "warn", "error", "fatal");
    private static final MethodMatcher LOG_METHODS = new MethodMatcher(LOGGER + " *(..)", true);
    private static final String LOG4J_SUPPLIER = "org.apache.logging.log4j.util.Supplier";
    private static final String JAVA_SUPPLIER = "java.util.function.Supplier";
    private static final MethodMatcher LOG4J_SUPPLIER_GET = new MethodMatcher(LOG4J_SUPPLIER + " get()", true);
    private static final MethodMatcher JAVA_SUPPLIER_GET = new MethodMatcher(JAVA_SUPPLIER + " get()", true);

    @Option(displayName = "Log levels",
            description = "The log levels whose statements are converted, e.g. `trace, debug`. " +
                          "Applies to the level methods such as `debug(..)` as well as to `log(Level.DEBUG, ..)`. " +
                          "Defaults to all levels. A `log(..)` call whose level is not a `Level` constant is only " +
                          "converted when no levels are given.",
            example = "trace, debug",
            required = false)
    @Nullable
    List<String> logLevels;

    @Option(displayName = "Convert simple arguments",
            description = "When `true`, statements whose arguments are all cheap to evaluate " +
                          "(literals, variables, field accesses and simple getters) are converted as well. " +
                          "Defaults to `false`, which converts a statement only when at least one argument does real work.",
            required = false)
    @Nullable
    Boolean convertSimpleArguments;

    String displayName = "Use lambda suppliers for Log4j parameters";

    String description = "Replace parameterized Log4j 2 log statements such as `logger.debug(String message, Object... params)` " +
                         "with `logger.debug(String message, Supplier<?>... paramSuppliers)`, so that the parameters are only " +
                         "evaluated when the log level is enabled. Statements whose last argument is a `Throwable`, whose arguments " +
                         "capture local variables that are not effectively final, have side effects, throw checked exceptions " +
                         "or are suppliers themselves are left unchanged.";

    Set<String> tags = new HashSet<>(asList("logging", "log4j"));

    @Override
    public Validated<Object> validate() {
        Validated<Object> validated = super.validate();
        if (logLevels != null) {
            for (String level : logLevels) {
                validated = validated.and(Validated.test("logLevels", "Must be one of " + ALL_LEVELS, level,
                        l -> ALL_LEVELS.contains(l.trim().toLowerCase(Locale.ROOT))));
            }
        }
        return validated;
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        Set<String> levels = new HashSet<>();
        if (logLevels != null) {
            for (String level : logLevels) {
                levels.add(level.trim().toLowerCase(Locale.ROOT));
            }
        }
        boolean convertSimple = Boolean.TRUE.equals(convertSimpleArguments);
        return Preconditions.check(new UsesMethod<>(LOG_METHODS), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                JavaType.Method methodType = m.getMethodType();
                if (m.getSelect() == null || methodType == null || !LOG_METHODS.matches(m)) {
                    return m;
                }

                List<Expression> args = m.getArguments();
                int messageIndex = messageIndex(methodType, args, levels);
                if (messageIndex < 0) {
                    return m;
                }
                List<Expression> params = args.subList(messageIndex + 1, args.size());
                if (!isConvertible(methodType, messageIndex, params) ||
                    !convertSimple && params.stream().noneMatch(ParameterizedLoggingToLambdaSuppliers::isExpensive) ||
                    !capturesOnlyEffectivelyFinalLocals(params, getCursor())) {
                    return m;
                }

                StringJoiner template = new StringJoiner(", ", "#{any(" + LOGGER + ")}." + m.getSimpleName() + "(", ")");
                for (int i = 0; i < messageIndex; i++) {
                    template.add("#{any(" + (isType(args.get(i), LEVEL) ? LEVEL : MARKER) + ")}");
                }
                template.add("#{any(java.lang.String)}");
                for (int i = 0; i < params.size(); i++) {
                    template.add("() -> #{any()}");
                }
                List<Object> templateArgs = new ArrayList<>(args.size() + 1);
                templateArgs.add(m.getSelect());
                templateArgs.addAll(args);
                J.MethodInvocation converted = JavaTemplate.builder(template.toString())
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "log4j-api"))
                        .build()
                        .apply(getCursor(), m.getCoordinates().replace(), templateArgs.toArray());
                // Keep the line breaks and comments around the arguments of the original call
                JContainer<Expression> originalArgs = m.getPadding().getArguments();
                return m.withMethodType(converted.getMethodType())
                        .withName(m.getName().withType(converted.getMethodType()))
                        .getPadding().withArguments(originalArgs.getPadding().withElements(
                                ListUtils.map(originalArgs.getPadding().getElements(), (i, original) -> {
                                    Expression arg = converted.getArguments().get(i);
                                    Space prefix = original.getElement().getPrefix();
                                    if (arg instanceof J.Lambda) {
                                        J.Lambda lambda = (J.Lambda) arg;
                                        return original.withElement(lambda.withPrefix(prefix).withBody(lambda.getBody().withPrefix(Space.SINGLE_SPACE)));
                                    }
                                    return original.withElement(arg.withPrefix(prefix));
                                })));
            }
        });
    }

    private static int messageIndex(JavaType.Method methodType, List<Expression> args, Set<String> levels) {
        int index = 0;
        if ("log".equals(methodType.getName())) {
            if (args.isEmpty() || !isType(args.get(0), LEVEL) ||
                !levels.isEmpty() && !levels.contains(levelName(args.get(0)))) {
                return -1;
            }
            index++;
        } else if (!(levels.isEmpty() ? ALL_LEVELS : levels).contains(methodType.getName())) {
            return -1;
        }
        if (args.size() > index && isType(args.get(index), MARKER)) {
            index++;
        }
        List<JavaType> parameterTypes = methodType.getParameterTypes();
        if (parameterTypes.size() <= index || !TypeUtils.isString(parameterTypes.get(index))) {
            return -1;
        }
        return index;
    }

    private static boolean isConvertible(JavaType.Method methodType, int messageIndex, List<Expression> params) {
        if (params.isEmpty()) {
            return false;
        }
        // All parameters after the message must be declared as `Object` or `Object...`
        List<JavaType> parameterTypes = methodType.getParameterTypes();
        for (int i = messageIndex + 1; i < parameterTypes.size(); i++) {
            JavaType type = parameterTypes.get(i);
            boolean objectType = TypeUtils.isObject(type) ||
                                 type instanceof JavaType.Array && TypeUtils.isObject(((JavaType.Array) type).getElemType());
            if (!objectType) {
                return false;
            }
        }
        for (Expression param : params) {
            JavaType type = param.getType();
            if (type == null || type instanceof JavaType.Unknown || type instanceof JavaType.Array ||
                // A supplier is logged as an object; wrapping it in another supplier gains nothing
                TypeUtils.isAssignableTo(LOG4J_SUPPLIER, type) || TypeUtils.isAssignableTo(JAVA_SUPPLIER, type) ||
                param instanceof J.Literal && ((J.Literal) param).getValue() == null ||
                hasSideEffectsOrCheckedExceptions(param)) {
                return false;
            }
        }
        // Log4j logs a trailing Throwable with its stack trace; such calls are kept as they are
        return !TypeUtils.isAssignableTo("java.lang.Throwable", params.get(params.size() - 1).getType());
    }

    private static boolean isType(Expression expression, String fqn) {
        return TypeUtils.isAssignableTo(fqn, expression.getType());
    }

    private static @Nullable String levelName(Expression level) {
        if (level instanceof J.FieldAccess) {
            return ((J.FieldAccess) level).getSimpleName().toLowerCase(Locale.ROOT);
        }
        if (level instanceof J.Identifier && ((J.Identifier) level).getFieldType() != null &&
            TypeUtils.isOfClassType(((J.Identifier) level).getFieldType().getOwner(), LEVEL)) {
            return ((J.Identifier) level).getSimpleName().toLowerCase(Locale.ROOT);
        }
        return null;
    }

    private static boolean isExpensive(Expression arg) {
        Expression unwrapped = arg.unwrap();
        if (unwrapped instanceof J.MethodInvocation) {
            J.MethodInvocation mi = (J.MethodInvocation) unwrapped;
            if (LOG4J_SUPPLIER_GET.matches(mi) || JAVA_SUPPLIER_GET.matches(mi)) {
                // `Supplier.get()` looks like a getter, but runs whatever the supplier computes
                return true;
            }
            String name = mi.getSimpleName();
            boolean simpleGetter = mi.getMethodType() != null &&
                                   !mi.getMethodType().hasFlags(Flag.Static) &&
                                   mi.getArguments().stream().allMatch(a -> a instanceof J.Empty) &&
                                   (name.startsWith("get") || name.startsWith("is")) &&
                                   (mi.getSelect() == null || mi.getSelect() instanceof J.Identifier || mi.getSelect() instanceof J.FieldAccess);
            return !simpleGetter;
        }
        return !(unwrapped instanceof J.Literal || unwrapped instanceof J.Identifier || unwrapped instanceof J.FieldAccess);
    }

    private static boolean hasSideEffectsOrCheckedExceptions(Expression param) {
        return new JavaIsoVisitor<AtomicBoolean>() {
            @Override
            public J.Assignment visitAssignment(J.Assignment assignment, AtomicBoolean found) {
                found.set(true);
                return assignment;
            }

            @Override
            public J.AssignmentOperation visitAssignmentOperation(J.AssignmentOperation assignOp, AtomicBoolean found) {
                found.set(true);
                return assignOp;
            }

            @Override
            public J.Unary visitUnary(J.Unary unary, AtomicBoolean found) {
                if (unary.getOperator().isModifying()) {
                    found.set(true);
                }
                return super.visitUnary(unary, found);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, AtomicBoolean found) {
                checkThrows(method.getMethodType(), found);
                return super.visitMethodInvocation(method, found);
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, AtomicBoolean found) {
                checkThrows(newClass.getConstructorType(), found);
                return super.visitNewClass(newClass, found);
            }

            @Override
            public J.Lambda visitLambda(J.Lambda lambda, AtomicBoolean found) {
                // What a nested lambda does is not evaluated eagerly
                return lambda;
            }

            private void checkThrows(JavaType.@Nullable Method methodType, AtomicBoolean found) {
                if (methodType == null) {
                    found.set(true);
                    return;
                }
                for (JavaType thrown : methodType.getThrownExceptions()) {
                    if (!TypeUtils.isAssignableTo("java.lang.RuntimeException", thrown) &&
                        !TypeUtils.isAssignableTo("java.lang.Error", thrown)) {
                        found.set(true);
                    }
                }
            }
        }.reduce(param, new AtomicBoolean()).get();
    }

    /**
     * A lambda may only capture local variables that are effectively final.
     */
    private static boolean capturesOnlyEffectivelyFinalLocals(List<Expression> params, Cursor cursor) {
        Set<String> capturedLocals = new HashSet<>();
        for (Expression param : params) {
            new JavaIsoVisitor<Set<String>>() {
                @Override
                public J.Identifier visitIdentifier(J.Identifier identifier, Set<String> locals) {
                    JavaType.Variable fieldType = identifier.getFieldType();
                    if (fieldType != null && !(fieldType.getOwner() instanceof JavaType.FullyQualified)) {
                        locals.add(identifier.getSimpleName());
                    }
                    return identifier;
                }
            }.visit(param, capturedLocals);
        }
        if (capturedLocals.isEmpty()) {
            return true;
        }
        J scope = cursor.firstEnclosing(J.MethodDeclaration.class);
        if (scope == null) {
            scope = cursor.firstEnclosingOrThrow(JavaSourceFile.class);
        }
        return !new JavaIsoVisitor<AtomicBoolean>() {
            @Override
            public J.Assignment visitAssignment(J.Assignment assignment, AtomicBoolean found) {
                checkTarget(assignment.getVariable(), found);
                return super.visitAssignment(assignment, found);
            }

            @Override
            public J.AssignmentOperation visitAssignmentOperation(J.AssignmentOperation assignOp, AtomicBoolean found) {
                checkTarget(assignOp.getVariable(), found);
                return super.visitAssignmentOperation(assignOp, found);
            }

            @Override
            public J.Unary visitUnary(J.Unary unary, AtomicBoolean found) {
                if (unary.getOperator().isModifying()) {
                    checkTarget(unary.getExpression(), found);
                }
                return super.visitUnary(unary, found);
            }

            private void checkTarget(Expression target, AtomicBoolean found) {
                if (target instanceof J.Identifier && capturedLocals.contains(((J.Identifier) target).getSimpleName())) {
                    found.set(true);
                }
            }
        }.reduce(scope, new AtomicBoolean()).get();
    }
}
