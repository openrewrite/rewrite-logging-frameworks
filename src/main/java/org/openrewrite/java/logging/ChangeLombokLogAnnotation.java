/*
 * Copyright 2023 the original author or authors.
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

import lombok.AllArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.java.ChangeType;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@AllArgsConstructor
public class ChangeLombokLogAnnotation extends Recipe {

    @Override
    public String getDisplayName() {
        return "Replace any Lombok log annotations with target logging framework annotation";
    }

    @Override
    public String getDescription() {
        return "Replace Lombok annotations such as `@CommonsLog` and `@Log4j` with the target logging framework annotation, or `@Sl4fj` if not provided.";
    }

    @Option(displayName = "Logging framework",
            description = "The logging framework to use.",
            valid = {"SLF4J", "Log4J1", "Log4J2", "JUL", "COMMONS"},
            required = false)
    @Nullable
    private String loggingFramework;

    @Override
    public List<Recipe> getRecipeList() {
        String targetLogAnnotationType = getTargetAnnotationType(loggingFramework);
        return Stream.of(
                        "lombok.extern.java.Log",
                        "lombok.extern.apachecommons.CommonsLog",
                        "lombok.extern.flogger.Flogger",
                        "lombok.extern.jbosslog.JBossLog",
                        "lombok.extern.log4j.Log4j",
                        "lombok.extern.log4j.Log4j2",
                        "lombok.extern.slf4j.Slf4j",
                        "lombok.extern.slf4j.XSlf4j",
                        "lombok.CustomLog")
                .filter(annotationType -> !annotationType.equals(targetLogAnnotationType))
                .map(annotationType -> new ChangeType(annotationType, targetLogAnnotationType, true))
                .collect(Collectors.toList());
    }

    private static String getTargetAnnotationType(@Nullable String loggingFramework) {
        if (loggingFramework != null) {
            switch (LoggingFramework.fromOption(loggingFramework)) {
                case Log4J1:
                    return "lombok.extern.log4j.Log4j";
                case Log4J2:
                    return "lombok.extern.log4j.Log4j2";
                case JUL:
                    return "lombok.extern.java.Log";
                case COMMONS:
                    return "lombok.extern.apachecommons.CommonsLog";
            }
        }
        return "lombok.extern.slf4j.Slf4j";
    }
}
