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
package org.openrewrite.java.logging;

import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Tree;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.trait.Scope;
import org.openrewrite.java.tree.Flag;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.Markers;

import static java.util.Collections.emptyList;

public enum LoggingFramework {
    SLF4J("org.slf4j.Logger"),
    Log4J1("org.apache.log4j.Logger"),
    Log4J2("org.apache.logging.log4j.Logger"),
    JUL("java.util.logging.Logger"),
    COMMONS("org.apache.commons.logging.Log"),
    SYSTEM("java.lang.System.Logger");

    private final String loggerType;

    LoggingFramework(String loggerType) {
        this.loggerType = loggerType;
    }

    public String getLoggerType() {
        return loggerType;
    }

    public static LoggingFramework fromOption(@Nullable String option) {
        if (option != null) {
            for (LoggingFramework value : values()) {
                if (value.toString().equalsIgnoreCase(option)) {
                    return value;
                }
            }
        }
        return SLF4J;
    }

    /**
     * A reference to the field holding this framework's logger that code at the cursor can call by its simple name,
     * from the innermost class first. The field may be inherited or belong to a class around the cursor's.
     */
    J.@Nullable Identifier loggerAt(Cursor cursor) {
        return Scope.at(cursor).visible()
                .filter(binding -> binding.getNamespace() == Scope.Namespace.VARIABLE && !binding.isLocal() &&
                                   binding.getType() instanceof JavaType.Variable)
                .filter(binding -> {
                    JavaType.Variable field = (JavaType.Variable) binding.getType();
                    return TypeUtils.isAssignableTo(loggerType, field.getType()) &&
                           (field.hasFlags(Flag.Static) || hasInstanceOf(cursor, binding.getScope().getTree()));
                })
                .map(binding -> {
                    JavaType.Variable field = (JavaType.Variable) binding.getType();
                    return new J.Identifier(Tree.randomId(), Space.EMPTY, Markers.EMPTY, emptyList(), binding.getName(), field.getType(), field);
                })
                .findFirst()
                .orElse(null);
    }

    /**
     * Whether code at the cursor runs with an instance of the class around it, whose instance fields it can then
     * read. Scope reports such a field visible from a static context too.
     */
    private static boolean hasInstanceOf(Cursor cursor, J enclosingClass) {
        for (Cursor c = cursor; c.getValue() != enclosingClass; c = c.getParentTreeCursor()) {
            Object tree = c.getValue();
            if (tree instanceof J.MethodDeclaration && ((J.MethodDeclaration) tree).hasModifier(J.Modifier.Type.Static) ||
                tree instanceof J.VariableDeclarations && ((J.VariableDeclarations) tree).hasModifier(J.Modifier.Type.Static) ||
                tree instanceof J.Block && ((J.Block) tree).isStatic() ||
                tree instanceof J.ClassDeclaration && (((J.ClassDeclaration) tree).hasModifier(J.Modifier.Type.Static) ||
                                                       ((J.ClassDeclaration) tree).getKind() != J.ClassDeclaration.Kind.Type.Class)) {
                return false;
            }
        }
        return true;
    }

    public JavaTemplate getErrorTemplate(String message, ExecutionContext ctx) {
        switch (this) {
            case SLF4J:
                return JavaTemplate
                        .builder("#{any(org.slf4j.Logger)}.error(" + message + ", #{any(java.lang.Throwable)})")
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "slf4j-api-2"))
                        .build();
            case Log4J1:
                return JavaTemplate
                        .builder("#{any(org.apache.log4j.Category)}.error(" + message + ", #{any(java.lang.Throwable)})")
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "log4j-1.2.+"))
                        .build();

            case Log4J2:
                return JavaTemplate
                        .builder("#{any(org.apache.logging.log4j.Logger)}.error(" + message + ", #{any(java.lang.Throwable)})")
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "log4j-api-2.+"))
                        .build();
            case COMMONS:
                return JavaTemplate
                        .builder("#{any(org.apache.commons.logging.Log)}.error(" + message + ", #{any(java.lang.Throwable)})")
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "commons-logging-1"))
                        .build();
            case SYSTEM:
                return JavaTemplate
                        .builder("#{any(java.lang.System.Logger)}.log(Level.ERROR, " + message + ", #{any(java.lang.Throwable)})")
                        .imports("java.lang.System.Logger.Level")
                        .build();
            case JUL:
            default:
                return JavaTemplate
                        .builder("#{any(java.util.logging.Logger)}.log(Level.SEVERE, " + message + ", #{any(java.lang.Throwable)})")
                        .imports("java.util.logging.Level")
                        .build();
        }
    }
}
