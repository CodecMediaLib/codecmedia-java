package me.tamkungz.codecmedia.internal.video.webm;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import me.tamkungz.codecmedia.CodecMediaException;

public final class WebmCodec {

    private WebmCodec() {
    }

    public static WebmProbeInfo decode(Path input) throws CodecMediaException {
        try {
            byte[] bytes = Files.readAllBytes(input);
            return decode(bytes, input);
        } catch (IOException e) {
            throw new CodecMediaException("Failed to decode WebM: " + input, e);
        }
    }

    public static WebmProbeInfo decode(byte[] bytes, Path sourceRef) throws CodecMediaException {
        WebmProbeInfo info = WebmParser.parse(bytes);
        validateDecodedProbe(info, sourceRef);
        return info;
    }

    public static void encode(byte[] encodedWebmData, Path output) throws CodecMediaException {
        if (encodedWebmData == null || encodedWebmData.length == 0) {
            throw new CodecMediaException("WebM encoded data is empty");
        }
        try {
            Files.write(output, encodedWebmData);
        } catch (IOException e) {
            throw new CodecMediaException("Failed to encode WebM: " + output, e);
        }
    }

    private static void validateDecodedProbe(WebmProbeInfo info, Path input) throws CodecMediaException {
        if (info.width() != null && info.width() <= 0) {
            throw new CodecMediaException("Decoded WebM has invalid width: " + input);
        }
        if (info.height() != null && info.height() <= 0) {
            throw new CodecMediaException("Decoded WebM has invalid height: " + input);
        }
        if (info.sampleRate() != null && info.sampleRate() <= 0) {
            throw new CodecMediaException("Decoded WebM has invalid sample rate: " + input);
        }
        if (info.channels() != null && info.channels() <= 0) {
            throw new CodecMediaException("Decoded WebM has invalid channel count: " + input);
        }
    }
}

