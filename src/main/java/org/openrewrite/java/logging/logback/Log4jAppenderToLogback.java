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
package org.openrewrite.java.logging.logback;

import lombok.Getter;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.concurrent.atomic.AtomicBoolean;

public class Log4jAppenderToLogback extends Recipe {
    private static final MethodMatcher GET_LAYOUT = new MethodMatcher("org.apache.log4j.AppenderSkeleton getLayout()", true);
    private static final MethodMatcher SET_LAYOUT = new MethodMatcher("org.apache.log4j.AppenderSkeleton setLayout(..)", true);

    @Getter
    final String displayName = "Migrate Log4j 2.x Appender to logback-classic equivalents";

    @Getter
    final String description = "Migrates custom Log4j 2.x Appender components to `logback-classic`. This recipe operates on the following assumptions: " +
            "1.) The contents of the `append()` method remains unchanged. " +
            "2.) The `requiresLayout()` method is not used in logback and can be removed. " +
            "3.) In logback, the `stop()` method is the equivalent of log4j's close() method. " +
            "For more details, see this page from logback: [`Migration from log4j`](http://logback.qos.ch/manual/migrationFromLog4j.html).";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>("org.apache.log4j.AppenderSkeleton", null), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                doAfterVisit(new ChangeMethodName("org.apache.log4j.Layout format(..)", "doLayout", null, null).getVisitor());
                return super.visitCompilationUnit(cu, ctx);
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);

                if (cd.getExtends() != null && cd.getExtends().getType() != null) {
                    JavaType.FullyQualified fullyQualifiedExtends = TypeUtils.asFullyQualified(cd.getExtends().getType());
                    if (fullyQualifiedExtends != null && "org.apache.log4j.AppenderSkeleton".equals(fullyQualifiedExtends.getFullyQualifiedName())) {
                        boolean usesLayout = usesInheritedLayout(cd);

                        maybeRemoveImport("org.apache.log4j.AppenderSkeleton");
                        maybeAddImport("ch.qos.logback.core.AppenderBase");
                        maybeAddImport("ch.qos.logback.classic.spi.ILoggingEvent");

                        doAfterVisit(new ChangeType("org.apache.log4j.spi.LoggingEvent", "ch.qos.logback.classic.spi.ILoggingEvent", null).getVisitor());
                        doAfterVisit(new ChangeType("org.apache.log4j.Layout", "ch.qos.logback.core.LayoutBase", null).getVisitor());

                        cd = JavaTemplate.builder("AppenderBase<ILoggingEvent>")
                                .contextSensitive()
                                .imports("ch.qos.logback.core.AppenderBase", "ch.qos.logback.classic.spi.ILoggingEvent")
                                .javaParser(JavaParser.fromJavaVersion().dependsOn(
                                        "package ch.qos.logback.core;public abstract class AppenderBase<E> { }",
                                        "package ch.qos.logback.classic.spi;public interface ILoggingEvent{ }",
                                        "package org.apache.log4j.spi;public class LoggingEvent { public String getRenderedMessage() {return null;}}"))
                                .build()
                                .apply(new Cursor(getCursor().getParent(), cd), cd.getCoordinates().replaceExtendsClause());

                        if (usesLayout) {
                            cd = addLayout(cd);
                        }
                    }

                    cd = cd.withBody(cd.getBody().withStatements(ListUtils.map(cd.getBody().getStatements(), statement -> {
                        if (statement instanceof J.MethodDeclaration) {
                            J.MethodDeclaration method = (J.MethodDeclaration) statement;
                            if ("requiresLayout".equals(method.getSimpleName())) {
                                return null;
                            }
                            if ("close".equals(method.getSimpleName())) {
                                if (method.getBody() != null && method.getBody().getStatements().isEmpty()) {
                                    return null;
                                }

                                return method.withName(method.getName().withSimpleName("stop"));
                            }
                        }
                        return statement;
                    })));

                }

                return cd;
            }

            // Logback's AppenderBase has no layout, so the class gets the field and accessors Log4j's AppenderSkeleton
            // gave it, through which Logback's configuration sets a nested <layout>
            private J.ClassDeclaration addLayout(J.ClassDeclaration cd) {
                maybeAddImport("ch.qos.logback.core.LayoutBase");
                cd = layoutTemplate("protected LayoutBase<ILoggingEvent> layout;")
                        .apply(new Cursor(getCursor().getParent(), cd), cd.getBody().getCoordinates().firstStatement());
                cd = cd.withBody(cd.getBody().withStatements(ListUtils.map(cd.getBody().getStatements(), (i, statement) ->
                        i == 1 && !statement.getPrefix().getWhitespace().startsWith("\n\n") ?
                                statement.withPrefix(statement.getPrefix().withWhitespace("\n" + statement.getPrefix().getWhitespace())) :
                                statement)));
                cd = layoutTemplate("public LayoutBase<ILoggingEvent> getLayout() {\n    return layout;\n}")
                        .apply(new Cursor(getCursor().getParent(), cd), cd.getBody().getCoordinates().lastStatement());
                return layoutTemplate("public void setLayout(LayoutBase<ILoggingEvent> layout) {\n    this.layout = layout;\n}")
                        .apply(new Cursor(getCursor().getParent(), cd), cd.getBody().getCoordinates().lastStatement());
            }
        });
    }

    private static boolean usesInheritedLayout(J.ClassDeclaration cd) {
        return new JavaIsoVisitor<AtomicBoolean>() {
            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, AtomicBoolean uses) {
                JavaType.Variable field = identifier.getFieldType();
                if ("layout".equals(identifier.getSimpleName()) && field != null &&
                    TypeUtils.isOfClassType(field.getOwner(), "org.apache.log4j.AppenderSkeleton")) {
                    uses.set(true);
                }
                return identifier;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, AtomicBoolean uses) {
                if (GET_LAYOUT.matches(method) || SET_LAYOUT.matches(method)) {
                    uses.set(true);
                }
                return super.visitMethodInvocation(method, uses);
            }
        }.reduce(cd.getBody(), new AtomicBoolean()).get();
    }

    private static JavaTemplate layoutTemplate(String code) {
        return JavaTemplate.builder(code)
                .contextSensitive()
                .imports("ch.qos.logback.core.LayoutBase", "ch.qos.logback.classic.spi.ILoggingEvent")
                .javaParser(JavaParser.fromJavaVersion().dependsOn(
                        "package ch.qos.logback.core;public abstract class AppenderBase<E> { protected abstract void append(E event); }",
                        "package ch.qos.logback.core;public abstract class LayoutBase<E> { public abstract String doLayout(E event); }",
                        "package ch.qos.logback.classic.spi;public interface ILoggingEvent{ }"))
                .build();
    }

}
