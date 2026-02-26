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
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringFormatToParameterizedLogging extends AbstractFormatToParameterizedLogging {

    private static final Pattern SIMPLE_FORMAT_SPECIFIER = Pattern.compile("%[sdxofbc]");
    private static final Pattern COMPLEX_FORMAT_PATTERN = Pattern.compile("%[0-9$+\\-#, (]+[sdxofbc]|%%|%n");
    private static final MethodMatcher STRING_FORMAT = new MethodMatcher("java.lang.String format(..)");

    @Getter
    final String displayName = "`String.format()` in logging statements should use SLF4J parameterized logging";

    @Getter
    final String description = "Replace `String.format()` calls in SLF4J logging statements with parameterized placeholders for improved performance.";

    @Override
    protected TreeVisitor<?, ExecutionContext> getFormatPrecondition() {
        return new UsesMethod<>(STRING_FORMAT);
    }

    @Override
    protected boolean isFormatCall(J.MethodInvocation call) {
        return STRING_FORMAT.matches(call);
    }

    @Override
    protected boolean isValidFormatString(String format) {
        if (COMPLEX_FORMAT_PATTERN.matcher(format).find()) {
            return false;
        }

        return SIMPLE_FORMAT_SPECIFIER.matcher(format).find();
    }

    @Override
    protected boolean validateArgumentCount(String format, List<Expression> formatArgs) {
        Matcher matcher = SIMPLE_FORMAT_SPECIFIER.matcher(format);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return formatArgs.size() == count + 1;
    }

    @Override
    protected String convertToSlf4jTemplate(String format) {
        return format.replaceAll("%[sdxofbc]", "{}");
    }
}
