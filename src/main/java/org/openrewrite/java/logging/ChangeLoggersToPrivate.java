/*
 * Copyright 2025 the original author or authors.
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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.Markers;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toSet;
import static org.openrewrite.java.tree.J.Modifier.Type.Abstract;

@EqualsAndHashCode(callSuper = false)
@Value
public class ChangeLoggersToPrivate extends Recipe {

    private static final Set<String> LOGGER_TYPES = Arrays.stream(LoggingFramework.values())
            .map(LoggingFramework::getLoggerType)
            .collect(toSet());

    String displayName = "Change logger fields to `private`";

    String description = "Ensures that logger fields are declared as `private` to encapsulate logging mechanics within the class.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(usesAnyLogger(), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                J.VariableDeclarations mv = super.visitVariableDeclarations(multiVariable, ctx);
                if (mv.getTypeExpression() == null ||
                        !isLoggerType(mv.getTypeExpression().getType()) ||
                        mv.hasModifier(J.Modifier.Type.Private)) {
                    return mv;
                }

                Cursor parent = getCursor().getParentTreeCursor();
                if (!(parent.getValue() instanceof J.Block)) {
                    return mv;
                }

                parent = parent.getParentTreeCursor();
                if (!(parent.getValue() instanceof J.ClassDeclaration)) {
                    return mv;
                }

                J.ClassDeclaration classDeclaration = parent.getValue();
                if (classDeclaration.getKind() == J.ClassDeclaration.Kind.Type.Interface ||
                        classDeclaration.hasModifier(Abstract)) {
                    return mv;
                }

                List<J.Modifier> mapped = ListUtils.map(mv.getModifiers(), mod -> {
                    if (mod.getType() == J.Modifier.Type.Public ||
                            mod.getType() == J.Modifier.Type.Protected ||
                            mod.getType() == J.Modifier.Type.Private) {
                        return mod.withType(J.Modifier.Type.Private);
                    }
                    return mod;
                });
                if (mapped == mv.getModifiers()) {
                    mapped = ListUtils.insert(mapped, new J.Modifier(
                            Tree.randomId(),
                            Space.EMPTY,
                            Markers.EMPTY,
                            null,
                            J.Modifier.Type.Private,
                            emptyList()
                    ), 0);
                }
                return autoFormat(mv.withModifiers(mapped), mv.getTypeExpression(), ctx, getCursor().getParentTreeCursor());
            }

            private boolean isLoggerType(@Nullable JavaType type) {
                JavaType.FullyQualified fqnType = TypeUtils.asFullyQualified(type);
                if (fqnType != null) {
                    return LOGGER_TYPES.contains(fqnType.getFullyQualifiedName());
                }
                return false;
            }
        });
    }

    private static TreeVisitor<?, ExecutionContext> usesAnyLogger() {
        UsesType[] usesTypes = new UsesType[LOGGER_TYPES.size()];
        int i = 0;
        for (String fqn : LOGGER_TYPES) {
            usesTypes[i++] = new UsesType<>(fqn, true);
        }
        return Preconditions.or(usesTypes);
    }
}
