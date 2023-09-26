/*
 * Copyright 2021 the original author or authors.
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

import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;

public enum LoggingFramework {
    SLF4J("org.slf4j.Logger", "org.slf4j.LoggerFactory"),
    Log4J1("org.apache.log4j.Logger", "org.apache.log4j.LogManager"),
    Log4J2("org.apache.logging.log4j.Logger", "org.apache.logging.log4j.LogManager"),
    JUL("java.util.logging.Logger", "java.util.logging.LogManager");

    private final String loggerType;
    private final String factoryType;

    LoggingFramework(String loggerType, String factoryType) {
        this.loggerType = loggerType;
        this.factoryType = factoryType;
    }

    public String getLoggerType() {
        return loggerType;
    }

    public String getFactoryType() {
        return factoryType;
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

    public <P> JavaTemplate getErrorTemplate(JavaVisitor<P> visitor, String message) {
        switch (this) {
            case SLF4J:
                return JavaTemplate
                        .builder("#{any(org.slf4j.Logger)}.error(" + message + ", #{any(java.lang.Throwable)})")
                        .contextSensitive()
                        .javaParser(JavaParser.fromJavaVersion().classpath("slf4j-api"))
                        .build();
            case Log4J1:
                return JavaTemplate
                        .builder("#{any(org.apache.log4j.Category)}.error(" + message + ", #{any(java.lang.Throwable)})")
                        .contextSensitive()
                        .javaParser(JavaParser.fromJavaVersion().classpath("log4j"))
                        .build();

            case Log4J2:
                return JavaTemplate
                        .builder("#{any(org.apache.logging.log4j.Logger)}.error(" + message + ", #{any(java.lang.Throwable)})")
                        .contextSensitive()
                        .javaParser(JavaParser.fromJavaVersion().classpath("log4j-api"))
                        .build();
            case JUL:
            default:
                return JavaTemplate
                        .builder("#{any(java.util.logging.Logger)}.log(Level.SEVERE, " + message + ", #{any(java.lang.Throwable)})")
                        .contextSensitive()
                        .imports("java.util.logging.Level")
                        .build();

        }
    }
}
