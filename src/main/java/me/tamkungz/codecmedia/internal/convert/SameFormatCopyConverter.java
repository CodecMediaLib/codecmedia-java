package me.tamkungz.codecmedia.internal.convert;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import me.tamkungz.codecmedia.CodecMediaException;
import me.tamkungz.codecmedia.model.ConversionResult;

public final class SameFormatCopyConverter implements MediaConverter {

    @Override
    public ConversionResult convert(ConversionRequest request) throws CodecMediaException {
        Path output = request.output();
        try {
            Path parent = output.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            if (Files.exists(output) && !request.options().overwrite()) {
                throw new CodecMediaException("Output already exists and overwrite is disabled: " + output);
            }
            Files.copy(request.input(), output, StandardCopyOption.REPLACE_EXISTING);
            return new ConversionResult(output, request.targetExtension(), false);
        } catch (IOException e) {
            throw new CodecMediaException("Failed to convert file: " + request.input(), e);
        }
    }
}

