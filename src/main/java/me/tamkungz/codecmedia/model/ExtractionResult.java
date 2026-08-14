package me.tamkungz.codecmedia.model;

import java.nio.file.Path;

public record ExtractionResult(
        Path outputFile,
        String format
) {
}
