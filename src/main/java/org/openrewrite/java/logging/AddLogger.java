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
    private final J.ClassDeclaration scope;
    private final String loggerType;
    private final String factoryType;
    private final String loggerName;
    private final JavaTemplate template;

    public AddLogger(J.ClassDeclaration scope, String loggerType, String factoryType, String loggerName, Function<JavaVisitor<?>, JavaTemplate> template) {
        this.scope = scope;
        this.loggerType = loggerType;
        this.factoryType = factoryType;
        this.loggerName = loggerName;
        this.template = template.apply(this);
    }

    public static TreeVisitor<J, ExecutionContext> addLogger(J.ClassDeclaration scope, LoggingFramework loggingFramework, String loggerName) {
        switch (loggingFramework) {
            case Log4J1:
                return addLog4j1Logger(scope, loggerName);
            case Log4J2:
                return addLog4j2Logger(scope, loggerName);
            case JUL:
                return addJulLogger(scope, loggerName);
            case SLF4J:
            default:
                return addSlf4jLogger(scope, loggerName);
        }
    }

    public static AddLogger addSlf4jLogger(J.ClassDeclaration scope, String loggerName) {
        return new AddLogger(scope, "org.slf4j.Logger", "org.slf4j.LoggerFactory", loggerName, visitor ->
                JavaTemplate
                        .builder("private static final Logger #{} = LoggerFactory.getLogger(#{}.class);")
                        .contextSensitive()
                        .imports("org.slf4j.Logger", "org.slf4j.LoggerFactory")
                        .javaParser(JavaParser.fromJavaVersion().classpath("slf4j-api"))
                        .build()
        );
    }

    public static AddLogger addJulLogger(J.ClassDeclaration scope, String loggerName) {
        return new AddLogger(scope, "java.util.logging.Logger", "java.util.logging.LogManager", loggerName, visitor ->
                JavaTemplate
                        .builder("private static final Logger #{} = LogManager.getLogger(\"#{}\");")
                        .contextSensitive()
                        .imports("java.util.logging.Logger", "java.util.logging.LogManager")
                        .build()
        );
    }

    public static AddLogger addLog4j1Logger(J.ClassDeclaration scope, String loggerName) {
        return new AddLogger(scope, "org.apache.log4j.Logger", "org.apache.log4j.LogManager", loggerName, visitor ->
                JavaTemplate
                        .builder("private static final Logger #{} = LogManager.getLogger(#{}.class);")
                        .contextSensitive()
                        .imports("org.apache.log4j.Logger", "org.apache.log4j.LogManager")
                        .javaParser(JavaParser.fromJavaVersion().classpath("log4j"))
                        .build()
        );
    }

    public static AddLogger addLog4j2Logger(J.ClassDeclaration scope, String loggerName) {
        return new AddLogger(scope, "org.apache.logging.log4j.Logger", "org.apache.logging.log4j.LogManager", loggerName, visitor ->
                JavaTemplate
                        .builder("private static final Logger #{} = LogManager.getLogger(#{}.class);")
                        .contextSensitive()
                        .imports("org.apache.logging.log4j.Logger", "org.apache.logging.log4j.LogManager")
                        .javaParser(JavaParser.fromJavaVersion().classpath("log4j-api"))
                        .build()
        );
    }

    @Override
    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
        J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);

        if (cd == scope) {
            if (!FindInheritedFields.find(cd, loggerType).isEmpty() || !FindFieldsOfType.find(cd, loggerType).isEmpty()) {
                return cd;
            }

            cd = template.apply(new Cursor(getCursor().getParent(), cd), cd.getBody().getCoordinates().firstStatement(), loggerName, cd.getSimpleName());

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
        }

        return cd;
    }
}
