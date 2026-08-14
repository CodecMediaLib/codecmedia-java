package me.tamkungz.codecmedia.internal.audio.flac;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import me.tamkungz.codecmedia.CodecMediaException;

public final class FlacCodec {

    private FlacCodec() {
    }

    public static FlacProbeInfo decode(Path input) throws CodecMediaException {
        try {
            byte[] bytes = Files.readAllBytes(input);
            return decode(bytes, input);
        } catch (IOException e) {
            throw new CodecMediaException("Failed to decode FLAC: " + input, e);
        }
    }

    public static FlacProbeInfo decode(byte[] bytes, Path sourceRef) throws CodecMediaException {
        FlacProbeInfo info = FlacParser.parse(bytes);
        validateDecodedProbe(info, sourceRef);
        return info;
    }

    // Intentional: FLAC codec is currently decode/probe only in this library.
    // No encode API is exposed until a deterministic FLAC encoder path is introduced.

    private static void validateDecodedProbe(FlacProbeInfo info, Path input) throws CodecMediaException {
        if (info.sampleRate() <= 0 || info.channels() <= 0 || info.bitsPerSample() <= 0) {
            throw new CodecMediaException("Decoded FLAC has invalid stream values: " + input);
        }
    }
}

