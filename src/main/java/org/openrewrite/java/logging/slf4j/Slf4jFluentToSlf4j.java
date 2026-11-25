/*
 * Copyright 2026 the original author or authors.
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
package org.openrewrite.java.logging.slf4j;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.java.*;
import org.openrewrite.java.dependencies.AddDependency;
import org.openrewrite.java.dependencies.ChangeDependency;
import org.openrewrite.java.marker.JavaProject;
import org.openrewrite.java.logging.internal.LogArguments;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.SearchResult;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Value
@EqualsAndHashCode(callSuper = false)
public class Slf4jFluentToSlf4j extends ScanningRecipe<Slf4jFluentToSlf4j.Accumulator> {
    private static final String LOGGER = "org.fissore.slf4j.FluentLogger";
    private static final String FACTORY = "org.fissore.slf4j.FluentLoggerFactory";
    private static final String BUILDER = "org.fissore.slf4j.LoggerAtLevel";
    private static final MethodMatcher LAZY = new MethodMatcher("org.fissore.slf4j.Util lazy(..)");
    private static final MethodMatcher LOG = new MethodMatcher(BUILDER + " log(..)");
    private static final MethodMatcher LEVEL = new MethodMatcher(LOGGER + " *()");
    private static final MethodMatcher WITH_CAUSE = new MethodMatcher(BUILDER + " withCause(..)");
    private static final MethodMatcher WITH_MARKER = new MethodMatcher(BUILDER + " withMarker(..)");
    private static final String MANUAL = "This slf4j-fluent usage requires manual migration before rerunning the recipe";

    String displayName = "Migrate Fissore SLF4J Fluent to SLF4J";

    String description = "Migrate `org.fissore:slf4j-fluent` to `org.slf4j:slf4j-api` 2.x, preserving lazy messages, " +
            "lazy arguments and exception causes. SLF4J 1.x providers in the migrated modules and in the modules depending on them " +
            "are moved to their SLF4J 2.x counterparts: `org.slf4j` artifacts to 2.x, `slf4j-log4j12` to `slf4j-reload4j`, " +
            "`log4j-slf4j-impl` to `log4j-slf4j2-impl` and Logback to 1.3.x (Java 8) or 1.5.x (Java 11+). " +
            "Rate limiting, marker-specific level checks, reusable marker mutations, argument arrays, arguments that may hold " +
            "a `Supplier` at runtime, side effects around suppliers, subclasses and unsupported API usages are marked for manual migration, " +
            "as are Spring Boot 2.x, `slf4j-jcl` and SLF4J 1.x providers whose version is not declared in the module's own build file. " +
            "If any such usages are found, source and dependency migration is withheld for the entire recipe run.";

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator(ctx);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        List<TreeVisitor<?, ExecutionContext>> dependencies = Arrays.asList(
                acc.dependency.getScanner(acc.dependencies),
                acc.addition.getScanner(acc.additions),
                acc.providers.scanner());
        JavaVisitor<ExecutionContext> unsupported = unsupported(() -> acc.unsupported = true);
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree instanceof SourceFile) {
                    for (TreeVisitor<?, ExecutionContext> dependency : dependencies) {
                        if (dependency.isAcceptable((SourceFile) tree, ctx)) {
                            dependency.visit(tree, ctx);
                        }
                    }
                }
                if (tree instanceof JavaSourceFile && usesFluent((JavaSourceFile) tree)) {
                    if (tree instanceof J.CompilationUnit) {
                        unsupported.visit(tree, ctx);
                        ((J.CompilationUnit) tree).getMarkers().findFirst(JavaProject.class).ifPresent(acc.projects::add);
                    } else {
                        acc.unsupported = true;
                    }
                }
                return tree;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        Set<JavaProject> affected = acc.providers.affected(acc.projects);
        Map<JavaProject, String> problems = acc.providers.problems(affected);
        if (acc.unsupported || !problems.isEmpty()) {
            JavaVisitor<ExecutionContext> unsupported = unsupported(() -> { });
            return new TreeVisitor<Tree, ExecutionContext>() {
                @Override
                public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                    if (tree instanceof SourceFile && Slf4jFluentProviders.isBuildFile((SourceFile) tree)) {
                        String problem = ((SourceFile) tree).getMarkers().findFirst(JavaProject.class).map(problems::get).orElse(null);
                        return problem == null ? tree : SearchResult.found(tree, problem);
                    }
                    if (!(tree instanceof JavaSourceFile) || !usesFluent((JavaSourceFile) tree)) {
                        return tree;
                    }
                    return tree instanceof J.CompilationUnit ? unsupported.visit(tree, ctx) : SearchResult.found(tree, MANUAL);
                }
            };
        }
        List<TreeVisitor<?, ExecutionContext>> dependencies = new ArrayList<>();
        dependencies.add(acc.dependency.getVisitor(acc.dependencies));
        dependencies.add(acc.addition.getVisitor(acc.additions));
        TreeVisitor<?, ExecutionContext> providers = acc.providers.visitor();
        JavaVisitor<ExecutionContext> java = migrate();
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree instanceof J.CompilationUnit) {
                    return usesFluent((J.CompilationUnit) tree) ? java.visit(tree, ctx) : tree;
                }
                if (tree instanceof SourceFile) {
                    for (TreeVisitor<?, ExecutionContext> dependency : dependencies) {
                        if (dependency.isAcceptable((SourceFile) tree, ctx)) {
                            tree = dependency.visit(tree, ctx);
                        }
                    }
                    SourceFile sourceFile = (SourceFile) tree;
                    if (Slf4jFluentProviders.isBuildFile(sourceFile) &&
                            sourceFile.getMarkers().findFirst(JavaProject.class).map(affected::contains).orElse(false)) {
                        tree = providers.visit(sourceFile, ctx);
                    }
                }
                return tree;
            }
        };
    }

    private static JavaVisitor<ExecutionContext> unsupported(Runnable found) {
        return new JavaVisitor<ExecutionContext>() {
            private <T extends Tree> T mark(T tree) {
                found.run();
                return SearchResult.found(tree, MANUAL);
            }

            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
                JavaType.Method type = method.getMethodType();
                if (type == null || !type.getDeclaringType().getFullyQualifiedName().startsWith("org.fissore.slf4j.")) {
                    return m;
                }
                String owner = type.getDeclaringType().getFullyQualifiedName();
                boolean supported = FACTORY.equals(owner) && "getLogger".equals(method.getSimpleName()) ||
                        LOGGER.equals(owner) && isLevel(method.getSimpleName()) && method.getArguments().get(0) instanceof J.Empty ||
                        BUILDER.equals(owner) && ("withCause".equals(method.getSimpleName()) || LOG.matches(method)) ||
                        BUILDER.equals(owner) && "withMarker".equals(method.getSimpleName()) && freshBuilder(method.getSelect(), false);
                if (LOG.matches(method)) {
                    List<Expression> args = method.getArguments();
                    if (args.size() == 2 && (args.get(1).getType() instanceof JavaType.Array || isNull(args.get(1)))) {
                        supported = false;
                    }
                    if (args.size() > 1 && !freshBuilder(method.getSelect(), true)) {
                        supported = false;
                    }
                    if (args.subList(1, args.size()).stream().anyMatch(Slf4jFluentToSlf4j::mayHoldSupplier)) {
                        supported = false;
                    }
                    for (int i = 0; i < args.size(); i++) {
                        if (TypeUtils.isAssignableTo("java.util.function.Supplier", args.get(i).getType())) {
                            if (args.subList(i + 1, args.size()).stream().anyMatch(Slf4jFluentToSlf4j::hasEagerEffects)) {
                                supported = false;
                            }
                            break;
                        }
                    }
                }
                if (LAZY.matches(method)) {
                    Object parent = getCursor().getParentTreeCursor().getValue();
                    supported = parent instanceof J.MethodInvocation && LOG.matches((J.MethodInvocation) parent) &&
                            ((J.MethodInvocation) parent).getArguments().indexOf(method) > 0;
                }
                return supported ? m : mark(m);
            }

            private boolean isUnsupportedUtilReference(TypedTree tree) {
                if (!TypeUtils.isOfClassType(tree.getType(), "org.fissore.slf4j.Util") ||
                        getCursor().firstEnclosing(J.Import.class) != null) {
                    return false;
                }
                Object parent = getCursor().getParentTreeCursor().getValue();
                if (parent instanceof J.FieldAccess && tree == ((J.FieldAccess) parent).getName()) {
                    return false;
                }
                return !(parent instanceof J.MethodInvocation && LAZY.matches((J.MethodInvocation) parent) &&
                        tree == ((J.MethodInvocation) parent).getSelect());
            }

            @Override
            public J visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                J.Identifier i = (J.Identifier) super.visitIdentifier(identifier, ctx);
                return isUnsupportedUtilReference(identifier) ? mark(i) : i;
            }

            @Override
            public J visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext ctx) {
                J.FieldAccess f = (J.FieldAccess) super.visitFieldAccess(fieldAccess, ctx);
                return isUnsupportedUtilReference(fieldAccess) ? mark(f) : f;
            }

            @Override
            public J visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass n = (J.NewClass) super.visitNewClass(newClass, ctx);
                JavaType.FullyQualified type = TypeUtils.asFullyQualified(newClass.getClazz() == null ?
                        newClass.getType() : newClass.getClazz().getType());
                if (type != null && (type.getFullyQualifiedName().startsWith("org.fissore.slf4j.") ||
                        TypeUtils.isAssignableTo(LOGGER, type) || TypeUtils.isAssignableTo(BUILDER, type)) &&
                        (!LOGGER.equals(type.getFullyQualifiedName()) || newClass.getBody() != null)) {
                    return mark(n);
                }
                return n;
            }

            @Override
            public J visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration c = (J.ClassDeclaration) super.visitClassDeclaration(classDecl, ctx);
                if (classDecl.getExtends() != null &&
                        (TypeUtils.isAssignableTo(LOGGER, classDecl.getExtends().getType()) ||
                         TypeUtils.isAssignableTo(BUILDER, classDecl.getExtends().getType()))) {
                    return mark(c);
                }
                return c;
            }

            @Override
            public J visitMemberReference(J.MemberReference memberRef, ExecutionContext ctx) {
                J.MemberReference m = (J.MemberReference) super.visitMemberReference(memberRef, ctx);
                JavaType.Method type = memberRef.getMethodType();
                if (type != null && type.getDeclaringType().getFullyQualifiedName().startsWith("org.fissore.slf4j.") &&
                        !(FACTORY.equals(type.getDeclaringType().getFullyQualifiedName()) && "getLogger".equals(type.getName()))) {
                    return mark(m);
                }
                return m;
            }

            @Override
            public NameTree visitTypeName(NameTree name, ExecutionContext ctx) {
                NameTree n = super.visitTypeName(name, ctx);
                JavaType.FullyQualified type = TypeUtils.asFullyQualified(name.getType());
                if (type != null && type.getFullyQualifiedName().startsWith("org.fissore.slf4j.") &&
                        !LOGGER.equals(type.getFullyQualifiedName()) && !FACTORY.equals(type.getFullyQualifiedName()) &&
                        !BUILDER.equals(type.getFullyQualifiedName()) && !"org.fissore.slf4j.Util".equals(type.getFullyQualifiedName())) {
                    return mark(n);
                }
                return n;
            }
        };
    }

    private static boolean mayHoldSupplier(Expression argument) {
        if (argument instanceof J.MethodInvocation && LAZY.matches((J.MethodInvocation) argument)) {
            // Fissore unwraps nested suppliers, while SLF4J evaluates only the outer supplier.
            return mayReturnSupplier(((J.MethodInvocation) argument).getArguments().get(0));
        }
        return mayHoldSupplier(argument.getType());
    }

    private static boolean mayReturnSupplier(Expression supplier) {
        if (supplier instanceof J.MemberReference) {
            J.MemberReference reference = (J.MemberReference) supplier;
            JavaType.Method method = reference.getMethodType();
            return method == null || "new".equals(reference.getReference().getSimpleName()) ||
                    mayHoldSupplier(method.getReturnType());
        }
        if (!(supplier instanceof J.Lambda)) {
            return true;
        }
        J body = ((J.Lambda) supplier).getBody();
        if (body instanceof Expression) {
            return mayHoldSupplier(((Expression) body).getType());
        }
        AtomicBoolean nestedSupplier = new AtomicBoolean();
        new JavaIsoVisitor<AtomicBoolean>() {
            @Override
            public J.Return visitReturn(J.Return statement, AtomicBoolean found) {
                Expression value = statement.getExpression();
                if (value == null || mayHoldSupplier(value.getType())) {
                    found.set(true);
                }
                return statement;
            }

            @Override
            public J.Lambda visitLambda(J.Lambda lambda, AtomicBoolean found) {
                return lambda;
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration declaration, AtomicBoolean found) {
                return declaration;
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, AtomicBoolean found) {
                return newClass;
            }
        }.visit(body, nestedSupplier);
        return nestedSupplier.get();
    }

    private static boolean mayHoldSupplier(@Nullable JavaType type) {
        if (type instanceof JavaType.Primitive || type instanceof JavaType.Array) {
            return false;
        }
        JavaType.FullyQualified declared = TypeUtils.asFullyQualified(type);
        return declared == null || declared instanceof JavaType.Unknown ||
                TypeUtils.isAssignableTo("java.util.function.Supplier", declared) ||
                !declared.hasFlags(Flag.Final) && declared.getKind() != JavaType.FullyQualified.Kind.Enum;
    }

    private static boolean hasEagerEffects(Expression expression) {
        AtomicBoolean effects = new AtomicBoolean(LogArguments.hasSideEffects(expression));
        new JavaIsoVisitor<AtomicBoolean>() {
            @Override
            public J.Lambda visitLambda(J.Lambda lambda, AtomicBoolean found) {
                return lambda;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, AtomicBoolean found) {
                if (!LAZY.matches(method)) {
                    found.set(true);
                }
                return super.visitMethodInvocation(method, found);
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, AtomicBoolean found) {
                found.set(true);
                return newClass;
            }
        }.visit(expression, effects);
        return effects.get();
    }

    private static boolean isLevel(String name) {
        return "trace".equals(name) || "debug".equals(name) || "info".equals(name) || "warn".equals(name) || "error".equals(name);
    }

    private static boolean isNull(Expression expression) {
        return expression.getType() == JavaType.Primitive.Null;
    }

    private static boolean usesFluent(JavaSourceFile sourceFile) {
        for (JavaType type : sourceFile.getTypesInUse().getTypesInUse()) {
            JavaType.FullyQualified fq = TypeUtils.asFullyQualified(type);
            if (fq != null && fq.getFullyQualifiedName().startsWith("org.fissore.slf4j.")) {
                return true;
            }
        }
        for (J.Import anImport : sourceFile.getImports()) {
            if (anImport.getTypeName().startsWith("org.fissore.slf4j.")) {
                return true;
            }
        }
        return false;
    }

    private static boolean freshBuilder(@Nullable Expression expression, boolean markers) {
        if (!(expression instanceof J.MethodInvocation)) {
            return false;
        }
        J.MethodInvocation m = (J.MethodInvocation) expression;
        return LEVEL.matches(m) && isLevel(m.getSimpleName()) ||
                (WITH_CAUSE.matches(m) || markers && WITH_MARKER.matches(m)) && freshBuilder(m.getSelect(), markers);
    }

    private static JavaVisitor<ExecutionContext> migrate() {
        return new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                maybeRemoveImport("org.fissore.slf4j.Util");
                maybeRemoveImport("org.fissore.slf4j.Util.lazy");
                doAfterVisit(new ChangeType(LOGGER, "org.slf4j.Logger", false).getVisitor());
                doAfterVisit(new ChangeType(FACTORY, "org.slf4j.LoggerFactory", false).getVisitor());
                doAfterVisit(new ChangeType(BUILDER, "org.slf4j.spi.LoggingEventBuilder", false).getVisitor());
                return super.visitCompilationUnit(cu, ctx);
            }

            @Override
            public J visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass n = (J.NewClass) super.visitNewClass(newClass, ctx);
                if (TypeUtils.isOfClassType(newClass.getType(), LOGGER)) {
                    return ParenthesizeVisitor.maybeParenthesize(n.getArguments().get(0).withPrefix(n.getPrefix()), getCursor());
                }
                return n;
            }

            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
                if (method.getSelect() == null || method.getMethodType() == null) {
                    return m;
                }
                String owner = method.getMethodType().getDeclaringType().getFullyQualifiedName();
                if (LOGGER.equals(owner) && isLevel(method.getSimpleName())) {
                    String name = method.getSimpleName();
                    return template("#{any(org.slf4j.Logger)}.at" + Character.toUpperCase(name.charAt(0)) + name.substring(1) + "()", ctx)
                            .apply(updateCursor(m), m.getCoordinates().replace(), m.getSelect());
                }
                if (BUILDER.equals(owner) && ("withCause".equals(method.getSimpleName()) || "withMarker".equals(method.getSimpleName()))) {
                    String name = "withCause".equals(method.getSimpleName()) ? "setCause" : "addMarker";
                    return template("#{any(org.slf4j.spi.LoggingEventBuilder)}." + name + "(#{any()})", ctx)
                            .apply(updateCursor(m), m.getCoordinates().replace(), m.getSelect(), m.getArguments().get(0));
                }
                if (!LOG.matches(method)) {
                    return m;
                }
                List<Object> parameters = new ArrayList<>();
                parameters.add(m.getSelect());
                parameters.add(m.getArguments().get(0));
                StringBuilder code = new StringBuilder("#{any(org.slf4j.spi.LoggingEventBuilder)}");
                if (m.getArguments().size() == 1) {
                    code.append(".log(#{any()})");
                } else {
                    code.append(".setMessage(#{any()})");
                    for (Expression argument : m.getArguments().subList(1, m.getArguments().size())) {
                        Expression arg = argument;
                        if (arg instanceof J.MethodInvocation && LAZY.matches((J.MethodInvocation) arg)) {
                            arg = ((J.MethodInvocation) arg).getArguments().get(0);
                            code.append(".addArgument(#{any(java.util.function.Supplier)})");
                        } else if (isNull(arg)) {
                            code.append(".addArgument((Object) #{any()})");
                        } else {
                            code.append(".addArgument(#{any()})");
                        }
                        parameters.add(arg);
                    }
                    code.append(".log()");
                }
                return template(code.toString(), ctx).apply(updateCursor(m), m.getCoordinates().replace(), parameters.toArray());
            }

            private JavaTemplate template(String code, ExecutionContext ctx) {
                return JavaTemplate.builder(code)
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "slf4j-api-2"))
                        .build();
            }
        };
    }

    static class Accumulator {
        final ChangeDependency dependency = new ChangeDependency("org.fissore", "slf4j-fluent", "org.slf4j", "slf4j-api", "2.x", null, true, true);
        final ChangeDependency.Accumulator dependencies;
        final AddDependency addition = new AddDependency("org.slf4j", "slf4j-api", "2.x", null,
                "org.fissore.slf4j.*", null, null, null, null, null, null, null, null, false);
        final AddDependency.Accumulator additions;
        final Slf4jFluentProviders providers;
        final Set<JavaProject> projects = new HashSet<>();
        boolean unsupported;

        Accumulator(ExecutionContext ctx) {
            dependencies = dependency.getInitialValue(ctx);
            additions = addition.getInitialValue(ctx);
            providers = new Slf4jFluentProviders(ctx);
        }
    }
}
