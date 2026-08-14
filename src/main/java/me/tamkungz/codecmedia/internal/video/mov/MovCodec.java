package me.tamkungz.codecmedia.internal.video.mov;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import me.tamkungz.codecmedia.CodecMediaException;

public final class MovCodec {

    private MovCodec() {
    }

    public static MovProbeInfo decode(Path input) throws CodecMediaException {
        try {
            byte[] bytes = Files.readAllBytes(input);
            return decode(bytes, input);
        } catch (IOException e) {
            throw new CodecMediaException("Failed to decode MOV: " + input, e);
        }
    }

    public static MovProbeInfo decode(byte[] bytes, Path sourceRef) throws CodecMediaException {
        MovProbeInfo info = MovParser.parse(bytes);
        validateDecodedProbe(info, sourceRef);
        return info;
    }

    public static void encode(byte[] encodedMovData, Path output) throws CodecMediaException {
        if (encodedMovData == null || encodedMovData.length == 0) {
            throw new CodecMediaException("MOV encoded data is empty");
        }
        try {
            Files.write(output, encodedMovData);
        } catch (IOException e) {
            throw new CodecMediaException("Failed to encode MOV: " + output, e);
        }
    }

    private static void validateDecodedProbe(MovProbeInfo info, Path input) throws CodecMediaException {
        if (info.majorBrand() == null || info.majorBrand().isBlank()) {
            throw new CodecMediaException("Decoded MOV has invalid major brand: " + input);
        }
        if (info.width() != null && info.width() <= 0) {
            throw new CodecMediaException("Decoded MOV has invalid width: " + input);
        }
        if (info.height() != null && info.height() <= 0) {
            throw new CodecMediaException("Decoded MOV has invalid height: " + input);
        }
        if (info.sampleRate() != null && info.sampleRate() <= 0) {
            throw new CodecMediaException("Decoded MOV has invalid sample rate: " + input);
        }
        if (info.channels() != null && info.channels() <= 0) {
            throw new CodecMediaException("Decoded MOV has invalid channel count: " + input);
        }
    }
}

