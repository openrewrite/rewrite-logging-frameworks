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
package org.openrewrite.java.logging;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.format.AutoFormatVisitor;
import org.openrewrite.java.search.FindFieldsOfType;
import org.openrewrite.java.search.FindInheritedFields;
import org.openrewrite.java.tree.J;

import java.util.function.Function;

/**
 * @author Edward Harman
 */
public class AddLogger extends JavaIsoVisitor<ExecutionContext> {
    private final String loggerType;
    private final String factoryType;
    private final JavaTemplate template;

    public AddLogger(String loggerType, String factoryType, Function<JavaVisitor<?>, JavaTemplate> template) {
        this.loggerType = loggerType;
        this.factoryType = factoryType;
        this.template = template.apply(this);
    }

    public static TreeVisitor<J, ExecutionContext> maybeAddLogger(Cursor cursor, LoggingFramework loggingFramework) {
        AddLogger logger;
        switch(loggingFramework) {
            case Log4J1:
                logger = addLog4j1Logger();
                break;
            case Log4J2:
                logger = addLog4j2Logger();
                break;
            case JUL:
                logger = addJulLogger();
                break;
            case SLF4J:
            default:
                logger = addSlf4jLogger();
                break;
        }

        J.ClassDeclaration cd = cursor.firstEnclosingOrThrow(J.ClassDeclaration.class);
        return FindInheritedFields.find(cd, logger.loggerType).isEmpty() && FindFieldsOfType.find(cd, logger.loggerType).isEmpty() ?
                logger :
                TreeVisitor.noop();
    }

    public static TreeVisitor<J, ExecutionContext> maybeAddLogger(Cursor cursor, AddLogger logger) {
        J.ClassDeclaration cd = cursor.firstEnclosingOrThrow(J.ClassDeclaration.class);
        return FindInheritedFields.find(cd, logger.loggerType).isEmpty() && FindFieldsOfType.find(cd, logger.loggerType).isEmpty() ?
                logger :
                TreeVisitor.noop();
    }

    public static AddLogger addSlf4jLogger() {
        return new AddLogger("org.slf4j.Logger", "org.slf4j.LoggerFactory", visitor ->
                JavaTemplate
                        .builder(visitor::getCursor, "private static final Logger LOGGER = LoggerFactory.getLogger(#{}.class);")
                        .imports("org.slf4j.Logger", "org.slf4j.LoggerFactory")
                        .javaParser(() -> JavaParser.fromJavaVersion()
                                .classpath("slf4j-api")
                                .build())
                        .build()
        );
    }

    public static AddLogger addJulLogger() {
        return new AddLogger("java.util.logging.Logger", "java.util.logging.LogManager", visitor ->
                JavaTemplate
                        .builder(visitor::getCursor, "private static final Logger LOGGER = LogManager.getLogger(\"#{}\");")
                        .imports("java.util.logging.Logger", "java.util.logging.LogManager")
                        .build()
        );
    }

    public static AddLogger addLog4j1Logger() {
        return new AddLogger("org.apache.log4j.Logger", "org.apache.log4j.LogManager", visitor ->
                JavaTemplate
                        .builder(visitor::getCursor, "private static final Logger LOGGER = LogManager.getLogger(#{}.class);")
                        .imports("org.apache.log4j.Logger", "org.apache.log4j.LogManager")
                        .javaParser(() -> JavaParser.fromJavaVersion()
                                .classpath("log4j")
                                .build())
                        .build()
        );
    }

    public static AddLogger addLog4j2Logger() {
        return new AddLogger("org.apache.logging.log4j.Logger", "org.apache.logging.log4j.LogManager", visitor ->
                JavaTemplate
                        .builder(visitor::getCursor, "private static final Logger LOGGER = LogManager.getLogger(#{}.class);")
                        .imports("org.apache.logging.log4j.Logger", "org.apache.logging.log4j.LogManager")
                        .javaParser(() -> JavaParser.fromJavaVersion()
                                .classpath("log4j-api")
                                .build())
                        .build()
        );
    }

    @Override
    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
        J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
        cd = cd.withTemplate(template, cd.getBody().getCoordinates().firstStatement(), cd.getSimpleName());

        // ensure the appropriate number of blank lines on next statement after new field
        J.ClassDeclaration formatted = (J.ClassDeclaration) new AutoFormatVisitor<ExecutionContext>().visitNonNull(cd, ctx, getCursor());
        cd = cd.withBody(cd.getBody().withStatements(ListUtils.map(cd.getBody().getStatements(), (i, stat) -> {
            if (i == 1) {
                return stat.withPrefix(formatted.getBody().getStatements().get(i).getPrefix());
            }
            return stat;
        })));

        maybeAddImport(loggerType);
        maybeAddImport(factoryType);
        return cd;
    }
}
