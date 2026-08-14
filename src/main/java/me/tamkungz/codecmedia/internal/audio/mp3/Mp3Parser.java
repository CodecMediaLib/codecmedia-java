package me.tamkungz.codecmedia.internal.audio.mp3;

import java.util.HashSet;
import java.util.Set;

import me.tamkungz.codecmedia.CodecMediaException;
import me.tamkungz.codecmedia.internal.audio.BitrateMode;
import me.tamkungz.codecmedia.internal.io.ByteArrayReader;

public final class Mp3Parser {

    private static final int[] BITRATE_MPEG1_L3 = {
            0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0
    };
    private static final int[] BITRATE_MPEG2_L3 = {
            0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, 0
    };
    private static final int[] SAMPLE_RATE_MPEG1 = {44100, 48000, 32000, 0};
    private static final int[] SAMPLE_RATE_MPEG2 = {22050, 24000, 16000, 0};
    private static final int[] SAMPLE_RATE_MPEG25 = {11025, 12000, 8000, 0};

    private Mp3Parser() {
    }

    public static Mp3ProbeInfo parse(byte[] data) throws CodecMediaException {
        if (data == null || data.length < 4) {
            throw new CodecMediaException("Invalid MP3 data: too small");
        }

        ByteArrayReader reader = new ByteArrayReader(data);
        int audioStart = skipId3v2(reader);
        int firstFrameOffset = findFrameOffset(data, audioStart);
        if (firstFrameOffset < 0) {
            if (containsUnsupportedMpegLayer(data, audioStart)) {
                throw new CodecMediaException("Unsupported MPEG audio layer: only Layer III (MP3) is supported");
            }
            throw new CodecMediaException("No valid MP3 frame found");
        }

        Mp3FrameHeader firstFrame = parseFrameHeader(data, firstFrameOffset);
        if (firstFrame == null) {
            throw new CodecMediaException("Invalid first MP3 frame");
        }

        int xingFrames = readXingFrameCountIfPresent(data, firstFrameOffset, firstFrame);
        int vbriFrames = readVbriFrameCountIfPresent(data, firstFrameOffset, firstFrame);

        int scanLimit = effectiveAudioEndOffset(data);
        ParseStats stats = scanFrames(data, firstFrameOffset, scanLimit, firstFrame.sampleRate(), firstFrame.samplesPerFrame());
        long durationMillis = estimateDurationMillis(stats, xingFrames, vbriFrames);
        int avgBitrate = estimateAverageBitrateKbps(stats, durationMillis);
        BitrateMode mode = detectBitrateMode(stats, xingFrames, vbriFrames);

        return new Mp3ProbeInfo(
                "mp3",
                firstFrame.sampleRate(),
                firstFrame.channels(),
                avgBitrate > 0 ? avgBitrate : firstFrame.bitrateKbps(),
                mode,
                durationMillis
        );
    }

    private static int skipId3v2(ByteArrayReader reader) {
        if (reader.length() < 10) {
            return 0;
        }
        if (reader.getU8(0) != 'I' || reader.getU8(1) != 'D' || reader.getU8(2) != '3') {
            return 0;
        }

        int flags = reader.getU8(5);
        int size = synchsafeToInt(reader.getU8(6), reader.getU8(7), reader.getU8(8), reader.getU8(9));
        int total = 10 + size + ((flags & 0x10) != 0 ? 10 : 0);
        return Math.min(total, reader.length());
    }

    private static int synchsafeToInt(int b0, int b1, int b2, int b3) {
        return ((b0 & 0x7F) << 21)
                | ((b1 & 0x7F) << 14)
                | ((b2 & 0x7F) << 7)
                | (b3 & 0x7F);
    }

    private static int findFrameOffset(byte[] data, int start) {
        for (int i = Math.max(0, start); i + 4 <= data.length; i++) {
            Mp3FrameHeader h = parseFrameHeader(data, i);
            if (h == null) {
                continue;
            }
            int next = i + h.frameLength();
            if (next + 4 <= data.length && parseFrameHeader(data, next) != null) {
                return i;
            }
        }
        return -1;
    }

    private static boolean containsUnsupportedMpegLayer(byte[] data, int start) {
        for (int i = Math.max(0, start); i + 4 <= data.length; i++) {
            int h = ((data[i] & 0xFF) << 24)
                    | ((data[i + 1] & 0xFF) << 16)
                    | ((data[i + 2] & 0xFF) << 8)
                    | (data[i + 3] & 0xFF);

            if ((h & 0xFFE00000) != 0xFFE00000) {
                continue;
            }

            int versionBits = (h >>> 19) & 0b11;
            int layerBits = (h >>> 17) & 0b11;
            int sampleRateIndex = (h >>> 10) & 0b11;

            if (versionBits == 0b01 || sampleRateIndex == 0b11) {
                continue;
            }
            if (layerBits == 0b10 || layerBits == 0b11) {
                return true;
            }
        }
        return false;
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

    private static int readXingFrameCountIfPresent(byte[] data, int frameOffset, Mp3FrameHeader header) {
        int sideInfoSize = header.versionBits() == 0b11
                ? (header.channels() == 1 ? 17 : 32)
                : (header.channels() == 1 ? 9 : 17);
        int xingOffset = frameOffset + 4 + sideInfoSize;
        if (xingOffset + 16 > data.length) {
            return -1;
        }
        String tag = ascii(data, xingOffset, 4);
        if (!"Xing".equals(tag) && !"Info".equals(tag)) {
            return -1;
        }
        int flags = readIntBE(data, xingOffset + 4);
        if ((flags & 0x1) == 0 || xingOffset + 12 > data.length) {
            return -1;
        }
        return readIntBE(data, xingOffset + 8);
    }

    private static int readVbriFrameCountIfPresent(byte[] data, int frameOffset, Mp3FrameHeader header) {
        int vbriOffset = frameOffset + 4 + 32;
        if (vbriOffset + 18 > data.length) {
            return -1;
        }
        if (!"VBRI".equals(ascii(data, vbriOffset, 4))) {
            return -1;
        }
        return readIntBE(data, vbriOffset + 14);
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

    private static ParseStats scanFrames(byte[] data, int startOffset, int scanLimit, int sampleRate, int samplesPerFrame) {
        int offset = startOffset;
        long totalBits = 0;
        long totalSamples = 0;
        int frames = 0;
        Set<Integer> bitrates = new HashSet<>();

        while (offset + 4 <= scanLimit) {
            Mp3FrameHeader h = parseFrameHeader(data, offset);
            if (h == null || offset + h.frameLength() > scanLimit) {
                break;
            }

            frames++;
            bitrates.add(h.bitrateKbps());
            totalBits += (long) h.frameLength() * 8;
            totalSamples += h.samplesPerFrame();
            offset += h.frameLength();
        }

        return new ParseStats(frames, bitrates, totalBits, totalSamples, sampleRate, samplesPerFrame);
    }

    private static long estimateDurationMillis(ParseStats stats, int xingFrames, int vbriFrames) {
        if (stats.frames() <= 0) {
            return 0;
        }
        int knownFrames = xingFrames > 0 ? xingFrames : vbriFrames;
        if (knownFrames > 0 && stats.samplesPerFrame() > 0 && stats.sampleRate() > 0) {
            return ((long) knownFrames * stats.samplesPerFrame() * 1000L) / stats.sampleRate();
        }
        if (stats.totalSamples() > 0 && stats.sampleRate() > 0) {
            return (stats.totalSamples() * 1000L) / stats.sampleRate();
        }
        return 0;
    }

    private static int estimateAverageBitrateKbps(ParseStats stats, long durationMillis) {
        if (durationMillis <= 0 || stats.totalBits() <= 0) {
            return 0;
        }
        return (int) ((stats.totalBits() * 1000L) / durationMillis / 1000L);
    }

    private static BitrateMode detectBitrateMode(ParseStats stats, int xingFrames, int vbriFrames) {
        if (stats.frames() <= 1) {
            return BitrateMode.UNKNOWN;
        }
        boolean hasVbrTag = xingFrames > 0 || vbriFrames > 0;
        boolean oneBitrate = stats.bitrates().size() <= 1;

        if (hasVbrTag && oneBitrate) {
            return BitrateMode.CVBR;
        }
        if (hasVbrTag || !oneBitrate) {
            return BitrateMode.VBR;
        }
        return BitrateMode.CBR;
    }

    private static int readIntBE(byte[] data, int offset) {
        if (offset + 4 > data.length) {
            return -1;
        }
        return ((data[offset] & 0xFF) << 24)
                | ((data[offset + 1] & 0xFF) << 16)
                | ((data[offset + 2] & 0xFF) << 8)
                | (data[offset + 3] & 0xFF);
    }

    private static String ascii(byte[] data, int offset, int len) {
        if (offset < 0 || offset + len > data.length) {
            return "";
        }
        return new String(data, offset, len, java.nio.charset.StandardCharsets.US_ASCII);
    }

    private record ParseStats(
            int frames,
            Set<Integer> bitrates,
            long totalBits,
            long totalSamples,
            int sampleRate,
            int samplesPerFrame
    ) {
    }
}

