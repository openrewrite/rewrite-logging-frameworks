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
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessageFormatToParameterizedLogging extends AbstractFormatToParameterizedLogging {

    private static final MethodMatcher MESSAGE_FORMAT = new MethodMatcher("java.text.MessageFormat format(..)");
    private static final Pattern SIMPLE_PLACEHOLDER = Pattern.compile("\\{(\\d+)}");
    private static final Pattern COMPLEX_PLACEHOLDER = Pattern.compile("\\{\\d+,[^}]+}");

    @Getter
    final String displayName = "`MessageFormat.format()` in logging statements should use SLF4J parameterized logging";

    @Getter
    final String description = "Replace `MessageFormat.format()` calls in SLF4J logging statements with parameterized placeholders for improved performance.";

    @Override
    protected TreeVisitor<?, ExecutionContext> getFormatPrecondition() {
        return new UsesType<>("java.text.MessageFormat", null);
    }

    @Override
    protected boolean isFormatCall(J.MethodInvocation call) {
        return MESSAGE_FORMAT.matches(call);
    }

    @Override
    protected boolean isValidFormatString(String pattern) {
        if (COMPLEX_PLACEHOLDER.matcher(pattern).find()) {
            return false;
        }

        return SIMPLE_PLACEHOLDER.matcher(pattern).find();
    }

    @Override
    protected boolean validateArgumentCount(String pattern, List<Expression> formatArgs) {
        SortedSet<Integer> indices = new TreeSet<>();
        Matcher matcher = SIMPLE_PLACEHOLDER.matcher(pattern);
        while (matcher.find()) {
            indices.add(Integer.parseInt(matcher.group(1)));
        }

        if (indices.isEmpty()) {
            return false;
        }

        int expected = 0;
        for (Integer index : indices) {
            if (index != expected) {
                return false;
            }
            expected++;
        }

        List<Integer> indicesInOrder = new ArrayList<>();
        Matcher orderMatcher = SIMPLE_PLACEHOLDER.matcher(pattern);
        while (orderMatcher.find()) {
            indicesInOrder.add(Integer.parseInt(orderMatcher.group(1)));
        }
        for (int i = 0; i < indicesInOrder.size(); i++) {
            if (indicesInOrder.get(i) != i) {
                return false;
            }
        }

        int maxIndex = indices.last();
        return formatArgs.size() == maxIndex + 2;
    }

    @Override
    protected String convertToSlf4jTemplate(String pattern) {
        return pattern.replaceAll("\\{\\d+}", "{}");
    }
}
