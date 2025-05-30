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
import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.Markers;

import java.util.*;

import static java.util.stream.Collectors.toSet;

@Value
@EqualsAndHashCode(callSuper = false)
public class ChangeLoggersToPrivate extends Recipe {

    private static final Set<String> LOGGER_TYPES = Arrays.stream(LoggingFramework.values())
            .map(LoggingFramework::getLoggerType)
            .collect(toSet());

    @Override
    public String getDisplayName() {
        return "Change logger fields to private";
    }

    @Override
    public String getDescription() {
        return "Ensures that logger fields are declared as 'private' to encapsulate logging mechanics within the class.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(usesAnyLogger(), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                Cursor parent = getCursor().getParentTreeCursor();
                if (!(parent.getValue() instanceof J.Block)) {
                    return multiVariable;
                }

                parent = parent.getParentTreeCursor();
                if (!(parent.getValue() instanceof J.ClassDeclaration)) {
                    return multiVariable;
                }

                J.ClassDeclaration classDeclaration = parent.getValue();
                if (classDeclaration.getKind() == J.ClassDeclaration.Kind.Type.Interface) {
                    return multiVariable;
                }

                if (multiVariable.getTypeExpression() == null || !isLoggerType(multiVariable.getTypeExpression().getType())) {
                    return multiVariable;
                }

                boolean isPrivate = multiVariable.getModifiers().stream()
                        .anyMatch(mod -> mod.getType() == J.Modifier.Type.Private);
                if (isPrivate) {
                    return multiVariable;
                }

                List<J.Modifier> newModifiers = new ArrayList<>();
                newModifiers.add(
                        new J.Modifier(
                                Tree.randomId(),
                                Space.EMPTY,
                                Markers.EMPTY,
                                null,
                                J.Modifier.Type.Private,
                                Collections.emptyList()
                        )
                );

                for (J.Modifier existingModifier : multiVariable.getModifiers()) {
                    if (existingModifier.getType() != J.Modifier.Type.Public &&
                            existingModifier.getType() != J.Modifier.Type.Protected &&
                            existingModifier.getType() != J.Modifier.Type.Private) {
                        newModifiers.add(existingModifier.withPrefix(Space.SINGLE_SPACE));
                    }
                }

                multiVariable = multiVariable.withModifiers(newModifiers)
                        .withTypeExpression(multiVariable.getTypeExpression().withPrefix(Space.SINGLE_SPACE));
                return autoFormat(multiVariable, ctx, getCursor().getParent());
            }

            private boolean isLoggerType(JavaType type) {
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
