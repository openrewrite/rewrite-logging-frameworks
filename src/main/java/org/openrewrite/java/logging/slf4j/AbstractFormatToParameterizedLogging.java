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
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.*;

public abstract class AbstractFormatToParameterizedLogging extends Recipe {

    private static final Set<String> LOGGER_METHODS = new HashSet<>(Arrays.asList("trace", "debug", "info", "warn", "error"));

    @Getter
    final Set<String> tags = new HashSet<>(Arrays.asList("logging", "slf4j"));

    protected abstract TreeVisitor<?, ExecutionContext> getFormatPrecondition();

    protected abstract boolean isFormatCall(J.MethodInvocation call);

    protected abstract boolean isValidFormatString(String format);

    protected abstract boolean validateArgumentCount(String format, List<Expression> formatArgs);

    protected abstract String convertToSlf4jTemplate(String format);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(getFormatPrecondition(), new JavaVisitor<ExecutionContext>() {

            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);

                if (!LOGGER_METHODS.contains(m.getSimpleName()) || m.getSelect() == null ||
                        !TypeUtils.isOfClassType(m.getSelect().getType(), "org.slf4j.Logger")) {
                    return m;
                }

                List<Expression> args = m.getArguments();
                int formatArgIndex = findFormatArgumentIndex(args);
                if (formatArgIndex < 0 || formatArgIndex >= args.size()) {
                    return m;
                }

                Expression formatArg = args.get(formatArgIndex);
                if (!(formatArg instanceof J.MethodInvocation)) {
                    return m;
                }
                J.MethodInvocation formatCall = (J.MethodInvocation) formatArg;
                if (!isFormatCall(formatCall)) {
                    return m;
                }

                List<Expression> formatArgs = formatCall.getArguments();
                if (formatArgs.isEmpty()) {
                    return m;
                }

                String formatString = extractFormatString(formatArgs.get(0));
                if (formatString == null) {
                    return m;
                }

                if (!isValidFormatString(formatString)) {
                    return m;
                }

                if (!validateArgumentCount(formatString, formatArgs)) {
                    return m;
                }

                String slf4jTemplate = convertToSlf4jTemplate(formatString);

                List<Expression> newArgs = buildNewArguments(args, formatArgIndex, slf4jTemplate, formatArgs);

                JavaType.Method methodType = formatCall.getMethodType();
                if (methodType != null) {
                    JavaType.FullyQualified declaringType = methodType.getDeclaringType();
                    if (formatCall.getSelect() == null) {
                        maybeRemoveImport(declaringType.getFullyQualifiedName() + "." + formatCall.getSimpleName());
                    } else {
                        maybeRemoveImport(declaringType);
                    }
                }

                return m.withArguments(newArgs);
            }

            private int findFormatArgumentIndex(List<Expression> args) {
                if (args.isEmpty()) {
                    return -1;
                }

                if (TypeUtils.isOfClassType(args.get(0).getType(), "org.slf4j.Marker")) {
                    return 1;
                }

                return 0;
            }

            private @Nullable String extractFormatString(Expression expr) {
                if (expr instanceof J.Literal) {
                    J.Literal literal = (J.Literal) expr;
                    if (literal.getValue() instanceof String) {
                        return (String) literal.getValue();
                    }
                    return null;
                }
                if (expr instanceof J.Binary) {
                    J.Binary binary = (J.Binary) expr;
                    if (binary.getOperator() == J.Binary.Type.Addition) {
                        String left = extractFormatString(binary.getLeft());
                        String right = extractFormatString(binary.getRight());
                        return left + right;
                    }
                }
                return null;
            }

            private List<Expression> buildNewArguments(
                    List<Expression> loggerArgs,
                    int formatArgIndex,
                    String slf4jTemplate,
                    List<Expression> formatArgs
            ) {
                List<Expression> newArgs = new ArrayList<>();

                for (int i = 0; i < formatArgIndex; i++) {
                    newArgs.add(loggerArgs.get(i));
                }

                Expression originalFormatArg = loggerArgs.get(formatArgIndex);
                J.Literal templateLiteral = new J.Literal(
                        originalFormatArg.getId(),
                        originalFormatArg.getPrefix(),
                        originalFormatArg.getMarkers(),
                        slf4jTemplate,
                        "\"" + slf4jTemplate.replace("\\", "\\\\").replace("\"", "\\\"") + "\"",
                        null,
                        JavaType.Primitive.String
                );
                newArgs.add(templateLiteral);

                for (int i = 1; i < formatArgs.size(); i++) {
                    newArgs.add(formatArgs.get(i));
                }

                for (int i = formatArgIndex + 1; i < loggerArgs.size(); i++) {
                    newArgs.add(loggerArgs.get(i));
                }

                return newArgs;
            }
        });
    }
}
