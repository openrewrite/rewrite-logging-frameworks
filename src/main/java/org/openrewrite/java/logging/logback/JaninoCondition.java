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
package org.openrewrite.java.logging.logback;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The subset of Janino expressions from the removed {@code <if condition="...">} attribute that has an exact
 * equivalent among the {@code ch.qos.logback.core.boolex} conditions.
 */
final class JaninoCondition {

    enum Type {
        /** {@code property("k").equals("v")}, {@code p("k").equals("v")} or {@code "v".equals(property("k"))}. */
        PROPERTY_EQUALS(4),
        /** {@code property("k").contains("v")} or {@code p("k").contains("v")}. */
        PROPERTY_CONTAINS(4),
        IS_DEFINED(4),
        IS_NULL(4),
        /** The {@code true} and {@code false} literals, which both languages share. */
        BOOLEAN_LITERAL(4),
        NOT(3),
        AND(2),
        OR(1);

        private final int precedence;

        Type(int precedence) {
            this.precedence = precedence;
        }
    }

    private final Type type;

    private final @Nullable String key;

    private final @Nullable String value;

    private final List<JaninoCondition> operands;

    private JaninoCondition(Type type, @Nullable String key, @Nullable String value, List<JaninoCondition> operands) {
        this.type = type;
        this.key = key;
        this.value = value;
        this.operands = operands;
    }

    /**
     * Parses a Janino condition, returning {@code null} when it falls outside the translatable subset.
     */
    static @Nullable JaninoCondition parse(String condition) {
        Parser parser = new Parser(condition);
        JaninoCondition parsed = parser.parseOr();
        parser.skipWhitespace();
        return parser.atEnd() ? parsed : null;
    }

    Type getType() {
        return type;
    }

    String getKey() {
        if (key == null) {
            throw new IllegalStateException(type + " does not carry a property key");
        }
        return key;
    }

    String getValue() {
        if (value == null) {
            throw new IllegalStateException(type + " does not carry a property value");
        }
        return value;
    }

    /**
     * Renders this condition in the expression language understood by {@code ExpressionPropertyCondition}.
     */
    String toExpression() {
        StringBuilder expression = new StringBuilder();
        append(expression, 0);
        return expression.toString();
    }

    private void append(StringBuilder expression, int parentPrecedence) {
        boolean parenthesize = type.precedence < parentPrecedence;
        if (parenthesize) {
            expression.append('(');
        }
        switch (type) {
            case PROPERTY_EQUALS:
                appendCall(expression, "propertyEquals", key, value);
                break;
            case PROPERTY_CONTAINS:
                appendCall(expression, "propertyContains", key, value);
                break;
            case IS_DEFINED:
                appendCall(expression, "isDefined", key, null);
                break;
            case IS_NULL:
                appendCall(expression, "isNull", key, null);
                break;
            case BOOLEAN_LITERAL:
                expression.append(value);
                break;
            case NOT:
                expression.append('!');
                operands.get(0).append(expression, type.precedence);
                break;
            default:
                String operator = type == Type.AND ? " && " : " || ";
                for (int i = 0; i < operands.size(); i++) {
                    if (i > 0) {
                        expression.append(operator);
                    }
                    operands.get(i).append(expression, type.precedence);
                }
                break;
        }
        if (parenthesize) {
            expression.append(')');
        }
    }

    private static void appendCall(StringBuilder expression, String function, @Nullable String key, @Nullable String value) {
        expression.append(function).append("(\"").append(key).append('"');
        if (value != null) {
            expression.append(", \"").append(value).append('"');
        }
        expression.append(')');
    }

    /**
     * A recursive descent parser that yields {@code null} for anything outside the translatable subset.
     */
    private static final class Parser {

        private final String condition;

        private int position;

        private Parser(String condition) {
            this.condition = condition;
        }

        private @Nullable JaninoCondition parseOr() {
            return parseBinary(Type.OR);
        }

        private @Nullable JaninoCondition parseBinary(Type type) {
            JaninoCondition first = type == Type.OR ? parseBinary(Type.AND) : parseUnary();
            if (first == null) {
                return null;
            }
            String operator = type == Type.OR ? "||" : "&&";
            List<JaninoCondition> operands = new ArrayList<>(2);
            operands.add(first);
            while (true) {
                skipWhitespace();
                if (!consume(operator)) {
                    return operands.size() == 1 ? first :
                            new JaninoCondition(type, null, null, Collections.unmodifiableList(operands));
                }
                JaninoCondition next = type == Type.OR ? parseBinary(Type.AND) : parseUnary();
                if (next == null) {
                    return null;
                }
                operands.add(next);
            }
        }

        private @Nullable JaninoCondition parseUnary() {
            skipWhitespace();
            if (peek() == '!') {
                if (peekAt(1) == '=') {
                    return null;
                }
                position++;
                JaninoCondition operand = parseUnary();
                return operand == null ? null :
                        new JaninoCondition(Type.NOT, null, null, Collections.singletonList(operand));
            }
            return parsePrimary();
        }

        private @Nullable JaninoCondition parsePrimary() {
            skipWhitespace();
            if (consume("(")) {
                JaninoCondition nested = parseOr();
                skipWhitespace();
                return nested == null || !consume(")") ? null : nested;
            }
            if (peek() == '"') {
                // "v".equals(property("k"))
                String literal = parseStringLiteral();
                if (literal == null || !consume(".equals(")) {
                    return null;
                }
                String key = parsePropertyLookup();
                skipWhitespace();
                return key == null || !consume(")") ? null :
                        new JaninoCondition(Type.PROPERTY_EQUALS, key, literal, Collections.<JaninoCondition>emptyList());
            }
            int start = position;
            String identifier = parseIdentifier();
            if ("true".equals(identifier) || "false".equals(identifier)) {
                return new JaninoCondition(Type.BOOLEAN_LITERAL, null, identifier, Collections.<JaninoCondition>emptyList());
            }
            if ("isDefined".equals(identifier) || "isNull".equals(identifier)) {
                skipWhitespace();
                if (!consume("(")) {
                    return null;
                }
                String key = parseStringLiteral();
                skipWhitespace();
                return key == null || !consume(")") ? null :
                        new JaninoCondition("isNull".equals(identifier) ? Type.IS_NULL : Type.IS_DEFINED, key, null,
                                Collections.<JaninoCondition>emptyList());
            }
            // property("k").equals("v") or property("k").contains("v")
            position = start;
            String key = parsePropertyLookup();
            skipWhitespace();
            if (key == null || !consume(".")) {
                return null;
            }
            String method = parseIdentifier();
            Type type = "equals".equals(method) ? Type.PROPERTY_EQUALS :
                    "contains".equals(method) ? Type.PROPERTY_CONTAINS : null;
            skipWhitespace();
            if (type == null || !consume("(")) {
                return null;
            }
            String value = parseStringLiteral();
            skipWhitespace();
            return value == null || !consume(")") ? null :
                    new JaninoCondition(type, key, value, Collections.<JaninoCondition>emptyList());
        }

        private @Nullable String parsePropertyLookup() {
            skipWhitespace();
            String identifier = parseIdentifier();
            if (!"property".equals(identifier) && !"p".equals(identifier)) {
                return null;
            }
            skipWhitespace();
            if (!consume("(")) {
                return null;
            }
            String key = parseStringLiteral();
            skipWhitespace();
            return key == null || !consume(")") ? null : key;
        }

        private String parseIdentifier() {
            skipWhitespace();
            int start = position;
            while (position < condition.length() && Character.isJavaIdentifierPart(condition.charAt(position))) {
                position++;
            }
            return condition.substring(start, position);
        }

        /**
         * Reads a double quoted literal, rejecting escapes so that the text can be reused verbatim in XML.
         */
        private @Nullable String parseStringLiteral() {
            skipWhitespace();
            if (peek() != '"') {
                return null;
            }
            int start = ++position;
            while (position < condition.length()) {
                char c = condition.charAt(position);
                if (c == '\\') {
                    return null;
                }
                if (c == '"') {
                    return condition.substring(start, position++);
                }
                position++;
            }
            return null;
        }

        private void skipWhitespace() {
            while (position < condition.length() && Character.isWhitespace(condition.charAt(position))) {
                position++;
            }
        }

        private boolean consume(String token) {
            if (condition.startsWith(token, position)) {
                position += token.length();
                return true;
            }
            return false;
        }

        private char peek() {
            return peekAt(0);
        }

        private char peekAt(int offset) {
            int index = position + offset;
            return index < condition.length() ? condition.charAt(index) : '\0';
        }

        private boolean atEnd() {
            return position == condition.length();
        }
    }
}
