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
package org.openrewrite.java.logging.internal;

import org.openrewrite.java.tree.*;

public final class LogArguments {

    private LogArguments() {
    }

    /**
     * Whether evaluating a log argument does enough work to be worth deferring until the log level is enabled.
     * Literals, variables, field accesses and simple getters are cheap, and so are casts, array accesses, ternaries
     * and operators applied only to cheap operands. String concatenation is expensive, unless it only joins literals,
     * which the compiler folds into a constant.
     */
    public static boolean isExpensive(Expression argument) {
        Expression expression = argument.unwrap();
        if (expression instanceof J.Literal || expression instanceof J.Identifier) {
            return false;
        }
        if (expression instanceof J.FieldAccess) {
            return isExpensive(((J.FieldAccess) expression).getTarget());
        }
        if (expression instanceof J.MethodInvocation) {
            return !isSimpleGetter((J.MethodInvocation) expression);
        }
        if (expression instanceof J.TypeCast) {
            return isExpensive(((J.TypeCast) expression).getExpression());
        }
        if (expression instanceof J.ArrayAccess) {
            J.ArrayAccess arrayAccess = (J.ArrayAccess) expression;
            return isExpensive(arrayAccess.getIndexed()) || isExpensive(arrayAccess.getDimension().getIndex());
        }
        if (expression instanceof J.Ternary) {
            J.Ternary ternary = (J.Ternary) expression;
            return isExpensive(ternary.getCondition()) || isExpensive(ternary.getTruePart()) ||
                   isExpensive(ternary.getFalsePart());
        }
        if (expression instanceof J.Unary) {
            return isExpensive(((J.Unary) expression).getExpression());
        }
        if (expression instanceof J.Binary) {
            J.Binary binary = (J.Binary) expression;
            if (isConcatenation(binary)) {
                return !isLiteralConcatenation(binary);
            }
            return isExpensive(binary.getLeft()) || isExpensive(binary.getRight());
        }
        return true;
    }

    private static boolean isSimpleGetter(J.MethodInvocation method) {
        JavaType.Method type = method.getMethodType();
        if (type == null || type.hasFlags(Flag.Static) || !type.getParameterTypes().isEmpty()) {
            return false;
        }
        Expression select = method.getSelect();
        if (select != null && !(select.unwrap() instanceof J.Identifier ||
                                select.unwrap() instanceof J.FieldAccess && !isExpensive(select))) {
            return false;
        }
        // `Supplier.get()` and the like run arbitrary code, so the bare `get` and `is` are not getter names
        String name = method.getSimpleName();
        return name.startsWith("get") && name.length() > 3 ||
               name.startsWith("is") && name.length() > 2 ||
               type.getDeclaringType().getKind() == JavaType.FullyQualified.Kind.Record;
    }

    private static boolean isConcatenation(J.Binary binary) {
        JavaType type = binary.getType();
        return binary.getOperator() == J.Binary.Type.Addition &&
               (TypeUtils.isString(type) || !(type instanceof JavaType.Primitive));
    }

    private static boolean isLiteralConcatenation(Expression expression) {
        Expression unwrapped = expression.unwrap();
        return unwrapped instanceof J.Literal ||
               unwrapped instanceof J.Binary &&
               isLiteralConcatenation(((J.Binary) unwrapped).getLeft()) &&
               isLiteralConcatenation(((J.Binary) unwrapped).getRight());
    }
}
