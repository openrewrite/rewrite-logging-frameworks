package org.openrewrite.java.logging.slf4j;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;

public class CompleteExceptionLogging extends Recipe {
    @Override
    public String getDisplayName() {
        return "Enhances logging of exceptions by including the full stack trace in addition to the exception message.";
    }

    @Override
    public String getDescription() {
        return "It is a common mistake to call Exception.getMessage() when passing an exception into a log method. " +
               "Not all exception types have useful messages, and even if the message is useful this omits the stack " +
               "trace. Including a complete stack trace of the error along with the exception message in the log " +
               "allows developers to better understand the context of the exception and identify the source of the " +
               "error more quickly and accurately.";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {


        };
    }
}
