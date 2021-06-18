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
import org.openrewrite.java.AddImport;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
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
 */
public class Log4jAppenderToLogback extends Recipe {
    private static final MethodMatcher MATCHER = new MethodMatcher("org.apache.log4j.Layout format(..)");

    @Override // todo
    public String getDisplayName() {
        return "Log4jAppenderToLogback";
    }

    @Override // todo
    public String getDescription() {
        return "TODO.";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new UsesType<>("org.apache.log4j.AppenderSkeleton");
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new Log4jAppenderToLogbackVisitor();
    }

    public static class Log4jAppenderToLogbackVisitor extends JavaIsoVisitor<ExecutionContext> {
        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
            J.ClassDeclaration c = super.visitClassDeclaration(classDecl, ctx);

            if (c.getExtends() != null &&
                    JavaType.Class.build("org.apache.log4j.AppenderSkeleton").equals(c.getExtends().getType())) {
                maybeRemoveImport("org.apache.log4j.AppenderSkeleton");
                maybeAddImport("ch.qos.logback.core.AppenderBase");
                maybeAddImport("ch.qos.logback.classic.spi.ILoggingEvent");

                doAfterVisit(new ChangeType("org.apache.log4j.spi.LoggingEvent", "ch.qos.logback.classic.spi.ILoggingEvent"));
                doAfterVisit(new ChangeType("org.apache.log4j.Layout", "ch.qos.logback.core.LayoutBase"));

                c = c.withTemplate(
                        template("AppenderBase<ILoggingEvent>")
                                .imports("ch.qos.logback.core.AppenderBase", "ch.qos.logback.classic.spi.ILoggingEvent")
                                .build(),
                        c.getCoordinates().replaceExtendsClause()
                );

                // should not be required, should be covered by maybeAddImport, will evaluate todo
                doAfterVisit(new AddImport<>("ch.qos.logback.core.AppenderBase", null, false));

            }

            Collection<J.MethodDeclaration> classDeclaredMethods = c.getBody().getStatements().stream()
                    .filter(J.MethodDeclaration.class::isInstance)
                    .map(J.MethodDeclaration.class::cast)
                    .collect(Collectors.toList());

            Optional<J.MethodDeclaration> requiresLayout = classDeclaredMethods.stream()
                    .filter(m -> m.getSimpleName().equals("requiresLayout"))
                    .findAny();

            Optional<J.MethodDeclaration> close = classDeclaredMethods.stream()
                    .filter(m -> m.getSimpleName().equals("close"))
                    .findAny();

            if (requiresLayout.isPresent() || close.isPresent()) {
                c = c.withBody(c.getBody().withStatements(c.getBody().getStatements().stream()
                        .map(statement -> {
                            if (statement == requiresLayout.orElse(null)) {
                                return null;
                            } else if (statement == close.orElse(null)) {
                                J.MethodDeclaration closeMethod = (J.MethodDeclaration) statement;

                                if (closeMethod.getBody() != null && closeMethod.getBody().getStatements().isEmpty()) {
                                    return null;
                                }

                                return closeMethod.withName(closeMethod.getName().withName("stop"));
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
                m = m.withName(m.getName().withName("doLayout"));
            }
            return m;
        }
    }

}
