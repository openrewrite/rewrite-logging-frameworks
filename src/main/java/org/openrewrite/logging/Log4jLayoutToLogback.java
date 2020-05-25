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
import org.openrewrite.java.refactor.ChangeType;
import org.openrewrite.java.refactor.ImplementInterface;
import org.openrewrite.java.refactor.JavaRefactorVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.util.Objects;
import java.util.Optional;

import static java.util.stream.Collectors.toList;

/**
 * Specified in the <a href="Logback migration guide">http://logback.qos.ch/manual/migrationFromLog4j.html</a>
 */
public class Log4jLayoutToLogback extends JavaRefactorVisitor {
    private final MethodMatcher getRenderedMessage = new MethodMatcher(
            "org.apache.log4j.spi.LoggingEvent getRenderedMessage()");

    @Override
    public String getName() {
        return "logging.Log4jLayoutToLogback";
    }

    @Override
    public boolean isCursored() {
        return true;
    }

    @Override
    public J visitClassDecl(J.ClassDecl classDecl) {
        J.ClassDecl c = refactor(classDecl, super::visitClassDecl);

        if (c.getExtends() != null && JavaType.Class.build("org.apache.log4j.Layout")
                .equals(c.getExtends().getFrom().getType())) {
            maybeRemoveImport("org.apache.log4j.Layout");
            maybeRemoveImport("org.apache.log4j.LoggingEvent");
            maybeAddImport("ch.qos.logback.core.LayoutBase");
            maybeAddImport("ch.qos.logback.classic.spi.ILoggingEvent");

            andThen(new ChangeType("org.apache.log4j.spi.LoggingEvent", "ch.qos.logback.classic.spi.ILoggingEvent"));

            c = c.withExtends(c.getExtends().withFrom(J.ParameterizedType.build(
                    "ch.qos.logback.core.LayoutBase",
                    "ch.qos.logback.classic.spi.ILoggingEvent")));
        }

        Optional<J.MethodDecl> ignoresThrowable = c.getMethods().stream()
                .filter(m -> m.getSimpleName().equals("ignoresThrowable")).findAny();

        Optional<J.MethodDecl> activateOptions = c.getMethods().stream()
                .filter(m -> m.getSimpleName().equals("activateOptions")).findAny();

        Optional<J.MethodDecl> format = c.getMethods().stream()
                .filter(m -> m.getSimpleName().equals("format")).findAny();

        if (ignoresThrowable.isPresent() || activateOptions.isPresent() || format.isPresent()) {
            c = c.withBody(c.getBody().withStatements(c.getBody().getStatements().stream()
                    .map(statement -> {
                        if (statement == ignoresThrowable.orElse(null)) {
                            return null;
                        } else if (statement == activateOptions.orElse(null)) {
                            J.MethodDecl activateOptionsMethod = (J.MethodDecl) statement;

                            if (activateOptionsMethod.getBody() != null &&
                                    activateOptionsMethod.getBody().getStatements().isEmpty()) {
                                return null;
                            }

                            J.ClassDecl classScope = getCursor().firstEnclosing(J.ClassDecl.class);
                            if (classScope != null) {
                                andThen(new ImplementInterface(classScope,
                                        "ch.qos.logback.core.spi.LifeCycle"));
                            }

                            return activateOptionsMethod.withName(activateOptionsMethod.getName()
                                    .withName("start"));
                        } else if (statement == format.orElse(null)) {
                            J.MethodDecl formatMethod = (J.MethodDecl) statement;
                            return formatMethod.withName(formatMethod.getName().withName("doLayout"));
                        }
                        return statement;
                    })
                    .filter(Objects::nonNull)
                    .collect(toList())));
        }

        return c;
    }

    @Override
    public J visitMethodInvocation(J.MethodInvocation method) {
        J.MethodInvocation m = refactor(method, super::visitMethodInvocation);
        if (getRenderedMessage.matches(method)) {
            m = m.withName(m.getName().withName("getMessage"));
        }
        return m;
    }
}
