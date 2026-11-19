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

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.format.AutoFormatVisitor;
import org.openrewrite.java.trait.Scope;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Comparator;
import java.util.Optional;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

/**
 * @author Edward Harman
 */
public class AddLogger extends JavaIsoVisitor<ExecutionContext> {
    private final J.ClassDeclaration scope;
    private final String loggerType;
    private final String factoryType;
    private final String loggerName;
    private final Function<String, JavaTemplate> template;

    /**
     * @param template Builds the field's template from the way to write the logger type, which is its simple name,
     *                 or its qualified name where the simple name means another type in the class.
     */
    public AddLogger(J.ClassDeclaration scope, String loggerType, String factoryType, String loggerName, Function<String, JavaTemplate> template) {
        this.scope = scope;
        this.loggerType = loggerType;
        this.factoryType = factoryType;
        this.loggerName = loggerName;
        this.template = template;
    }

    public static TreeVisitor<J, ExecutionContext> addLogger(J.ClassDeclaration scope, LoggingFramework loggingFramework, String loggerName, ExecutionContext ctx) {
        switch (loggingFramework) {
            case Log4J1:
                return addLog4j1Logger(scope, loggerName, ctx);
            case Log4J2:
                return addLog4j2Logger(scope, loggerName, ctx);
            case JUL:
                return addJulLogger(scope, loggerName, ctx);
            case SYSTEM:
                return addSystemLogger(scope, loggerName, ctx);
            case SLF4J:
            default:
                return addSlf4jLogger(scope, loggerName, ctx);
        }
    }

    public static AddLogger addSlf4jLogger(J.ClassDeclaration scope, String loggerName, ExecutionContext ctx) {
        return new AddLogger(scope, "org.slf4j.Logger", "org.slf4j.LoggerFactory", loggerName, type ->
                JavaTemplate
                        .builder(getModifiers(scope) + " " + type + " #{} = LoggerFactory.getLogger(#{}.class);")
                        .contextSensitive()
                        .imports("org.slf4j.Logger", "org.slf4j.LoggerFactory")
                        .javaParser(JavaParser.fromJavaVersion()
                                .classpathFromResources(ctx, "slf4j-api-2"))
                        .build()
        );
    }

    public static AddLogger addJulLogger(J.ClassDeclaration scope, String loggerName, @SuppressWarnings("unused") ExecutionContext ctx) {
        return new AddLogger(scope, "java.util.logging.Logger", "java.util.logging.LogManager", loggerName, type ->
                JavaTemplate
                        .builder(getModifiers(scope) + " " + type + " #{} = LogManager.getLogManager().getLogger(\"#{}\");")
                        .contextSensitive()
                        .imports("java.util.logging.Logger", "java.util.logging.LogManager")
                        .build()
        );
    }

    public static AddLogger addSystemLogger(J.ClassDeclaration scope, String loggerName, @SuppressWarnings("unused") ExecutionContext ctx) {
        return new AddLogger(scope, "java.lang.System.Logger", "java.lang.System", loggerName, type ->
                JavaTemplate
                        .builder(getModifiers(scope) + " " + type + " #{} = System.getLogger(\"#{}\");")
                        .contextSensitive()
                        .imports("java.lang.System.Logger")
                        .build()
        );
    }

    public static AddLogger addLog4j1Logger(J.ClassDeclaration scope, String loggerName, ExecutionContext ctx) {
        return new AddLogger(scope, "org.apache.log4j.Logger", "org.apache.log4j.LogManager", loggerName, type ->
                JavaTemplate
                        .builder(getModifiers(scope) + " " + type + " #{} = LogManager.getLogger(#{}.class);")
                        .contextSensitive()
                        .imports("org.apache.log4j.Logger", "org.apache.log4j.LogManager")
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "log4j-1.2.+"))
                        .build()
        );
    }

    public static AddLogger addLog4j2Logger(J.ClassDeclaration scope, String loggerName, ExecutionContext ctx) {
        return new AddLogger(scope, "org.apache.logging.log4j.Logger", "org.apache.logging.log4j.LogManager", loggerName, type ->
                JavaTemplate
                        .builder(getModifiers(scope) + " " + type + " #{} = LogManager.getLogger(#{}.class);")
                        .contextSensitive()
                        .imports("org.apache.logging.log4j.Logger", "org.apache.logging.log4j.LogManager")
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "log4j-api-2.+"))
                        .build()
        );
    }

    private static String getModifiers(J.ClassDeclaration scope) {
        boolean innerClass = scope.getType() != null && scope.getType().getOwningClass() != null;
        return innerClass && !scope.hasModifier(J.Modifier.Type.Static) ? "private final" : "private static final";
    }

    /**
     * Whether the class has a logger of the type among its own members, declared or inherited, so that it gets no
     * other. The logger of a nested class does not count, as the class cannot call it by its simple name.
     */
    static boolean hasLogger(Cursor classCursor, String loggerType) {
        return Scope.at(classCursor).getBindings().stream().anyMatch(binding ->
                binding.getNamespace() == Scope.Namespace.VARIABLE && TypeUtils.isAssignableTo(loggerType, binding.getType()));
    }

    @Override
    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
        J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);

        if (cd == scope) {
            if (hasLogger(getCursor(), loggerType)) {
                return cd;
            }

            //noinspection ComparatorMethodParameterNotUsed
            Comparator<Statement> firstAfterEnumValueSet = (unused, o2) -> o2 instanceof J.EnumValueSet ? 1 : -1;
            // Asked from the body, where the members the class declares and inherits are in scope
            Scope body = Scope.at(new Cursor(getCursor(), cd.getBody()));
            // A name that code only refers to is kept, as a caller may have written those references for this field
            boolean bound = body.lookup(loggerName).isPresent() || Scope.declaredWithin(getCursor()).anyMatch(binding ->
                    binding.getNamespace() == Scope.Namespace.VARIABLE && binding.getName().equals(loggerName));
            String name = bound ? body.freeName(loggerName) : loggerName;
            String simpleName = loggerType.substring(loggerType.lastIndexOf('.') + 1);
            boolean qualified = namesAnotherType(simpleName, body);
            String type = !qualified ? simpleName :
                    loggerType.startsWith("java.lang.") ? loggerType.substring("java.lang.".length()) : loggerType;
            cd = template.apply(type).apply(updateCursor(cd), cd.getBody().getCoordinates().addStatement(firstAfterEnumValueSet), name, cd.getSimpleName());

            // ensure the appropriate number of blank lines on next statement after new field
            J.ClassDeclaration formatted = (J.ClassDeclaration) new AutoFormatVisitor<ExecutionContext>().visitNonNull(cd, ctx, requireNonNull(getCursor().getParent()));
            cd = cd.withBody(cd.getBody().withStatements(ListUtils.map(cd.getBody().getStatements(), (i, stat) -> {
                if (i == 1) {
                    return stat.withPrefix(formatted.getBody().getStatements().get(i).getPrefix());
                }
                return stat;
            })));

            if (!qualified) {
                maybeAddImport(loggerType);
            }
            maybeAddImport(factoryType);
        }

        return cd;
    }

    private boolean namesAnotherType(String simpleName, Scope body) {
        Optional<Scope.Binding> bound = body.lookup(simpleName, Scope.Namespace.TYPE);
        if (bound.isPresent()) {
            return !TypeUtils.isOfClassType(bound.get().getType(), loggerType);
        }
        // A type of the same package or from an on-demand import is bound by no scope. An import of the logger type
        // would hide it from the code that uses it.
        for (JavaType type : getCursor().firstEnclosingOrThrow(JavaSourceFile.class).getTypesInUse().getTypesInUse()) {
            if (type instanceof JavaType.FullyQualified && !TypeUtils.isOfClassType(type, loggerType)) {
                String className = ((JavaType.FullyQualified) type).getClassName();
                if (className.substring(className.lastIndexOf('.') + 1).equals(simpleName)) {
                    return true;
                }
            }
        }
        return false;
    }
}
