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
import org.openrewrite.ExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;

public enum LoggingFramework {
    SLF4J("org.slf4j.Logger"),
    Log4J1("org.apache.log4j.Logger"),
    Log4J2("org.apache.logging.log4j.Logger"),
    JUL("java.util.logging.Logger"),
    COMMONS("org.apache.commons.logging.Log");

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

    public JavaTemplate getErrorTemplate(String message, ExecutionContext ctx) {
        switch (this) {
            case SLF4J:
                return JavaTemplate
                        .builder("#{any(org.slf4j.Logger)}.error(" + message + ", #{any(java.lang.Throwable)})")
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "slf4j-api-2.1.+"))
                        .build();
            case Log4J1:
                return JavaTemplate
                        .builder("#{any(org.apache.log4j.Category)}.error(" + message + ", #{any(java.lang.Throwable)})")
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "log4j-1.2.+"))
                        .build();

            case Log4J2:
                return JavaTemplate
                        .builder("#{any(org.apache.logging.log4j.Logger)}.error(" + message + ", #{any(java.lang.Throwable)})")
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "log4j-api-2.23.+"))
                        .build();
            case COMMONS:
                return JavaTemplate
                        .builder("#{any(org.apache.commons.logging.Log)}.error(" + message + ", #{any(java.lang.Throwable)})")
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "commons-logging-1.3.+"))
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
