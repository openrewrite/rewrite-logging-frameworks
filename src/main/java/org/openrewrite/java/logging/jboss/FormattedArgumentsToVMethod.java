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
package org.openrewrite.java.logging.jboss;

import com.google.errorprone.refaster.annotation.AfterTemplate;
import com.google.errorprone.refaster.annotation.BeforeTemplate;
import org.jboss.logging.Logger;
import org.openrewrite.java.template.RecipeDescriptor;

@RecipeDescriptor(
        name = "Replace deprecated JBoss Logging Logger formatted message invocations with the v-version of methods",
        description = "Replace `logger.level(\"hello {0}\", arg)` with `logger.levelv(\"hello {0}\", arg)`."
)
@SuppressWarnings({"deprecation", "unused"})
public class FormattedArgumentsToVMethod {
    public static class TraceToVTrace {
        @BeforeTemplate
        void before(Logger logger, String message, Object[] args) {
            logger.trace(message, args);
        }

        @AfterTemplate
        void after(Logger logger, String message, Object[] args) {
            logger.tracev(message, args);
        }
    }

    public static class TraceToVTraceWithThrowable {
        @BeforeTemplate
        void before(Logger logger, String message, Object[] args, Throwable t) {
            logger.trace((Object) message, args, t);
        }

        @AfterTemplate
        void after(Logger logger, String message, Object[] args, Throwable t) {
            logger.tracev(message, args, t);
        }
    }

    public static class DebugToVDebug {
        @BeforeTemplate
        void before(Logger logger, String message, Object[] args) {
            logger.debug(message, args);
        }

        @AfterTemplate
        void after(Logger logger, String message, Object[] args) {
            logger.debugv(message, args);
        }
    }

    public static class DebugToVDebugWithThrowable {
        @BeforeTemplate
        void before(Logger logger, String message, Object[] args, Throwable t) {
            logger.debug((Object) message, args, t);
        }

        @AfterTemplate
        void after(Logger logger, String message, Object[] args, Throwable t) {
            logger.debugv(message, args, t);
        }
    }

    public static class InfoToVInfo {
        @BeforeTemplate
        void before(Logger logger, String message, Object[] args) {
            logger.info(message, args);
        }

        @AfterTemplate
        void after(Logger logger, String message, Object[] args) {
            logger.infov(message, args);
        }
    }

    public static class InfoToVInfoWithThrowable {
        @BeforeTemplate
        void before(Logger logger, String message, Object[] args, Throwable t) {
            logger.info((Object) message, args, t);
        }

        @AfterTemplate
        void after(Logger logger, String message, Object[] args, Throwable t) {
            logger.infov(message, args, t);
        }
    }

    public static class WarnToVWarn {
        @BeforeTemplate
        void before(Logger logger, String message, Object[] args) {
            logger.warn(message, args);
        }

        @AfterTemplate
        void after(Logger logger, String message, Object[] args) {
            logger.warnv(message, args);
        }
    }

    public static class WarnToVWarnWithThrowable {
        @BeforeTemplate
        void before(Logger logger, String message, Object[] args, Throwable t) {
            logger.warn((Object) message, args, t);
        }

        @AfterTemplate
        void after(Logger logger, String message, Object[] args, Throwable t) {
            logger.warnv(message, args, t);
        }
    }

    public static class ErrorToVError {
        @BeforeTemplate
        void before(Logger logger, String message, Object[] args) {
            logger.error(message, args);
        }

        @AfterTemplate
        void after(Logger logger, String message, Object[] args) {
            logger.errorv(message, args);
        }
    }

    public static class ErrorToVErrorWithThrowable {
        @BeforeTemplate
        void before(Logger logger, String message, Object[] args, Throwable t) {
            logger.error((Object) message, args, t);
        }

        @AfterTemplate
        void after(Logger logger, String message, Object[] args, Throwable t) {
            logger.errorv(message, args, t);
        }
    }

    public static class FatalToVFatal {
        @BeforeTemplate
        void before(Logger logger, String message, Object[] args) {
            logger.fatal(message, args);
        }

        @AfterTemplate
        void after(Logger logger, String message, Object[] args) {
            logger.fatalv(message, args);
        }
    }

    public static class FatalToVFatalWithThrowable {
        @BeforeTemplate
        void before(Logger logger, String message, Object[] args, Throwable t) {
            logger.fatal((Object) message, args, t);
        }

        @AfterTemplate
        void after(Logger logger, String message, Object[] args, Throwable t) {
            logger.fatalv(message, args, t);
        }
    }
}
