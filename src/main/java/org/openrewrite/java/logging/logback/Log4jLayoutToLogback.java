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
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.time.Duration;

public class Log4jLayoutToLogback extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate Log4j 2.x Layout to logback-classic equivalents";
    }

    @Override
    public String getDescription() {
        return "Migrates custom Log4j 2.x Layout components to `logback-classic`. This recipe operates on the following assumptions: " +
                "1.) A logback-classic layout must extend the `LayoutBase<ILoggingEvent>` class. " +
                "2.) log4j's `format()` is renamed to `doLayout()` in a logback-classic layout. " +
                "3.) LoggingEvent `getRenderedMessage()` is converted to LoggingEvent `getMessage()`. " +
                "4.) The log4j ignoresThrowable() method is not needed and has no equivalent in logback-classic. " +
                "5.) The activateOptions() method merits further discussion. In log4j, a layout will have its activateOptions() method invoked by log4j configurators, that is PropertyConfigurator or DOMConfigurator just after all the options of the layout have been set. Thus, the layout will have an opportunity to check that its options are coherent and if so, proceed to fully initialize itself. " +
                "6.) In logback-classic, layouts must implement the LifeCycle interface which includes a method called start(). The start() method is the equivalent of log4j's activateOptions() method. " +
                "For more details, see this page from logback: [`Migration from log4j`](http://logback.qos.ch/manual/migrationFromLog4j.html).";
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(5);
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new UsesType<>("org.apache.log4j.Layout");
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                doAfterVisit(new ChangeMethodName("org.apache.log4j.Layout format(..)", "doLayout", true, null));
                doAfterVisit(new ChangeMethodName("org.apache.log4j.spi.LoggingEvent getRenderedMessage()", "getMessage", true, null));
                return super.visitCompilationUnit(cu, ctx);
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                if (cd.getExtends() != null && cd.getExtends().getType() != null) {
                    JavaType.FullyQualified fullyQualifiedExtends = TypeUtils.asFullyQualified(cd.getExtends().getType());
                    if (fullyQualifiedExtends != null && "org.apache.log4j.Layout".equals(fullyQualifiedExtends.getFullyQualifiedName())) {

                        maybeRemoveImport("org.apache.log4j.Layout");
                        maybeRemoveImport("org.apache.log4j.LoggingEvent");
                        maybeAddImport("ch.qos.logback.core.LayoutBase");
                        maybeAddImport("ch.qos.logback.classic.spi.ILoggingEvent");

                        doAfterVisit(new ChangeType("org.apache.log4j.spi.LoggingEvent", "ch.qos.logback.classic.spi.ILoggingEvent", null));

                        cd = cd.withTemplate(
                                JavaTemplate.builder(this::getCursor, "LayoutBase<ILoggingEvent>")
                                        .imports("ch.qos.logback.core.LayoutBase", "ch.qos.logback.classic.spi.ILoggingEvent")
                                        .javaParser(() -> JavaParser.fromJavaVersion().dependsOn(
                                                "package ch.qos.logback.classic.spi;public interface ILoggingEvent{ }",
                                                "package org.apache.log4j.spi;public class LoggingEvent { public String getRenderedMessage() {return null;}}").build())
                                        .build(),
                                cd.getCoordinates().replaceExtendsClause()
                        );

                        // should be covered by maybeAddImport, todo
                        doAfterVisit(new AddImport<>("ch.qos.logback.core.LayoutBase", null, false));
                    }

                    cd = cd.withBody(cd.getBody().withStatements(ListUtils.map(cd.getBody().getStatements(), statement -> {
                        if (statement instanceof J.MethodDeclaration) {
                            J.MethodDeclaration method = (J.MethodDeclaration) statement;
                            if ("ignoresThrowable".equals(method.getSimpleName())) {
                                return null;
                            } else if ("activateOptions".equals(method.getSimpleName())) {
                                if (method.getBody() != null && method.getBody().getStatements().isEmpty()) {
                                    return null;
                                }
                                J.ClassDeclaration enclosingClass = getCursor().firstEnclosing(J.ClassDeclaration.class);
                                assert enclosingClass != null;
                                doAfterVisit(new ImplementInterface<>(enclosingClass, "ch.qos.logback.core.spi.LifeCycle"));
                                return method.withName(method.getName().withSimpleName("start"));
                            }
                        }
                        return statement;
                    })));

                }

                return cd;

            }

        };
    }

}
