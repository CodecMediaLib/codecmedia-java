package me.tamkungz.codecmedia.internal.video.webm;

import java.nio.charset.StandardCharsets;

import me.tamkungz.codecmedia.CodecMediaException;

public final class WebmParser {

    private static final byte[] EBML_ID = new byte[] {(byte) 0x1A, (byte) 0x45, (byte) 0xDF, (byte) 0xA3};

    private WebmParser() {
    }

    public static boolean isLikelyWebm(byte[] bytes) {
        if (bytes == null || bytes.length < 16) {
            return false;
        }
        return matches(bytes, 0, EBML_ID) && indexOf(bytes, "webm".getBytes(StandardCharsets.US_ASCII), 0) >= 0;
    }

    public static WebmProbeInfo parse(byte[] bytes) throws CodecMediaException {
        if (bytes == null || bytes.length < 16) {
            throw new CodecMediaException("WebM data is empty or too short");
        }
        if (!matches(bytes, 0, EBML_ID)) {
            throw new CodecMediaException("Not an EBML/WebM file (missing EBML header)");
        }

        double durationSeconds = parseDurationSeconds(bytes);
        long timecodeScale = parseTimecodeScale(bytes);
        Long durationMillis = durationSeconds > 0.0d
                ? Math.round(durationSeconds * (timecodeScale / 1_000_000.0d))
                : null;

        Integer width = null;
        Integer height = null;
        String videoCodec = null;
        String audioCodec = null;
        Integer sampleRate = null;
        Integer channels = null;
        Double frameRate = null;
        Integer videoBitrateKbps = null;
        Integer audioBitrateKbps = null;

        int cursor = 0;
        while (cursor + 2 < bytes.length) {
            if ((bytes[cursor] & 0xFF) == 0xAE) { // TrackEntry
                int sizeLen = vintLength(bytes[cursor + 1] & 0xFF);
                if (sizeLen == 0 || cursor + 1 + sizeLen >= bytes.length) {
                    break;
                }
                long payloadSize = readVintValue(bytes, cursor + 1, sizeLen);
                int payloadOffset = cursor + 1 + sizeLen;
                int payloadEnd = (int) Math.min((long) bytes.length, payloadOffset + payloadSize);
                TrackEntry t = parseTrackEntry(bytes, payloadOffset, payloadEnd);
                if (t.trackType != null && t.trackType == 1) {
                    width = t.width != null ? t.width : width;
                    height = t.height != null ? t.height : height;
                    videoCodec = t.codec != null ? t.codec : videoCodec;
                    frameRate = t.frameRate != null ? t.frameRate : frameRate;
                    videoBitrateKbps = t.bitrateKbps != null ? t.bitrateKbps : videoBitrateKbps;
                } else if (t.trackType != null && t.trackType == 2) {
                    audioCodec = t.codec != null ? t.codec : audioCodec;
                    sampleRate = t.sampleRate != null ? t.sampleRate : sampleRate;
                    channels = t.channels != null ? t.channels : channels;
                    audioBitrateKbps = t.bitrateKbps != null ? t.bitrateKbps : audioBitrateKbps;
                }
                cursor = payloadEnd;
                continue;
            }
            cursor++;
        }

        String displayAspectRatio = null;
        if (width != null && height != null && width > 0 && height > 0) {
            int gcd = gcd(width, height);
            displayAspectRatio = (width / gcd) + ":" + (height / gcd);
        }

        if (durationMillis != null && durationMillis > 0) {
            int totalKbps = (int) (((long) bytes.length * 8L * 1000L) / (durationMillis * 1000L));
            if (videoBitrateKbps == null && width != null && height != null && width > 0 && height > 0) {
                videoBitrateKbps = totalKbps;
            } else if (audioBitrateKbps == null && (sampleRate != null || channels != null)) {
                audioBitrateKbps = totalKbps;
            }
        }

        return new WebmProbeInfo(
                durationMillis,
                width,
                height,
                videoCodec,
                audioCodec,
                sampleRate,
                channels,
                frameRate,
                videoBitrateKbps,
                audioBitrateKbps,
                null,
                displayAspectRatio
        );
    }

    private static TrackEntry parseTrackEntry(byte[] bytes, int offset, int end) throws CodecMediaException {
        TrackEntry out = new TrackEntry();
        int p = offset;
        while (p + 2 <= end) {
            int idLen = ebmlIdLength(bytes[p] & 0xFF);
            if (idLen == 0 || p + idLen >= end) {
                break;
            }

            int id = readElementId(bytes, p, idLen);
            int sizeLen = vintLength(bytes[p + idLen] & 0xFF);
            if (sizeLen == 0 || p + idLen + sizeLen > end) {
                break;
            }

            long valueSize = readVintValue(bytes, p + idLen, sizeLen);
            int valueOffset = p + idLen + sizeLen;
            int valueEnd = (int) Math.min((long) end, valueOffset + valueSize);

            if (id == 0x83 && valueSize >= 1) { // TrackType
                out.trackType = bytes[valueOffset] & 0xFF;
            } else if (id == 0x86) { // CodecID
                out.codec = new String(bytes, valueOffset, Math.max(0, valueEnd - valueOffset), StandardCharsets.US_ASCII).trim();
            } else if (id == 0xB0) { // PixelWidth
                out.width = readUnsigned(bytes, valueOffset, valueEnd - valueOffset);
            } else if (id == 0xBA) { // PixelHeight
                out.height = readUnsigned(bytes, valueOffset, valueEnd - valueOffset);
            } else if (id == 0x9F) { // Channels
                out.channels = readUnsigned(bytes, valueOffset, valueEnd - valueOffset);
            } else if (id == 0xB5) { // SamplingFrequency float
                out.sampleRate = parseEbmlFloatAsInt(bytes, valueOffset, valueEnd - valueOffset);
            } else if (id == 0x258688) { // Track default bitrate (Bit/s)
                Integer bps = readUnsignedLongAsInt(bytes, valueOffset, valueEnd - valueOffset);
                if (bps != null && bps > 0) {
                    out.bitrateKbps = (int) Math.max(1L, Math.round(bps / 1000.0d));
                }
            } else if (id == 0x23E383) {
                // DefaultDuration with 3-byte ID (0x23E383)
                out.frameRate = parseDefaultDurationFrameRate(bytes, valueOffset, (int) valueSize, end);
            }

            p = valueEnd;
        }
        return out;
    }

    private static Double parseDefaultDurationFrameRate(byte[] bytes, int valueOffset, int valueSize, int end) {
        if (valueSize <= 0 || valueSize > 8) {
            return null;
        }
        if (valueOffset < 0 || valueOffset + valueSize > end || valueOffset + valueSize > bytes.length) {
            return null;
        }

        long nanoseconds = 0;
        for (int i = 0; i < valueSize; i++) {
            nanoseconds = (nanoseconds << 8) | (bytes[valueOffset + i] & 0xFFL);
        }
        if (nanoseconds <= 0) {
            return null;
        }
        return 1_000_000_000.0d / nanoseconds;
    }

    private static double parseDurationSeconds(byte[] bytes) {
        int idx = indexOf(bytes, new byte[] {(byte) 0x44, (byte) 0x89}, 0);
        if (idx < 0 || idx + 3 >= bytes.length) {
            return -1.0d;
        }
        int sizeLen = vintLength(bytes[idx + 2] & 0xFF);
        if (sizeLen == 0 || idx + 2 + sizeLen >= bytes.length) {
            return -1.0d;
        }
        int size = (int) readVintValue(bytes, idx + 2, sizeLen);
        int valueOffset = idx + 2 + sizeLen;
        if (size != 4 && size != 8) {
            return -1.0d;
        }
        if (valueOffset + size > bytes.length) {
            return -1.0d;
        }
        if (size == 4) {
            int raw = ((bytes[valueOffset] & 0xFF) << 24)
                    | ((bytes[valueOffset + 1] & 0xFF) << 16)
                    | ((bytes[valueOffset + 2] & 0xFF) << 8)
                    | (bytes[valueOffset + 3] & 0xFF);
            return Float.intBitsToFloat(raw);
        }
        long raw = 0L;
        for (int i = 0; i < 8; i++) {
            raw = (raw << 8) | (bytes[valueOffset + i] & 0xFFL);
        }
        return Double.longBitsToDouble(raw);
    }

    private static long parseTimecodeScale(byte[] bytes) {
        int idx = indexOf(bytes, new byte[] {(byte) 0x2A, (byte) 0xD7, (byte) 0xB1}, 0);
        if (idx < 0 || idx + 4 >= bytes.length) {
            return 1_000_000L;
        }
        int sizeLen = vintLength(bytes[idx + 3] & 0xFF);
        if (sizeLen == 0 || idx + 3 + sizeLen >= bytes.length) {
            return 1_000_000L;
        }
        int size = (int) readVintValue(bytes, idx + 3, sizeLen);
        int valueOffset = idx + 3 + sizeLen;
        if (size <= 0 || size > 8 || valueOffset + size > bytes.length) {
            return 1_000_000L;
        }
        long v = 0;
        for (int i = 0; i < size; i++) {
            v = (v << 8) | (bytes[valueOffset + i] & 0xFFL);
        }
        return v > 0 ? v : 1_000_000L;
    }

    private static int parseEbmlFloatAsInt(byte[] bytes, int offset, int size) {
        if (size == 4 && offset + 4 <= bytes.length) {
            int raw = ((bytes[offset] & 0xFF) << 24)
                    | ((bytes[offset + 1] & 0xFF) << 16)
                    | ((bytes[offset + 2] & 0xFF) << 8)
                    | (bytes[offset + 3] & 0xFF);
            return (int) Math.round(Float.intBitsToFloat(raw));
        }
        if (size == 8 && offset + 8 <= bytes.length) {
            long raw = 0L;
            for (int i = 0; i < 8; i++) {
                raw = (raw << 8) | (bytes[offset + i] & 0xFFL);
            }
            return (int) Math.round(Double.longBitsToDouble(raw));
        }
        return 0;
    }

    private static int readUnsigned(byte[] bytes, int offset, int size) {
        if (size <= 0 || size > 4 || offset + size > bytes.length) {
            return 0;
        }
        int value = 0;
        for (int i = 0; i < size; i++) {
            value = (value << 8) | (bytes[offset + i] & 0xFF);
        }
        return value;
    }

    private static Integer readUnsignedLongAsInt(byte[] bytes, int offset, int size) {
        if (size <= 0 || size > 8 || offset + size > bytes.length) {
            return null;
        }
        long value = 0;
        for (int i = 0; i < size; i++) {
            value = (value << 8) | (bytes[offset + i] & 0xFFL);
        }
        if (value <= 0 || value > Integer.MAX_VALUE) {
            return null;
        }
        return (int) value;
    }

    private static int gcd(int a, int b) {
        int x = Math.abs(a);
        int y = Math.abs(b);
        while (y != 0) {
            int t = x % y;
            x = y;
            y = t;
        }
        return x == 0 ? 1 : x;
    }

    private static int indexOf(byte[] haystack, byte[] needle, int from) {
        if (needle.length == 0 || haystack.length < needle.length || from < 0) {
            return -1;
        }
        outer:
        for (int i = from; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private static boolean matches(byte[] bytes, int offset, byte[] expected) {
        if (offset < 0 || offset + expected.length > bytes.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if (bytes[offset + i] != expected[i]) {
                return false;
            }
        }
        return true;
    }

    private static int vintLength(int firstByte) {
        if (firstByte == 0) {
            return 0;
        }
        int mask = 0x80;
        int len = 1;
        while ((firstByte & mask) == 0) {
            mask >>>= 1;
            len++;
            if (len > 8) {
                return 0;
            }
        }
        return len;
    }

    private static int ebmlIdLength(int firstByte) {
        if (firstByte == 0) {
            return 0;
        }
        int mask = 0x80;
        int len = 1;
        while ((firstByte & mask) == 0) {
            mask >>>= 1;
            len++;
            if (len > 4) {
                return 0;
            }
        }
        return len;
    }

    private static int readElementId(byte[] bytes, int offset, int len) {
        int value = 0;
        for (int i = 0; i < len; i++) {
            value = (value << 8) | (bytes[offset + i] & 0xFF);
        }
        return value;
    }

    private static long readVintValue(byte[] bytes, int offset, int len) {
        long value = bytes[offset] & (0xFF >>> len);
        for (int i = 1; i < len; i++) {
            value = (value << 8) | (bytes[offset + i] & 0xFFL);
        }
        return value;
    }

    private static final class TrackEntry {
        Integer trackType;
        String codec;
        Integer width;
        Integer height;
        Integer sampleRate;
        Integer channels;
        Double frameRate;
        Integer bitrateKbps;
    }
}

