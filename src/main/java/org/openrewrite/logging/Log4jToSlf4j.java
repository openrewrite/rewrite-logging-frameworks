/*
 * Copyright 2020 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.logging;

import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.refactor.ChangeMethodName;
import org.openrewrite.java.refactor.ChangeMethodTargetToStatic;
import org.openrewrite.java.refactor.ChangeType;
import org.openrewrite.java.refactor.JavaRefactorVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;
import static org.openrewrite.Formatting.EMPTY;
import static org.openrewrite.Tree.randomId;

/**
 * Use of the traditional Log4J to SLF4J bridge can result in some
 * loss of performance as the Log4j 2 Messages must be formatted
 * before they can be passed to SLF4J.
 */
public class Log4jToSlf4j extends JavaRefactorVisitor {
    private final MethodMatcher getLoggerMatcher = new MethodMatcher(
            "org.apache.log4j.Logger getLogger(..)");

    private final MethodMatcher getLoggerWithManagerMatcher = new MethodMatcher(
            "org.apache.log4j.LogManager getLogger(..)");

    private final Stack<String> loggerField = new Stack<>();

    private final List<MethodMatcher> logLevelMatchers;

    public Log4jToSlf4j() {
        logLevelMatchers = Stream.of("trace", "debug", "info", "warn", "error", "fatal")
                .map(level -> "org.apache.log4j." + (level.equals("trace") ? "Logger" : "Category") +
                        " " + level + "(..)")
                .map(MethodMatcher::new)
                .collect(Collectors.toList());
    }


    @Override
    public String getName() {
        return "logging.Log4jToSlf4j";
    }

    @Override
    public J visitCompilationUnit(J.CompilationUnit cu) {
        J c = super.visitCompilationUnit(cu);
        andThen(new ChangeType("org.apache.log4j.Logger", "org.slf4j.Logger"));
        return c;
    }

    @Override
    public J visitClassDecl(J.ClassDecl classDecl) {
        Optional<String> loggerFieldName = classDecl.findFields("org.apache.log4j.Logger").stream()
                .findAny()
                .map(field -> field.getVars().iterator().next().getSimpleName())
                .or(() -> classDecl
                        .findInheritedFields("org.apache.log4j.Logger")
                        .stream()
                        .findAny()
                        .map(JavaType.Var::getName)
                );

        loggerFieldName.ifPresent(loggerField::push);
        J c = super.visitClassDecl(classDecl);
        loggerFieldName.ifPresent(field -> loggerField.pop());
        return c;
    }

    @Override
    public J visitMethodInvocation(J.MethodInvocation method) {
        J.MethodInvocation m = refactor(method, super::visitMethodInvocation);

        if (getLoggerMatcher.matches(method) || getLoggerWithManagerMatcher.matches(method)) {
            andThen(new ChangeMethodTargetToStatic(method, "org.slf4j.LoggerFactory"));
        }

        if (logLevelMatchers.stream().anyMatch(matcher -> matcher.matches(method)) && !loggerField.isEmpty()) {
            if(method.getSimpleName().equals("fatal")) {
                andThen(new ChangeMethodName(method, "error"));
            }

            List<Expression> args = method.getArgs().getArgs();
            if (!args.isEmpty()) {
                Expression message = args.iterator().next();
                if (!isString(message.getType())) {
                    if (message.getType() instanceof JavaType.Class) {
                        List<Expression> withToString = new ArrayList<>(args);
                        withToString.set(0, new J.MethodInvocation(randomId(),
                                message,
                                null,
                                J.Ident.build(
                                        randomId(),
                                        "toString",
                                        JavaType.Primitive.String,
                                        EMPTY
                                ),
                                new J.MethodInvocation.Arguments(randomId(), emptyList(), EMPTY),
                                null,
                                EMPTY
                        ));
                        m = m.withArgs(m.getArgs().withArgs(withToString));
                    }
                } else if (message instanceof J.Binary) {
                    MessageAndArguments literalAndArgs = concatenationToLiteral(message,
                            new MessageAndArguments("", new ArrayList<>()));
                    List<Expression> fixedArgs = new ArrayList<>(args);

                    fixedArgs.set(0, J.Literal.buildString(literalAndArgs.message));

                    for (int i = literalAndArgs.arguments.size() - 1; i >= 0; i--) {
                        fixedArgs.add(1, literalAndArgs.arguments.get(i));
                    }

                    m = m.withArgs(m.getArgs().withArgs(fixedArgs));
                }
            }
        }

        return m;
    }

    private MessageAndArguments concatenationToLiteral(Expression message, MessageAndArguments result) {
        if (!(message instanceof J.Binary)) {
            result.arguments.add(message);
            return result;
        }

        J.Binary concat = (J.Binary) message;
        if (concat.getLeft() instanceof J.Binary) {
            concatenationToLiteral(concat.getLeft(), result);
        }
        else if(concat.getLeft() instanceof J.Literal) {
            result.message = ((J.Literal) concat.getLeft()).getValue() + result.message;
        }
        else {
            result.message = "{}" + result.message;
            result.arguments.add(concat.getLeft());
        }

        if (concat.getRight() instanceof J.Binary) {
            concatenationToLiteral(concat.getRight(), result);
        }
        else if(concat.getRight() instanceof J.Literal) {
            result.message += ((J.Literal) concat.getRight()).getValue();
        }
        else {
            result.message += "{}";
            result.arguments.add(concat.getRight());
        }

        return result;
    }

    private static class MessageAndArguments {
        private String message;
        private final List<Expression> arguments;

        private MessageAndArguments(String message, List<Expression> arguments) {
            this.message = message;
            this.arguments = arguments;
        }
    }

    private static boolean isString(JavaType javaType) {
        if (javaType instanceof JavaType.FullyQualified) {
            return ((JavaType.FullyQualified) javaType).getFullyQualifiedName().equals("java.lang.String");
        }
        return JavaType.Primitive.String.equals(javaType);
    }
}
