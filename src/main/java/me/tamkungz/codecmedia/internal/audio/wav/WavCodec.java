package me.tamkungz.codecmedia.internal.audio.wav;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import me.tamkungz.codecmedia.CodecMediaException;

public final class WavCodec {

    private WavCodec() {
    }

    public static WavProbeInfo decode(Path input) throws CodecMediaException {
        try {
            byte[] bytes = Files.readAllBytes(input);
            return decode(bytes, input);
        } catch (IOException e) {
            throw new CodecMediaException("Failed to decode WAV: " + input, e);
        }
    }

    public static WavProbeInfo decode(byte[] bytes, Path sourceRef) throws CodecMediaException {
        WavProbeInfo info = WavParser.parse(bytes);
        validateDecodedProbe(info, sourceRef);
        return info;
    }

    public static void encode(byte[] encodedWavData, Path output) throws CodecMediaException {
        if (encodedWavData == null || encodedWavData.length == 0) {
            throw new CodecMediaException("WAV encoded data is empty");
        }
        try {
            Files.write(output, encodedWavData);
        } catch (IOException e) {
            throw new CodecMediaException("Failed to encode WAV: " + output, e);
        }
    }

    private static void validateDecodedProbe(WavProbeInfo info, Path input) throws CodecMediaException {
        if (info.sampleRate() <= 0 || info.channels() <= 0 || info.bitrateKbps() <= 0) {
            throw new CodecMediaException("Decoded WAV has invalid stream values: " + input);
        }
    }
}

