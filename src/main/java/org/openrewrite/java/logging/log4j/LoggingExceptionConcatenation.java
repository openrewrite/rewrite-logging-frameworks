package org.openrewrite.java.logging.log4j;

import com.google.errorprone.refaster.annotation.AfterTemplate;
import com.google.errorprone.refaster.annotation.BeforeTemplate;
import org.apache.logging.log4j.Logger;
import org.openrewrite.java.template.RecipeDescriptor;

@RecipeDescriptor(
        name = "Log exceptions as parameters rather than as string concatenations",
        description = "By using the exception as another parameter you get the whole stack trace."
)
public class LoggingExceptionConcatenation {

    @BeforeTemplate
    void before(Logger logger, String s, Exception e) {
        logger.error(s + e);
    }

    @AfterTemplate
    void after(Logger logger, String s, Exception e) {
        logger.error(s, e);
    }
}
