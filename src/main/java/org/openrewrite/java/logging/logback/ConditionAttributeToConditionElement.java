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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.FindSourceFiles;
import org.openrewrite.Option;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.logging.logback.table.UnmigratedJaninoConditions;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Content;
import org.openrewrite.xml.tree.Xml;

import java.util.Arrays;

/**
 * Migrates only the conditions with an exact equivalent in `ch.qos.logback.core.boolex`, since guessing at the rest
 * would produce a configuration that starts but selects the wrong appenders.
 */
@Value
@EqualsAndHashCode(callSuper = false)
public class ConditionAttributeToConditionElement extends Recipe {

    private static final String DEFAULT_FILE_PATTERN = "**/logback*.xml";

    private static final String BOOLEX = "ch.qos.logback.core.boolex.";

    transient UnmigratedJaninoConditions unmigratedConditions = new UnmigratedJaninoConditions(this);

    String displayName = "Replace the Logback `condition` attribute with the `condition` element";

    String description = "Logback 1.5.37 removed the Janino based `<if condition=\"...\">` attribute that 1.5.20 deprecated, " +
                         "so configuration files still using it fail to select the intended appenders. " +
                         "Replaces the attribute with the `<condition class=\"...\"/>` element that precedes `<if>`, using the " +
                         "conditions shipped in `ch.qos.logback.core.boolex`. " +
                         "Conditions that require custom Java logic are left unchanged and reported in a data table.";

    @Option(displayName = "File pattern",
            description = "A glob expression that can be used to constrain which directories or source files should be searched. " +
                          "Multiple patterns may be specified, separated by a semicolon `;`. " +
                          "If multiple patterns are supplied any of the patterns matching will be interpreted as a match. " +
                          "When not set, `**/logback*.xml` is used.",
            required = false,
            example = "**/logback-spring.xml")
    @Nullable
    String filePattern;

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new FindSourceFiles(filePattern == null ? DEFAULT_FILE_PATTERN : filePattern), new XmlIsoVisitor<ExecutionContext>() {

            @Override
            public Xml.Document visitDocument(Xml.Document document, ExecutionContext ctx) {
                if (!"configuration".equals(document.getRoot().getName())) {
                    return document;
                }
                return super.visitDocument(document, ctx);
            }

            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ctx) {
                Xml.Tag t = super.visitTag(tag, ctx);
                return t.withContent(ListUtils.flatMap(t.getContent(), (index, child) -> {
                    Xml.Attribute condition = conditionAttribute(child);
                    if (condition == null || precededByConditionElement(t, index)) {
                        return child;
                    }
                    String expression = unescape(condition.getValueAsString());
                    JaninoCondition parsed = JaninoCondition.parse(expression);
                    if (parsed == null) {
                        unmigratedConditions.insertRow(ctx, new UnmigratedJaninoConditions.Row(
                                getCursor().firstEnclosingOrThrow(Xml.Document.class).getSourcePath().toString(),
                                expression));
                        return child;
                    }
                    Xml.Tag ifTag = (Xml.Tag) child;
                    return Arrays.asList(
                            conditionElement(parsed, ctx).withPrefix(ifTag.getPrefix()),
                            ifTag.withAttributes(ListUtils.map(ifTag.getAttributes(), a -> a == condition ? null : a))
                                    .withPrefix(lastLineOf(ifTag.getPrefix())));
                }));
            }

            private Xml.@Nullable Attribute conditionAttribute(Content content) {
                if (content instanceof Xml.Tag && "if".equals(((Xml.Tag) content).getName())) {
                    for (Xml.Attribute attribute : ((Xml.Tag) content).getAttributes()) {
                        if ("condition".equals(attribute.getKeyAsString())) {
                            return attribute;
                        }
                    }
                }
                return null;
            }

            private boolean precededByConditionElement(Xml.Tag parent, int index) {
                //noinspection DataFlowIssue
                for (int i = index - 1; i >= 0; i--) {
                    Content preceding = parent.getContent().get(i);
                    if (preceding instanceof Xml.Tag) {
                        return "condition".equals(((Xml.Tag) preceding).getName());
                    }
                }
                return false;
            }

            private Xml.Tag conditionElement(JaninoCondition condition, ExecutionContext ctx) {
                StringBuilder element = new StringBuilder("<condition class=\"").append(BOOLEX);
                switch (condition.getType()) {
                    case PROPERTY_EQUALS:
                        element.append("PropertyEqualityCondition\">")
                                .append("<key>").append(escape(condition.getKey())).append("</key>")
                                .append("<value>").append(escape(condition.getValue())).append("</value>");
                        break;
                    case IS_DEFINED:
                        element.append("IsPropertyDefinedCondition\">")
                                .append("<key>").append(escape(condition.getKey())).append("</key>");
                        break;
                    case IS_NULL:
                        element.append("IsPropertyNullCondition\">")
                                .append("<key>").append(escape(condition.getKey())).append("</key>");
                        break;
                    default:
                        // Only the expression language covers `contains` and the boolean operators.
                        element.append("ExpressionPropertyCondition\">")
                                .append("<expression>").append(escape(condition.toExpression())).append("</expression>");
                        break;
                }
                return autoFormat(Xml.Tag.build(element.append("</condition>").toString()), ctx, getCursor());
            }
        });
    }

    /**
     * Keeps the indentation of a prefix while dropping any blank lines, which belong to the element that now precedes it.
     */
    private static String lastLineOf(String prefix) {
        int lastNewline = prefix.lastIndexOf('\n');
        return lastNewline == -1 ? prefix : '\n' + prefix.substring(lastNewline + 1);
    }

    /**
     * Resolves the entities that an attribute value carries verbatim, so that the condition can be parsed as Java.
     */
    private static String unescape(String value) {
        if (value.indexOf('&') < 0) {
            return value;
        }
        StringBuilder unescaped = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            int end = c == '&' ? value.indexOf(';', i) : -1;
            String entity = end == -1 ? null : entity(value.substring(i + 1, end));
            if (entity == null) {
                unescaped.append(c);
            } else {
                unescaped.append(entity);
                i = end;
            }
        }
        return unescaped.toString();
    }

    private static @Nullable String entity(String name) {
        switch (name) {
            case "amp":
                return "&";
            case "lt":
                return "<";
            case "gt":
                return ">";
            case "quot":
                return "\"";
            case "apos":
                return "'";
            default:
                return characterReference(name);
        }
    }

    /**
     * Resolves a decimal or hexadecimal character reference, which logback accepts anywhere an entity is allowed.
     */
    private static @Nullable String characterReference(String name) {
        if (name.length() < 2 || name.charAt(0) != '#') {
            return null;
        }
        boolean hex = name.charAt(1) == 'x' || name.charAt(1) == 'X';
        try {
            int codePoint = Integer.parseInt(name.substring(hex ? 2 : 1), hex ? 16 : 10);
            return Character.isValidCodePoint(codePoint) ? new String(Character.toChars(codePoint)) : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Escapes only what element text requires, leaving the quotes that conditions are built from readable.
     */
    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
