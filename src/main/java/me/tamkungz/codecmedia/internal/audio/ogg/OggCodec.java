package me.tamkungz.codecmedia.internal.audio.ogg;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import me.tamkungz.codecmedia.CodecMediaException;

public final class OggCodec {

    private OggCodec() {
    }

    public static OggProbeInfo decode(Path input) throws CodecMediaException {
        try {
            byte[] bytes = Files.readAllBytes(input);
            return decode(bytes, input);
        } catch (IOException e) {
            throw new CodecMediaException("Failed to decode OGG: " + input, e);
        }
    }

    public static OggProbeInfo decode(byte[] bytes, Path sourceRef) throws CodecMediaException {
        OggProbeInfo info = OggParser.parse(bytes);
        validateDecodedProbe(info, sourceRef);
        return info;
    }

    public static void encode(byte[] encodedOggData, Path output) throws CodecMediaException {
        if (encodedOggData == null || encodedOggData.length == 0) {
            throw new CodecMediaException("OGG encoded data is empty");
        }
        try {
            Files.write(output, encodedOggData);
        } catch (IOException e) {
            throw new CodecMediaException("Failed to encode OGG: " + output, e);
        }
    }

    private static void validateDecodedProbe(OggProbeInfo info, Path input) throws CodecMediaException {
        if (info.sampleRate() <= 0 || info.channels() <= 0) {
            throw new CodecMediaException("Decoded OGG has invalid stream values: " + input);
        }
    }
}

