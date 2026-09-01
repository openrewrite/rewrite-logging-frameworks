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
package org.openrewrite.java.logging.log4j;

import org.jspecify.annotations.NullMarked;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.template.internal.AbstractRefasterJavaVisitor;
import org.openrewrite.java.tree.J;


import static org.openrewrite.java.template.internal.AbstractRefasterJavaVisitor.EmbeddingOption.*;

@NullMarked
public class LoggerSetLevelToConfiguratorRecipe extends Recipe {
    @Override
    public String getDisplayName() {
        //language=markdown
        return "Convert Log4j `Logger.setLevel` to Log4j2 `Configurator.setLevel`";
    }

    @Override
    public String getDescription() {
        //language=markdown
        return "Converts `org.apache.log4j.Logger.setLevel` to `org.apache.logging.log4j.core.config.Configurator.setLevel`.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        JavaVisitor<ExecutionContext> javaVisitor = new AbstractRefasterJavaVisitor() {
            JavaTemplate before;
            JavaTemplate after;

            @Override
            public J visitMethodInvocation(J.MethodInvocation elem, ExecutionContext ctx) {
                JavaTemplate.Matcher matcher;
                if (before == null) {
                    before = JavaTemplate.builder("#{logger:any(org.apache.log4j.Logger)}.setLevel(#{level:any(org.apache.log4j.Level)});")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "log4j-1"))
                            .build();
                }
                if ((matcher = before.matcher(getCursor())).find()) {
                    maybeRemoveImport("org.apache.log4j.Logger");
                    maybeRemoveImport("org.apache.log4j.Level");
                    if (after == null) {
                        after = JavaTemplate.builder("org.apache.logging.log4j.core.config.Configurator.setLevel(#{logger:any(org.apache.logging.log4j.Logger)}, #{level:any(org.apache.logging.log4j.Level)});")
                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "log4j-api-2", "log4j-core-2"))
                                .build();
                    }
                    return embed(
                            after.apply(getCursor(), elem.getCoordinates().replace(), matcher.parameter(0), matcher.parameter(1)),
                            getCursor(),
                            ctx,
                            SHORTEN_NAMES
                    );
                }
                return super.visitMethodInvocation(elem, ctx);
            }

        };
        return Preconditions.check(
                Preconditions.and(
                        new UsesType<>("org.apache.log4j.Level", true),
                        new UsesType<>("org.apache.log4j.Logger", true),
                        new UsesMethod<>("org.apache.log4j.Category setLevel(..)", true)
                ),
                javaVisitor
        );
    }
}
