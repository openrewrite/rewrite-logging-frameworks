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

import lombok.AllArgsConstructor;
import org.apache.logging.converter.config.ConfigurationConverter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.text.PlainText;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Converts a logging configuration file from one format to another.
 */
@AllArgsConstructor
public class ConvertConfiguration extends Recipe {

    @Option(displayName = "Pattern for the files to convert",
            description = "If set, only the files that match this pattern will be converted.",
            example = "**/log4j.properties",
            required = false)
    @Nullable
    String filePattern;

    @Option(displayName = "Input format",
            description = "The id of the input logging configuration format. See [Log4j documentation](https://logging.staged.apache.org/log4j/transform/log4j-converter-config.html#formats) for a list of supported formats.",
            example = "v1:properties")
    String inputFormat;

    @Option(displayName = "Output format",
            description = "The id of the output logging configuration format. See [Log4j documentation](https://logging.staged.apache.org/log4j/transform/log4j-converter-config.html#formats) for a list of supported formats.",
            example = "v2:xml")
    String outputFormat;

    private static final ConfigurationConverter converter = ConfigurationConverter.getInstance();

    @Override
    public String getDisplayName() {
        return "Convert logging configuration";
    }

    @Override
    public String getDescription() {
        return "Converts the configuration of a logging backend from one format to another. For example it can convert a Log4j 1 properties configuration file into a Log4j Core 2 XML configuration file.";
    }

    @Override
    public int maxCycles() {
        return 1;
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return
                Preconditions.check(new FindSourceFiles(filePattern),
                        new TreeVisitor<Tree, ExecutionContext>() {

                            @Override
                            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                                if (tree instanceof SourceFile) {
                                    SourceFile sourceFile = (SourceFile) tree;
                                    ByteArrayInputStream inputStream = new ByteArrayInputStream(sourceFile.printAllAsBytes());
                                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

                                    converter.convert(inputStream, inputFormat, outputStream, outputFormat);

                                    String utf8 = new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
                                    if (tree instanceof PlainText) {
                                        return ((PlainText) tree).withText(utf8).withCharset(StandardCharsets.UTF_8);
                                    }
                                    return PlainText.builder()
                                            .id(sourceFile.getId())
                                            .charsetBomMarked(sourceFile.isCharsetBomMarked())
                                            .charsetName(StandardCharsets.UTF_8.name())
                                            .checksum(sourceFile.getChecksum())
                                            .fileAttributes(sourceFile.getFileAttributes())
                                            .markers(sourceFile.getMarkers())
                                            .sourcePath(sourceFile.getSourcePath())
                                            .text(utf8)
                                            .build();
                                }
                                return super.visit(tree, ctx);
                            }
                        });
    }
}
