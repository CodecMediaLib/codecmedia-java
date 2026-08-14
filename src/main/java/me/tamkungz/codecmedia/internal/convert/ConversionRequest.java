package me.tamkungz.codecmedia.internal.convert;

import java.nio.file.Path;

import me.tamkungz.codecmedia.model.MediaType;
import me.tamkungz.codecmedia.options.ConversionOptions;

public record ConversionRequest(
        Path input,
        Path output,
        String sourceExtension,
        String targetExtension,
        MediaType sourceMediaType,
        MediaType targetMediaType,
        ConversionOptions options
) {
}

