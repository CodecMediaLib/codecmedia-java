package me.tamkungz.codecmedia.internal.convert;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import me.tamkungz.codecmedia.CodecMediaException;
import me.tamkungz.codecmedia.internal.audio.mp3.Mp3Decoder;
import me.tamkungz.codecmedia.internal.audio.mp3.PureJavaMp3Decoder;
import me.tamkungz.codecmedia.model.ConversionResult;

/**
 * MP3 decoder-backed converter for MP3 -> PCM/WAV.
 */
public final class Mp3PcmWavConverter implements MediaConverter {

    private static final String PRESET_PREFIX_DECODER = "decoder=";

    @Override
    public ConversionResult convert(ConversionRequest request) throws CodecMediaException {
        boolean toPcm = "mp3".equals(request.sourceExtension()) && "pcm".equals(request.targetExtension());
        boolean toWav = "mp3".equals(request.sourceExtension()) && "wav".equals(request.targetExtension());
        if (!toPcm && !toWav) {
            throw new CodecMediaException("Unsupported MP3 decoder route: "
                    + request.sourceExtension() + "->" + request.targetExtension());
        }

        Path output = request.output();
        try {
            Path parent = output.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            if (Files.exists(output) && !request.options().overwrite()) {
                throw new CodecMediaException("Output already exists and overwrite is disabled: " + output);
            }

            byte[] inputBytes = Files.readAllBytes(request.input());
            DecoderBackend backend = resolveDecoderBackend(request.options().preset());
            Mp3Decoder.DecodedPcm decoded = decodeByBackend(inputBytes, backend);

            if (toPcm) {
                Files.write(output, decoded.pcmS16Le());
            } else {
                byte[] wav = wrapPcmAsWav(decoded.pcmS16Le(), decoded.sampleRate(), decoded.channels(), decoded.bitsPerSample());
                Files.write(output, wav);
            }

            return new ConversionResult(output, request.targetExtension(), true);
        } catch (IOException e) {
            throw new CodecMediaException("Failed to convert MP3: " + request.input(), e);
        }
    }

    static DecoderBackend resolveDecoderBackend(String preset) throws CodecMediaException {
        if (preset == null || preset.isBlank() || "balanced".equalsIgnoreCase(preset.trim())) {
            return DecoderBackend.JAVA_SOUND;
        }

        DecoderBackend backend = DecoderBackend.JAVA_SOUND;
        String[] tokens = preset.toLowerCase(Locale.ROOT).split(",");
        for (String rawToken : tokens) {
            String token = rawToken.trim();
            if (token.isEmpty() || "balanced".equals(token)) {
                continue;
            }
            if (!token.startsWith(PRESET_PREFIX_DECODER)) {
                throw new CodecMediaException("Unsupported preset token for mp3 decode: " + token);
            }

            String decoderToken = token.substring(PRESET_PREFIX_DECODER.length()).trim();
            backend = DecoderBackend.fromToken(decoderToken);
        }
        return backend;
    }

    private static Mp3Decoder.DecodedPcm decodeByBackend(byte[] inputBytes, DecoderBackend backend)
            throws CodecMediaException {
        return switch (backend) {
            case JAVA_SOUND -> Mp3Decoder.decodeToPcm(inputBytes);
            case PURE_JAVA -> PureJavaMp3Decoder.decodeToPcm(inputBytes);
        };
    }

    private static byte[] wrapPcmAsWav(byte[] pcm, int sampleRate, int channels, int bitsPerSample) throws CodecMediaException {
        long totalSize = 44L + pcm.length;
        if (totalSize > Integer.MAX_VALUE) {
            throw new CodecMediaException("Decoded PCM is too large for WAV container");
        }

        int bytesPerSample = bitsPerSample / 8;
        int byteRate = sampleRate * channels * bytesPerSample;
        short blockAlign = (short) (channels * bytesPerSample);
        int riffChunkSize = (int) totalSize - 8;

        ByteBuffer b = ByteBuffer.allocate((int) totalSize).order(ByteOrder.LITTLE_ENDIAN);
        b.put((byte) 'R').put((byte) 'I').put((byte) 'F').put((byte) 'F');
        b.putInt(riffChunkSize);
        b.put((byte) 'W').put((byte) 'A').put((byte) 'V').put((byte) 'E');

        b.put((byte) 'f').put((byte) 'm').put((byte) 't').put((byte) ' ');
        b.putInt(16);
        b.putShort((short) 1);
        b.putShort((short) channels);
        b.putInt(sampleRate);
        b.putInt(byteRate);
        b.putShort(blockAlign);
        b.putShort((short) bitsPerSample);

        b.put((byte) 'd').put((byte) 'a').put((byte) 't').put((byte) 'a');
        b.putInt(pcm.length);
        b.put(pcm);
        return b.array();
    }

    enum DecoderBackend {
        JAVA_SOUND,
        PURE_JAVA;

        static DecoderBackend fromToken(String token) throws CodecMediaException {
            return switch (token) {
                case "javasound" -> JAVA_SOUND;
                case "pure-java", "layer3" -> PURE_JAVA;
                default -> throw new CodecMediaException(
                        "Unsupported MP3 decoder token: " + token
                                + " (supported: decoder=javasound, decoder=pure-java, decoder=layer3)"
                );
            };
        }
    }
}
