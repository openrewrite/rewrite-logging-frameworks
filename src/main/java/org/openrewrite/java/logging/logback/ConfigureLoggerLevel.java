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
package org.openrewrite.java.logging.logback;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.xml.XPathMatcher;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Content;
import org.openrewrite.xml.tree.Xml;

import java.util.List;

@Value
@EqualsAndHashCode(callSuper = false)
public class ConfigureLoggerLevel extends Recipe {

    @Override
    public @NlsRewrite.DisplayName String getDisplayName() {
        return "Configure logback logger level";
    }

    @Override
    public @NlsRewrite.Description String getDescription() {
        return "Within logback.xml configuration files sets the specified log level for a particular class. " +
               "Will not create a logback.xml if one does not already exist.";
    }

    @Option(displayName = "Class name",
            description = "The fully qualified class name to configure the log level for",
            example = "com.example.MyClass")
    String className;

    @Option(displayName = "Log level",
            description = "The log level to set for the class",
            valid = {"trace", "debug", "info", "warn", "error", "off"},
            example = "off")
    LogLevel logLevel;

    public enum LogLevel {
        trace,
        debug,
        info,
        warn,
        error,
        off
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new FindSourceFiles("**/logback.xml"), new XmlIsoVisitor<ExecutionContext>() {

            @Override
            public Xml.Document visitDocument(Xml.Document document, ExecutionContext ctx) {
                Xml.Document x = super.visitDocument(document, ctx);
                if(x == document && !getCursor().getMessage("found", false)) {
                    // No tag already exists for the specified logger, we need to create a new one
                    Xml.Tag l = Xml.Tag.build("\n<logger name=\"" + className + "\" level=\"" + logLevel.name() + "\"/>");
                    l = autoFormat(l, ctx, new Cursor(getCursor(), x.getRoot()));
                    //noinspection unchecked
                    x = x.withRoot(x.getRoot().withContent(ListUtils.concat((List<Content>)x.getRoot().getContent(), l)));
                }
                return x;
            }

            final XPathMatcher loggerMatcher = new XPathMatcher("/configuration/logger[@name='" + className + "']");

            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ctx) {
                Xml.Tag t = super.visitTag(tag, ctx);
                if (loggerMatcher.matches(getCursor())) {
                    getCursor().putMessageOnFirstEnclosing(Xml.Document.class, "found", true);
                    t = t.withAttributes(ListUtils.map(t.getAttributes(), a -> {
                        if(a != null && "level".equals(a.getKeyAsString()) && !logLevel.name().equals(a.getValueAsString())) {
                            return a.withValue(a.getValue().withValue(logLevel.name()));
                        }
                        return a;
                    }));
                }
                return t;
            }
        });
    }
}
