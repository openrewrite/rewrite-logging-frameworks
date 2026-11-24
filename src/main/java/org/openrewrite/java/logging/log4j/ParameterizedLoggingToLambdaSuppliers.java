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
import org.openrewrite.java.logging.internal.LogArguments;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.trait.Scope;
import org.openrewrite.java.tree.*;
import org.openrewrite.staticanalysis.java.JavaFileChecker;

import java.util.*;

import static java.util.Arrays.asList;
import static org.openrewrite.java.logging.internal.LogArguments.*;

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

    @Option(displayName = "Log levels",
            description = "The log levels whose statements are converted, e.g. `trace, debug`. " +
                          "Applies to the level methods such as `debug(..)` as well as to `log(Level.DEBUG, ..)`. " +
                          "Defaults to all levels. A `log(..)` call whose level is not a `Level` constant is only " +
                          "converted when no levels are given.",
            example = "trace, debug",
            required = false)
    @Nullable
    List<String> logLevels;

    String displayName = "Use lambda suppliers for Log4j parameters and composed messages";

    String description = "Replace parameterized Log4j 2 log statements such as `logger.debug(String message, Object... params)` " +
                         "with `logger.debug(String message, Supplier<?>... paramSuppliers)`, so that the parameters are only " +
                         "evaluated when the log level is enabled. A message without parameters that is put together at the call, " +
                         "such as `logger.debug(\"Hello \" + name)`, is passed as a supplier too. Statements whose arguments are " +
                         "all cheap to evaluate, such as variables and simple getters, whose last argument is a `Throwable`, whose " +
                         "arguments capture local variables that are not effectively final, have side effects, throw checked " +
                         "exceptions or are suppliers themselves are left unchanged.";

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
        return Preconditions.check(
                Preconditions.and(new JavaFileChecker<>(), new UsesMethod<>(LOG_METHODS)),
                new JavaIsoVisitor<ExecutionContext>() {
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
                        boolean messageOnly = isComposedMessageOnly(methodType, messageIndex, args);
                        if (messageOnly) {
                            if (!lambdasCanCapture(args.subList(messageIndex, messageIndex + 1), updateCursor(m))) {
                                return m;
                            }
                        } else if (!isConvertible(methodType, messageIndex, params) ||
                                   params.stream().noneMatch(LogArguments::isExpensive) ||
                                   !lambdasCanCapture(params, updateCursor(m))) {
                            return m;
                        }

                        StringJoiner template = new StringJoiner(", ", "#{any(" + LOGGER + ")}." + m.getSimpleName() + "(", ")");
                        for (int i = 0; i < messageIndex; i++) {
                            template.add("#{any(" + (TypeUtils.isAssignableTo(LEVEL, args.get(i).getType()) ? LEVEL : MARKER) + ")}");
                        }
                        if (messageOnly) {
                            template.add("() -> #{any(java.lang.String)}");
                            if (!params.isEmpty()) {
                                template.add("#{any(java.lang.Throwable)}");
                            }
                        } else {
                            template.add("#{any(java.lang.String)}");
                            for (int i = 0; i < params.size(); i++) {
                                template.add("() -> #{any()}");
                            }
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
            if (args.isEmpty() || !TypeUtils.isAssignableTo(LEVEL, args.get(0).getType()) ||
                !levels.isEmpty() && !levels.contains(levelName(args.get(0)))) {
                return -1;
            }
            index++;
        } else if (!(levels.isEmpty() ? ALL_LEVELS : levels).contains(methodType.getName())) {
            return -1;
        }
        if (args.size() > index && TypeUtils.isAssignableTo(MARKER, args.get(index).getType())) {
            index++;
        }
        List<JavaType> parameterTypes = methodType.getParameterTypes();
        if (parameterTypes.size() <= index || !TypeUtils.isString(parameterTypes.get(index))) {
            return -1;
        }
        return index;
    }

    private static boolean isComposedMessageOnly(JavaType.Method methodType, int messageIndex, List<Expression> args) {
        // `debug(String)` and `debug(String, Throwable)` have a `Supplier<?>` counterpart for the message
        List<JavaType> parameterTypes = methodType.getParameterTypes();
        int trailing = parameterTypes.size() - messageIndex - 1;
        if (args.size() != parameterTypes.size() || trailing > 1 ||
            trailing == 1 && !TypeUtils.isOfClassType(parameterTypes.get(messageIndex + 1), "java.lang.Throwable")) {
            return false;
        }
        Expression message = args.get(messageIndex);
        return TypeUtils.isString(message.getType()) && isExpensive(message) &&
               !hasSideEffects(message) && !mayThrowCheckedException(message);
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
                hasSideEffects(param) || mayThrowCheckedException(param)) {
                return false;
            }
        }
        // Log4j logs a trailing Throwable with its stack trace; such calls are kept as they are
        return !TypeUtils.isAssignableTo("java.lang.Throwable", params.get(params.size() - 1).getType());
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

    private static boolean lambdasCanCapture(List<Expression> arguments, Cursor cursor) {
        List<Cursor> regions = new ArrayList<>(arguments.size());
        for (Expression argument : arguments) {
            regions.add(Scope.cursorTo(cursor, argument));
        }
        return Scope.lambdaCanCapture(regions);
    }
}
