package me.tamkungz.codecmedia.internal.video.mp4;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import me.tamkungz.codecmedia.CodecMediaException;

public final class Mp4Codec {

    private Mp4Codec() {
    }

    public static Mp4ProbeInfo decode(Path input) throws CodecMediaException {
        try {
            byte[] bytes = Files.readAllBytes(input);
            return decode(bytes, input);
        } catch (IOException e) {
            throw new CodecMediaException("Failed to decode MP4: " + input, e);
        }
    }

    public static Mp4ProbeInfo decode(byte[] bytes, Path sourceRef) throws CodecMediaException {
        Mp4ProbeInfo info = Mp4Parser.parse(bytes);
        validateDecodedProbe(info, sourceRef);
        return info;
    }

    public static void encode(byte[] encodedMp4Data, Path output) throws CodecMediaException {
        if (encodedMp4Data == null || encodedMp4Data.length == 0) {
            throw new CodecMediaException("MP4 encoded data is empty");
        }
        try {
            Files.write(output, encodedMp4Data);
        } catch (IOException e) {
            throw new CodecMediaException("Failed to encode MP4: " + output, e);
        }
    }

    private static void validateDecodedProbe(Mp4ProbeInfo info, Path input) throws CodecMediaException {
        if (info.majorBrand() == null || info.majorBrand().isBlank()) {
            throw new CodecMediaException("Decoded MP4 has invalid major brand: " + input);
        }
        if (info.width() != null && info.width() <= 0) {
            throw new CodecMediaException("Decoded MP4 has invalid width: " + input);
        }
        if (info.height() != null && info.height() <= 0) {
            throw new CodecMediaException("Decoded MP4 has invalid height: " + input);
        }
    }
}

