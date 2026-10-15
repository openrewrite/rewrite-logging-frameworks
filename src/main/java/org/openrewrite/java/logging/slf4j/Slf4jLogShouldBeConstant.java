/*
 * Copyright 2024 the original author or authors.
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
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.logging.internal.JavaStringEscapes;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Slf4jLogShouldBeConstant extends Recipe {

    private static final String SLF4J_FORMAT_SPECIFIER = "{}";
    private static final Pattern FORMAT_SPECIFIER_PATTERN = Pattern.compile("%(\\d+\\$|<)?[-#+ 0,(]*\\d*(\\.\\d+)?([tT][a-zA-Z]|[a-zA-Z%])");
    private static final Pattern SIMPLE_FORMAT_SPECIFIER_PATTERN = Pattern.compile("%[dfscb]");
    private static final MethodMatcher SLF4J_LOG = new MethodMatcher("org.slf4j.Logger *(..)");
    private static final MethodMatcher STRING_FORMAT = new MethodMatcher("java.lang.String format(..)");
    private static final MethodMatcher STRING_VALUE_OF = new MethodMatcher("java.lang.String valueOf(..)");
    private static final MethodMatcher OBJECT_TO_STRING = new MethodMatcher("java.lang.Object toString()");

    @Getter
    final String displayName = "SLF4J logging statements should begin with constants";

    @Getter
    final String description = "Logging statements shouldn't begin with `String#format`, calls to `toString()`, etc.";

    @Getter
    final Set<String> tags = new HashSet<>(Arrays.asList("logging", "slf4j"));

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(SLF4J_LOG), new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                if (SLF4J_LOG.matches(method)) {
                    String name = method.getSimpleName();
                    if ("trace".equals(name) || "debug".equals(name) || "info".equals(name) || "warn".equals(name) || "error".equals(name)) {
                        List<Expression> args = method.getArguments();
                        if (STRING_FORMAT.matches(args.get(0))) {
                            J.MethodInvocation stringFormat = (J.MethodInvocation) args.get(0);

                            if (!CompleteExceptionLogging.isStringLiteral(stringFormat.getArguments().get(0))) {
                                return method;
                            }

                            String strFormat = Objects.requireNonNull(((J.Literal) stringFormat.getArguments().get(0)).getValue()).toString();
                            if (!isConvertibleFormat(strFormat, stringFormat.getArguments().size() - 1)) {
                                return method;
                            }
                            String updatedStrFormat = FORMAT_SPECIFIER_PATTERN.matcher(strFormat).replaceAll(SLF4J_FORMAT_SPECIFIER);
                            List<Expression> stringFormatWithArgs = ListUtils.map(stringFormat.getArguments(), (n, arg) -> {
                                if (n == 0) {
                                    J.Literal str = (J.Literal) arg;
                                    return str.withValue(updatedStrFormat)
                                            .withValueSource("\"" + JavaStringEscapes.escapeJavaStringContent(updatedStrFormat) + "\"");
                                }
                                return arg;
                            });
                            List<Expression> originalArgsWithoutMessage = args.subList(1, args.size());
                            return method.withArguments(ListUtils.concatAll(stringFormatWithArgs, originalArgsWithoutMessage));
                        }
                        if (STRING_VALUE_OF.matches(args.get(0))) {
                            Expression valueOf = ((J.MethodInvocation) args.get(0)).getArguments().get(0);
                            if (TypeUtils.isAssignableTo(JavaType.ShallowClass.build("java.lang.Throwable"), valueOf.getType())) {
                                J.MethodInvocation m = JavaTemplate.builder("\"Exception\", #{any()}").contextSensitive().build()
                                        .apply(getCursor(), method.getCoordinates().replaceArguments(), valueOf);
                                return m.withSelect(method.getSelect());
                            }
                        } else if (OBJECT_TO_STRING.matches(args.get(0))) {
                            Expression toString = ((J.MethodInvocation) args.get(0)).getSelect();
                            if (toString != null) {
                                J.MethodInvocation m = JavaTemplate.builder("\"{}\", #{any()}").contextSensitive().build()
                                        .apply(getCursor(), method.getCoordinates().replaceArguments(), toString);
                                return m.withSelect(method.getSelect());
                            }
                        }
                    }
                }
                return super.visitMethodInvocation(method, ctx);
            }
        });
    }

    private static boolean isConvertibleFormat(String format, int argumentCount) {
        int specifierCount = 0;
        Matcher matcher = FORMAT_SPECIFIER_PATTERN.matcher(format);
        while (matcher.find()) {
            if (!SIMPLE_FORMAT_SPECIFIER_PATTERN.matcher(matcher.group()).matches()) {
                return false;
            }
            specifierCount++;
        }
        return specifierCount == argumentCount && (specifierCount == 0 || !format.contains(SLF4J_FORMAT_SPECIFIER));
    }
}
