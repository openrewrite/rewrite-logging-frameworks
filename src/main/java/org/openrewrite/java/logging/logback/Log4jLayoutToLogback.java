/*
 * Copyright 2021 the original author or authors.
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
package org.openrewrite.java.logging.logback;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

/**
 * Should evaluate what all this does and whether it can be broken down into discrete recipes
 * TODO
 * <p>
 *
 * @see <a href="http://logback.qos.ch/manual/migrationFromLog4j.html">Logback migration guide</a>
 */
public class Log4jLayoutToLogback extends Recipe {
    private static final MethodMatcher MATCHER = new MethodMatcher("org.apache.log4j.spi.LoggingEvent getRenderedMessage()");

    @Override // todo
    public String getDisplayName() {
        return "Log4jLayoutToLogback";
    }

    @Override // todo
    public String getDescription() {
        return "TODO.";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new UsesType<>("org.apache.log4j.Layout");
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new Log4jLayoutToLogbackVisitor();
    }

    public static class Log4jLayoutToLogbackVisitor extends JavaIsoVisitor<ExecutionContext> {
        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
            J.ClassDeclaration c = super.visitClassDeclaration(classDecl, ctx);

            if (c.getExtends() != null
                    && JavaType.Class.build("org.apache.log4j.Layout").equals(c.getExtends().getType())) {
                maybeRemoveImport("org.apache.log4j.Layout");
                maybeRemoveImport("org.apache.log4j.LoggingEvent");
                maybeAddImport("ch.qos.logback.core.LayoutBase");
                maybeAddImport("ch.qos.logback.classic.spi.ILoggingEvent");

                doAfterVisit(new ChangeType("org.apache.log4j.spi.LoggingEvent", "ch.qos.logback.classic.spi.ILoggingEvent"));

                c = c.withTemplate(
                        template("LayoutBase<ILoggingEvent>")
                                .imports("ch.qos.logback.core.LayoutBase", "ch.qos.logback.classic.spi.ILoggingEvent")
                                .build(),
                        c.getCoordinates().replaceExtendsClause()
                );

                // should not be required, should be covered by maybeAddImport, will evaluate todo
                doAfterVisit(new AddImport<>("ch.qos.logback.core.LayoutBase", null, false));

            }

            Collection<J.MethodDeclaration> classDeclaredMethods = c.getBody().getStatements().stream()
                    .filter(J.MethodDeclaration.class::isInstance)
                    .map(J.MethodDeclaration.class::cast)
                    .collect(Collectors.toList());

            Optional<J.MethodDeclaration> ignoresThrowable = classDeclaredMethods.stream()
                    .filter(m -> m.getSimpleName().equals("ignoresThrowable")).findAny();

            Optional<J.MethodDeclaration> activateOptions = classDeclaredMethods.stream()
                    .filter(m -> m.getSimpleName().equals("activateOptions")).findAny();

            Optional<J.MethodDeclaration> format = classDeclaredMethods.stream()
                    .filter(m -> m.getSimpleName().equals("format")).findAny();

            if (ignoresThrowable.isPresent() || activateOptions.isPresent() || format.isPresent()) {
                c = c.withBody(c.getBody().withStatements(c.getBody().getStatements().stream()
                        .map(statement -> {
                            if (statement == ignoresThrowable.orElse(null)) {
                                return null;
                            } else if (statement == activateOptions.orElse(null)) {
                                J.MethodDeclaration activateOptionsMethod = (J.MethodDeclaration) statement;

                                if (activateOptionsMethod.getBody() != null &&
                                        activateOptionsMethod.getBody().getStatements().isEmpty()) {
                                    return null;
                                }

                                J.ClassDeclaration classScope = getCursor().firstEnclosing(J.ClassDeclaration.class);
                                if (classScope != null) {
                                    doAfterVisit(new ImplementInterface<>(classScope,
                                            "ch.qos.logback.core.spi.LifeCycle"));
                                }

                                return activateOptionsMethod.withName(activateOptionsMethod.getName()
                                        .withName("start"));
                            } else if (statement == format.orElse(null)) {
                                J.MethodDeclaration formatMethod = (J.MethodDeclaration) statement;
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
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
            if (MATCHER.matches(method)) {
                m = m.withName(m.getName().withName("getMessage"));
            }
            return m;
        }

    }

}
