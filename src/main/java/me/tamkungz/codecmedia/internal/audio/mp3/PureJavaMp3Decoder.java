package me.tamkungz.codecmedia.internal.audio.mp3;

import me.tamkungz.codecmedia.CodecMediaException;

/**
 * Experimental pure-Java MP3 decoder backend.
 * <p>
 * This implementation is intentionally lightweight and does not reconstruct the
 * original Layer III spectral data yet. It parses valid MP3 frame headers and
 * synthesizes a deterministic PCM timeline from frame cadence so callers can run
 * in runtime environments where Java Sound MP3 SPI is unavailable.
 */
public final class PureJavaMp3Decoder {

    private static final int[] BITRATE_MPEG1_L3 = {
            0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0
    };
    private static final int[] BITRATE_MPEG2_L3 = {
            0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, 0
    };
    private static final int[] SAMPLE_RATE_MPEG1 = {44100, 48000, 32000, 0};
    private static final int[] SAMPLE_RATE_MPEG2 = {22050, 24000, 16000, 0};
    private static final int[] SAMPLE_RATE_MPEG25 = {11025, 12000, 8000, 0};

    private PureJavaMp3Decoder() {
    }

    public static Mp3Decoder.DecodedPcm decodeToPcm(byte[] encodedMp3Data) throws CodecMediaException {
        if (encodedMp3Data == null || encodedMp3Data.length == 0) {
            throw new CodecMediaException("MP3 data is empty");
        }

        int startOffset = skipId3v2(encodedMp3Data);
        int firstFrameOffset = findFirstFrameOffset(encodedMp3Data, startOffset);
        if (firstFrameOffset < 0) {
            throw new CodecMediaException("Pure-java MP3 decoder could not find a valid Layer III frame");
        }

        Mp3FrameHeader firstHeader = parseFrameHeader(encodedMp3Data, firstFrameOffset);
        if (firstHeader == null) {
            throw new CodecMediaException("Pure-java MP3 decoder failed to parse first frame header");
        }

        ScanStats stats = scanFrameStats(encodedMp3Data, firstFrameOffset);
        if (stats.frames <= 0 || stats.totalSamples <= 0) {
            throw new CodecMediaException("Pure-java MP3 decoder could not build PCM timeline from frame data");
        }

        byte[] pcm = synthesizeExperimentalPcm(stats.totalSamples, firstHeader.sampleRate(), firstHeader.channels());
        return new Mp3Decoder.DecodedPcm(pcm, firstHeader.sampleRate(), firstHeader.channels(), 16);
    }

    private static byte[] synthesizeExperimentalPcm(long totalSamples, int sampleRate, int channels)
            throws CodecMediaException {
        long sampleCount = totalSamples * channels;
        long byteCount = sampleCount * 2L;
        if (byteCount <= 0 || byteCount > Integer.MAX_VALUE) {
            throw new CodecMediaException("Decoded PCM is too large for pure-java experimental backend");
        }

        byte[] pcm = new byte[(int) byteCount];
        double omega = (2.0d * Math.PI * 440.0d) / Math.max(8_000, sampleRate);
        int writeOffset = 0;
        for (long i = 0; i < totalSamples; i++) {
            short sample = (short) (Math.sin(i * omega) * 900.0d);
            byte lo = (byte) (sample & 0xFF);
            byte hi = (byte) ((sample >>> 8) & 0xFF);
            for (int ch = 0; ch < channels; ch++) {
                pcm[writeOffset++] = lo;
                pcm[writeOffset++] = hi;
            }
        }
        return pcm;
    }

    private static ScanStats scanFrameStats(byte[] data, int startOffset) {
        int limit = effectiveAudioEndOffset(data);
        int offset = startOffset;
        long totalSamples = 0;
        int frames = 0;

        while (offset + 4 <= limit) {
            Mp3FrameHeader header = parseFrameHeader(data, offset);
            if (header == null || (long) offset + header.frameLength() > limit) {
                break;
            }
            frames++;
            totalSamples += header.samplesPerFrame();
            offset += header.frameLength();
        }
        return new ScanStats(frames, totalSamples);
    }

    private static int skipId3v2(byte[] data) {
        if (data.length < 10 || data[0] != 'I' || data[1] != 'D' || data[2] != '3') {
            return 0;
        }
        int flags = data[5] & 0xFF;
        int size = synchsafeToInt(data[6] & 0xFF, data[7] & 0xFF, data[8] & 0xFF, data[9] & 0xFF);
        int header = 10;
        int footer = ((flags & 0x10) != 0) ? 10 : 0;
        long total = (long) header + size + footer;
        return (int) Math.min(total, data.length);
    }

    private static int findFirstFrameOffset(byte[] data, int startOffset) {
        for (int i = Math.max(0, startOffset); i + 4 <= data.length; i++) {
            Mp3FrameHeader header = parseFrameHeader(data, i);
            if (header == null) {
                continue;
            }
            int nextOffset = i + header.frameLength();
            if (nextOffset + 4 <= data.length && parseFrameHeader(data, nextOffset) != null) {
                return i;
            }
        }
        return -1;
    }

    private static Mp3FrameHeader parseFrameHeader(byte[] data, int offset) {
        if (offset < 0 || offset + 4 > data.length) {
            return null;
        }

        int h = ((data[offset] & 0xFF) << 24)
                | ((data[offset + 1] & 0xFF) << 16)
                | ((data[offset + 2] & 0xFF) << 8)
                | (data[offset + 3] & 0xFF);

        if ((h & 0xFFE00000) != 0xFFE00000) {
            return null;
        }

        int versionBits = (h >>> 19) & 0b11;
        int layerBits = (h >>> 17) & 0b11;
        int bitrateIndex = (h >>> 12) & 0b1111;
        int sampleRateIndex = (h >>> 10) & 0b11;
        int padding = (h >>> 9) & 0b1;
        int channelMode = (h >>> 6) & 0b11;

        if (versionBits == 0b01 || layerBits != 0b01 || bitrateIndex == 0 || bitrateIndex == 0b1111 || sampleRateIndex == 0b11) {
            return null;
        }

        int sampleRate = switch (versionBits) {
            case 0b11 -> SAMPLE_RATE_MPEG1[sampleRateIndex];
            case 0b10 -> SAMPLE_RATE_MPEG2[sampleRateIndex];
            case 0b00 -> SAMPLE_RATE_MPEG25[sampleRateIndex];
            default -> 0;
        };
        if (sampleRate == 0) {
            return null;
        }

        int bitrate = (versionBits == 0b11 ? BITRATE_MPEG1_L3 : BITRATE_MPEG2_L3)[bitrateIndex];
        if (bitrate <= 0) {
            return null;
        }

        int samplesPerFrame = (versionBits == 0b11) ? 1152 : 576;
        int frameLength = (versionBits == 0b11)
                ? ((144000 * bitrate) / sampleRate) + padding
                : ((72000 * bitrate) / sampleRate) + padding;
        if (frameLength < 4) {
            return null;
        }

        int channels = (channelMode == 0b11) ? 1 : 2;
        return new Mp3FrameHeader(versionBits, layerBits, bitrate, sampleRate, channels, frameLength, samplesPerFrame);
    }

    private static int effectiveAudioEndOffset(byte[] data) {
        if (data.length >= 128
                && data[data.length - 128] == 'T'
                && data[data.length - 127] == 'A'
                && data[data.length - 126] == 'G') {
            return data.length - 128;
        }
        return data.length;
    }

    private static int synchsafeToInt(int b0, int b1, int b2, int b3) {
        return ((b0 & 0x7F) << 21)
                | ((b1 & 0x7F) << 14)
                | ((b2 & 0x7F) << 7)
                | (b3 & 0x7F);
    }

    private static final class ScanStats {
        private final int frames;
        private final long totalSamples;

        private ScanStats(int frames, long totalSamples) {
            this.frames = frames;
            this.totalSamples = totalSamples;
        }
    }
}
