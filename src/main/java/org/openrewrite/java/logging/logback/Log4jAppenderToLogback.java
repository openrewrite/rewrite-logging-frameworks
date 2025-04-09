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

import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

public class Log4jAppenderToLogback extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate Log4j 2.x Appender to logback-classic equivalents";
    }

    @Override
    public String getDescription() {
        return "Migrates custom Log4j 2.x Appender components to `logback-classic`. This recipe operates on the following assumptions: " +
               "1.) The contents of the `append()` method remains unchanged. " +
               "2.) The `requiresLayout()` method is not used in logback and can be removed. " +
               "3.) In logback, the `stop()` method is the equivalent of log4j's close() method. " +
               "For more details, see this page from logback: [`Migration from log4j`](http://logback.qos.ch/manual/migrationFromLog4j.html).";
    }

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

                        maybeRemoveImport("org.apache.log4j.AppenderSkeleton");
                        maybeAddImport("ch.qos.logback.core.AppenderBase");
                        maybeAddImport("ch.qos.logback.classic.spi.ILoggingEvent");

                        doAfterVisit(new ChangeType("org.apache.log4j.spi.LoggingEvent", "ch.qos.logback.classic.spi.ILoggingEvent", null, null).getVisitor());
                        doAfterVisit(new ChangeType("org.apache.log4j.Layout", "ch.qos.logback.core.LayoutBase", null, null).getVisitor());

                        cd = JavaTemplate.builder("AppenderBase<ILoggingEvent>")
                                .contextSensitive()
                                .imports("ch.qos.logback.core.AppenderBase", "ch.qos.logback.classic.spi.ILoggingEvent")
                                .javaParser(JavaParser.fromJavaVersion().dependsOn(
                                        "package ch.qos.logback.classic.spi;public interface ILoggingEvent{ }",
                                        "package org.apache.log4j.spi;public class LoggingEvent { public String getRenderedMessage() {return null;}}"))
                                .build()
                                .apply(new Cursor(getCursor().getParent(), cd), cd.getCoordinates().replaceExtendsClause());

                        // should be covered by maybeAddImport, todo
                        doAfterVisit(new AddImport<>("ch.qos.logback.core.AppenderBase", null, false));
                    }

                    cd = cd.withBody(cd.getBody().withStatements(ListUtils.map(cd.getBody().getStatements(), statement -> {
                        if (statement instanceof J.MethodDeclaration) {
                            J.MethodDeclaration method = (J.MethodDeclaration) statement;
                            if ("requiresLayout".equals(method.getSimpleName())) {
                                return null;
                            } else if ("close".equals(method.getSimpleName())) {
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
        });
    }

}
