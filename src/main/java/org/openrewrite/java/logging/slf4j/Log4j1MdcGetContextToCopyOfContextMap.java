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

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.ChangeMethodName;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JContainer;
import org.openrewrite.java.tree.JRightPadded;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.Markers;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Collections.emptyList;
import static org.openrewrite.Tree.randomId;

public class Log4j1MdcGetContextToCopyOfContextMap extends Recipe {

    private static final String GET_CONTEXT_PATTERN = "org.apache.log4j.MDC getContext()";
    private static final MethodMatcher GET_CONTEXT = new MethodMatcher(GET_CONTEXT_PATTERN);

    private static final JavaType MAP_TYPE = JavaType.ShallowClass.build("java.util.Map");
    private static final JavaType STRING_TYPE = JavaType.ShallowClass.build("java.lang.String");

    private static final String ASSIGNED_TARGETS_KEY = "log4j1MdcGetContextAssignmentTargets";

    @Getter
    final Set<String> tags = new HashSet<>(Arrays.asList("logging", "slf4j", "log4j"));

    @Getter
    final Duration estimatedEffortPerOccurrence = Duration.ofSeconds(10);

    @Override
    public String getDisplayName() {
        return "Convert Log4j 1.x `MDC.getContext()` to `getCopyOfContextMap()`";
    }

    @Override
    public String getDescription() {
        return "Renames Log4j 1.x `org.apache.log4j.MDC.getContext()` (returns `Hashtable`) to " +
               "`getCopyOfContextMap()` (returns `Map`) at every call site, and retypes any `Hashtable` " +
               "declaration — local variable, field, method parameter, or method return type — that " +
               "receives the result, whether initialized directly from the call, directly assigned it in " +
               "a later statement, or returning it, to `Map<String, String>`, since `Map` is not " +
               "assignable to `Hashtable`. Retyping a parameter or return type changes the method's " +
               "signature; overriding methods are left unchanged to avoid breaking the override, so they " +
               "need a manual fix. Does not change the `org.apache.log4j.MDC` type; compose with a " +
               "`ChangeType` to complete the migration.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(GET_CONTEXT), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                // Delegate the rename to the stock ChangeMethodName, which keeps the method's name and
                // type metadata consistent. It matches while the receiver is still org.apache.log4j.MDC.
                doAfterVisit(new ChangeMethodName(GET_CONTEXT_PATTERN, "getCopyOfContextMap", null, null).getVisitor());
                // A variable declared separately and assigned the result later (e.g. `Hashtable h; h =
                // MDC.getContext();`) is not caught by an initializer check, but its declaration still has
                // to be retyped or the renamed call won't compile. Pre-scan the file for those targets so
                // visitVariableDeclarations can retype them, whether they are locals, fields, or parameters.
                Set<JavaType.Variable> assignedFromGetContext = new HashSet<>();
                new JavaIsoVisitor<Set<JavaType.Variable>>() {
                    @Override
                    public J.Assignment visitAssignment(J.Assignment assignment, Set<JavaType.Variable> targets) {
                        if (assignment.getAssignment() instanceof J.MethodInvocation &&
                            GET_CONTEXT.matches((J.MethodInvocation) assignment.getAssignment()) &&
                            assignment.getVariable() instanceof J.Identifier) {
                            JavaType.Variable fieldType = ((J.Identifier) assignment.getVariable()).getFieldType();
                            if (fieldType != null) {
                                targets.add(fieldType);
                            }
                        }
                        return super.visitAssignment(assignment, targets);
                    }
                }.visit(cu, assignedFromGetContext);
                getCursor().putMessage(ASSIGNED_TARGETS_KEY, assignedFromGetContext);
                return super.visitCompilationUnit(cu, ctx);
            }

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                J.VariableDeclarations mv = super.visitVariableDeclarations(multiVariable, ctx);
                // getContext() returns Hashtable but getCopyOfContextMap() returns Map; a Hashtable-typed
                // declaration that receives the result would no longer compile, so retype the declaration.
                // The rename applies to every getContext() call, so any declaration with even one variable
                // initialized or assigned from it must be retyped, including multi-variable declarations.
                if (TypeUtils.isOfClassType(mv.getType(), "java.util.Hashtable") &&
                    !isOverriddenMethodParameter() && retypeFromGetContext(mv)) {
                    maybeAddImport("java.util.Map");
                    maybeRemoveImport("java.util.Hashtable");
                    // Replace only the type expression so modifiers, annotations, variable names,
                    // initializers, and surrounding formatting are preserved. Each variable's own type
                    // attribution is retyped to Map too, so the Hashtable import is seen as unused.
                    mv = mv.withTypeExpression(mapStringString(mv.getTypeExpression().getPrefix()))
                            .withVariables(ListUtils.map(mv.getVariables(), nv -> {
                                JavaType.Variable variableType = nv.getVariableType() == null ? null :
                                        nv.getVariableType().withType(MAP_TYPE);
                                return nv.withVariableType(variableType)
                                        .withName(nv.getName().withType(MAP_TYPE).withFieldType(variableType));
                            }));
                }
                return mv;
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
                // visitVariableDeclarations may have retyped a parameter from Hashtable to Map; sync the
                // method type's parameter list so no stale Hashtable reference is left behind (which would
                // otherwise keep the Hashtable import alive and leave the signature type inconsistent).
                JavaType.Method methodType = m.getMethodType();
                if (methodType != null && !methodType.getParameterTypes().isEmpty()) {
                    List<Statement> parameters = m.getParameters();
                    m = m.withMethodType(methodType.withParameterTypes(
                            ListUtils.map(methodType.getParameterTypes(), (i, parameterType) -> {
                                Statement parameter = parameters.get(i);
                                return parameter instanceof J.VariableDeclarations &&
                                       TypeUtils.isOfClassType(((J.VariableDeclarations) parameter).getType(), "java.util.Map") ?
                                        MAP_TYPE : parameterType;
                            })));
                }
                // A Hashtable return type whose method returns getContext() would no longer compile after
                // the rename, so retype it to Map<String, String> too. Overriding methods are excluded:
                // widening their return type would violate covariant-return rules against the supertype.
                methodType = m.getMethodType();
                if (m.getReturnTypeExpression() != null &&
                    TypeUtils.isOfClassType(m.getReturnTypeExpression().getType(), "java.util.Hashtable") &&
                    methodType != null && !TypeUtils.isOverride(methodType) &&
                    returnsGetContext(m)) {
                    maybeAddImport("java.util.Map");
                    maybeRemoveImport("java.util.Hashtable");
                    m = m.withReturnTypeExpression(mapStringString(m.getReturnTypeExpression().getPrefix()))
                            .withMethodType(methodType.withReturnType(MAP_TYPE));
                }
                return m;
            }

            private boolean returnsGetContext(J.MethodDeclaration method) {
                AtomicBoolean found = new AtomicBoolean();
                new JavaIsoVisitor<AtomicBoolean>() {
                    @Override
                    public J.Return visitReturn(J.Return r, AtomicBoolean f) {
                        if (r.getExpression() instanceof J.MethodInvocation &&
                            GET_CONTEXT.matches((J.MethodInvocation) r.getExpression())) {
                            f.set(true);
                        }
                        return super.visitReturn(r, f);
                    }

                    // Returns inside nested lambdas or anonymous classes belong to those bodies,
                    // not to this method's return type, so don't descend into them.
                    @Override
                    public J.Lambda visitLambda(J.Lambda lambda, AtomicBoolean f) {
                        return lambda;
                    }

                    @Override
                    public J.NewClass visitNewClass(J.NewClass newClass, AtomicBoolean f) {
                        return newClass;
                    }
                }.visit(method.getBody(), found);
                return found.get();
            }

            private boolean isOverriddenMethodParameter() {
                // Retyping a parameter of an overriding method would break the override against a
                // supertype whose signature is not changed here, so leave those parameters alone.
                Object parent = getCursor().getParentTreeCursor().getValue();
                if (!(parent instanceof J.MethodDeclaration)) {
                    return false;
                }
                JavaType.Method methodType = ((J.MethodDeclaration) parent).getMethodType();
                return methodType != null && TypeUtils.isOverride(methodType);
            }

            private boolean retypeFromGetContext(J.VariableDeclarations mv) {
                Set<JavaType.Variable> assignedTargets = getCursor().getNearestMessage(ASSIGNED_TARGETS_KEY);
                for (J.VariableDeclarations.NamedVariable nv : mv.getVariables()) {
                    boolean initializedFromGetContext = nv.getInitializer() instanceof J.MethodInvocation &&
                            GET_CONTEXT.matches((J.MethodInvocation) nv.getInitializer());
                    if (initializedFromGetContext ||
                        (assignedTargets != null && assignedTargets.contains(nv.getVariableType()))) {
                        return true;
                    }
                }
                return false;
            }
        });
    }

    /**
     * Builds a {@code Map<String, String>} type expression, taking on the leading whitespace of the
     * type it replaces.
     */
    private static J.ParameterizedType mapStringString(Space prefix) {
        J.Identifier map = identifier("Map", Space.EMPTY, MAP_TYPE);
        J.Identifier firstArg = identifier("String", Space.EMPTY, STRING_TYPE);
        J.Identifier secondArg = identifier("String", Space.format(" "), STRING_TYPE);
        JContainer<Expression> typeParameters = JContainer.build(Space.EMPTY,
                Arrays.asList(JRightPadded.build((Expression) firstArg), JRightPadded.build((Expression) secondArg)),
                Markers.EMPTY);
        return new J.ParameterizedType(randomId(), prefix, Markers.EMPTY, map, typeParameters, MAP_TYPE);
    }

    private static J.Identifier identifier(String name, Space prefix, JavaType type) {
        return new J.Identifier(randomId(), prefix, Markers.EMPTY, emptyList(), name, type, null);
    }
}
