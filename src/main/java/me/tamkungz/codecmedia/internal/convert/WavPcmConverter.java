package me.tamkungz.codecmedia.internal.convert;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import me.tamkungz.codecmedia.CodecMediaException;
import me.tamkungz.codecmedia.model.ConversionResult;

/**
 * WAV <-> PCM converter.
 * <p>
 * - WAV -> PCM: extracts raw PCM payload from the WAV {@code data} chunk.
 * - PCM -> WAV: wraps raw PCM bytes in a canonical 16-bit LE PCM WAV container
 *   (44.1kHz, stereo).
 */
public final class WavPcmConverter implements MediaConverter {

    private static final int DEFAULT_SAMPLE_RATE = 44_100;
    private static final short DEFAULT_CHANNELS = 2;
    private static final short DEFAULT_BITS_PER_SAMPLE = 16;

    private static final String PRESET_PREFIX_SR = "sr=";
    private static final String PRESET_PREFIX_CHANNELS = "ch=";
    private static final String PRESET_PREFIX_BITS = "bits=";

    @Override
    public ConversionResult convert(ConversionRequest request) throws CodecMediaException {
        String source = request.sourceExtension();
        String target = request.targetExtension();

        boolean wavToPcm = "wav".equals(source) && "pcm".equals(target);
        boolean pcmToWav = "pcm".equals(source) && "wav".equals(target);
        if (!(wavToPcm || pcmToWav)) {
            throw new CodecMediaException(
                    "audio->audio transcoding is not implemented yet (supported pair: wav<->pcm only)"
            );
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

            if (wavToPcm) {
                byte[] wavBytes = Files.readAllBytes(request.input());
                byte[] pcmBytes = extractWavDataChunk(wavBytes);
                Files.write(output, pcmBytes);
                return new ConversionResult(output, request.targetExtension(), true);
            }

            byte[] pcmBytes = Files.readAllBytes(request.input());
            PcmWavParams params = parsePcmWavParams(request.options().preset());
            byte[] wavBytes = wrapPcmAsWav(pcmBytes, params);
            Files.write(output, wavBytes);
            return new ConversionResult(output, request.targetExtension(), true);
        } catch (IOException e) {
            throw new CodecMediaException("Failed to convert file: " + request.input(), e);
        }
    }

    private static byte[] extractWavDataChunk(byte[] wavBytes) throws CodecMediaException {
        if (wavBytes.length < 12) {
            throw new CodecMediaException("Invalid WAV: file too small");
        }
        String riff = new String(wavBytes, 0, 4, StandardCharsets.US_ASCII);
        String wave = new String(wavBytes, 8, 4, StandardCharsets.US_ASCII);
        boolean isRf64 = "RF64".equals(riff);
        if ((!"RIFF".equals(riff) && !isRf64) || !"WAVE".equals(wave)) {
            throw new CodecMediaException("Invalid WAV header");
        }

        int offset = 12;
        boolean sawFmt = false;
        Long ds64DataSize = null;
        while (offset + 8 <= wavBytes.length) {
            String chunkId = new String(wavBytes, offset, 4, StandardCharsets.US_ASCII);
            long rawChunkSize = readLeUInt32(wavBytes, offset + 4);
            int dataStart = offset + 8;

            if ("ds64".equals(chunkId)) {
                if (!isRf64 || rawChunkSize < 16 || (long) dataStart + 16 > wavBytes.length) {
                    throw new CodecMediaException("Invalid RF64 ds64 chunk");
                }
                long parsedDataSize = ByteBuffer.wrap(wavBytes, dataStart + 8, 8)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .getLong();
                if (parsedDataSize < 0) {
                    throw new CodecMediaException("RF64 data size is too large");
                }
                ds64DataSize = parsedDataSize;
            }

            long chunkSize = rawChunkSize;
            if (isRf64 && "data".equals(chunkId) && rawChunkSize == 0xFFFFFFFFL) {
                if (ds64DataSize == null) {
                    throw new CodecMediaException("RF64 data chunk uses 0xFFFFFFFF size but ds64 is missing");
                }
                chunkSize = ds64DataSize;
            }

            long dataEndLong = (long) dataStart + chunkSize;
            if (dataEndLong < dataStart || dataEndLong > wavBytes.length) {
                throw new CodecMediaException("WAV chunk exceeds file bounds: " + chunkId);
            }

            if ("fmt ".equals(chunkId)) {
                if (chunkSize < 16) {
                    throw new CodecMediaException("Invalid WAV fmt chunk");
                }
                int audioFormat = readLeUnsignedShort(wavBytes, dataStart);
                if (audioFormat != 1) {
                    throw new CodecMediaException("Unsupported WAV format for PCM extraction: " + audioFormat);
                }
                sawFmt = true;
            }

            if ("data".equals(chunkId)) {
                if (!sawFmt) {
                    throw new CodecMediaException("Invalid WAV: missing fmt chunk before data");
                }
                if (chunkSize > Integer.MAX_VALUE) {
                    throw new CodecMediaException("WAV data chunk is too large");
                }
                byte[] out = new byte[(int) chunkSize];
                System.arraycopy(wavBytes, dataStart, out, 0, (int) chunkSize);
                return out;
            }

            long paddedSize = (chunkSize % 2 == 0) ? chunkSize : chunkSize + 1L;
            long nextOffset = (long) dataStart + paddedSize;
            if (nextOffset < dataStart || nextOffset > Integer.MAX_VALUE || nextOffset > wavBytes.length) {
                throw new CodecMediaException("WAV chunk offset overflow");
            }
            offset = (int) nextOffset;
        }

        throw new CodecMediaException("WAV data chunk not found");
    }

    private static byte[] wrapPcmAsWav(byte[] pcmBytes, PcmWavParams params) throws CodecMediaException {
        int dataSize = pcmBytes.length;
        long totalBytes = 44L + dataSize;
        if (totalBytes > Integer.MAX_VALUE) {
            throw new CodecMediaException("PCM data too large for WAV container");
        }

        int sampleRate = params.sampleRate();
        short channels = params.channels();
        short bitsPerSample = params.bitsPerSample();

        int bytesPerSample = bitsPerSample / 8;
        int byteRate = sampleRate * channels * bytesPerSample;
        short blockAlign = (short) (channels * bytesPerSample);

        int riffChunkSize = (int) (totalBytes - 8L);
        ByteBuffer b = ByteBuffer.allocate((int) totalBytes).order(ByteOrder.LITTLE_ENDIAN);
        b.put((byte) 'R').put((byte) 'I').put((byte) 'F').put((byte) 'F');
        b.putInt(riffChunkSize);
        b.put((byte) 'W').put((byte) 'A').put((byte) 'V').put((byte) 'E');

        b.put((byte) 'f').put((byte) 'm').put((byte) 't').put((byte) ' ');
        b.putInt(16);
        b.putShort((short) 1);
        b.putShort(channels);
        b.putInt(sampleRate);
        b.putInt(byteRate);
        b.putShort(blockAlign);
        b.putShort(bitsPerSample);

        b.put((byte) 'd').put((byte) 'a').put((byte) 't').put((byte) 'a');
        b.putInt(dataSize);
        b.put(pcmBytes);
        return b.array();
    }

    private static long readLeUInt32(byte[] bytes, int offset) throws CodecMediaException {
        if (offset < 0 || offset + 4 > bytes.length) {
            throw new CodecMediaException("Unexpected end of WAV data");
        }
        long b0 = bytes[offset] & 0xFFL;
        long b1 = bytes[offset + 1] & 0xFFL;
        long b2 = bytes[offset + 2] & 0xFFL;
        long b3 = bytes[offset + 3] & 0xFFL;
        return b0 | (b1 << 8) | (b2 << 16) | (b3 << 24);
    }

    private static int readLeUnsignedShort(byte[] bytes, int offset) throws CodecMediaException {
        if (offset < 0 || offset + 2 > bytes.length) {
            throw new CodecMediaException("Unexpected end of WAV data");
        }
        return (bytes[offset] & 0xFF) | ((bytes[offset + 1] & 0xFF) << 8);
    }

    private static PcmWavParams parsePcmWavParams(String preset) throws CodecMediaException {
        int sampleRate = DEFAULT_SAMPLE_RATE;
        short channels = DEFAULT_CHANNELS;
        short bitsPerSample = DEFAULT_BITS_PER_SAMPLE;

        if (preset == null || preset.isBlank() || "balanced".equalsIgnoreCase(preset.trim())) {
            return new PcmWavParams(sampleRate, channels, bitsPerSample);
        }

        String[] tokens = preset.toLowerCase(Locale.ROOT).split(",");
        for (String rawToken : tokens) {
            String token = rawToken.trim();
            if (token.isEmpty()) {
                continue;
            }
            if (token.startsWith(PRESET_PREFIX_SR)) {
                sampleRate = parseIntParam(token.substring(PRESET_PREFIX_SR.length()), "sr", 8_000, 384_000);
                continue;
            }
            if (token.startsWith(PRESET_PREFIX_CHANNELS)) {
                int parsed = parseIntParam(token.substring(PRESET_PREFIX_CHANNELS.length()), "ch", 1, 8);
                channels = (short) parsed;
                continue;
            }
            if (token.startsWith(PRESET_PREFIX_BITS)) {
                int parsed = parseBitsPerSample(token.substring(PRESET_PREFIX_BITS.length()));
                bitsPerSample = (short) parsed;
                continue;
            }
            throw new CodecMediaException("Unsupported preset token for pcm->wav: " + token);
        }

        return new PcmWavParams(sampleRate, channels, bitsPerSample);
    }

    private static int parseIntParam(String value, String name, int min, int max) throws CodecMediaException {
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed < min || parsed > max) {
                throw new CodecMediaException(name + " out of range: " + parsed + " (" + min + "-" + max + ")");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new CodecMediaException("Invalid integer for " + name + ": " + value, e);
        }
    }

    private static int parseBitsPerSample(String value) throws CodecMediaException {
        int parsed = parseIntParam(value, "bits", 8, 32);
        return switch (parsed) {
            case 8, 16, 24, 32 -> parsed;
            default -> throw new CodecMediaException("Unsupported bits value in preset (allowed: 8,16,24,32)");
        };
    }

    private record PcmWavParams(int sampleRate, short channels, short bitsPerSample) {
    }
}

