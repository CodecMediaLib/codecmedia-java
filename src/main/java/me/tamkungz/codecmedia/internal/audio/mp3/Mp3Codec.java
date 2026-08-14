package me.tamkungz.codecmedia.internal.audio.mp3;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import me.tamkungz.codecmedia.CodecMediaException;

public final class Mp3Codec {

    private Mp3Codec() {
    }

    public static Mp3ProbeInfo decode(Path input) throws CodecMediaException {
        try {
            byte[] bytes = Files.readAllBytes(input);
            return decode(bytes, input);
        } catch (IOException e) {
            throw new CodecMediaException("Failed to decode MP3: " + input, e);
        }
    }

    public static Mp3ProbeInfo decode(byte[] bytes, Path sourceRef) throws CodecMediaException {
        Mp3ProbeInfo info = Mp3Parser.parse(bytes);
        validateDecodedProbe(info, sourceRef);
        return info;
    }

    public static void encode(byte[] encodedMp3Data, Path output) throws CodecMediaException {
        if (encodedMp3Data == null || encodedMp3Data.length == 0) {
            throw new CodecMediaException("MP3 encoded data is empty");
        }
        try {
            Files.write(output, encodedMp3Data);
        } catch (IOException e) {
            throw new CodecMediaException("Failed to encode MP3: " + output, e);
        }
    }

    private static void validateDecodedProbe(Mp3ProbeInfo info, Path input) throws CodecMediaException {
        if (info.sampleRate() <= 0 || info.channels() <= 0) {
            throw new CodecMediaException("Decoded MP3 has invalid stream values: " + input);
        }
    }
}

