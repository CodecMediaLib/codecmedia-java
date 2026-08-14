package me.tamkungz.codecmedia.model;

import java.nio.file.Path;

public record ConversionResult(
        Path outputFile,
        String format,
        boolean reencoded
) {
}
