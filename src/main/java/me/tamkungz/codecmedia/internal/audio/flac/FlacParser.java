package me.tamkungz.codecmedia.internal.audio.flac;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import me.tamkungz.codecmedia.CodecMediaException;
import me.tamkungz.codecmedia.internal.audio.BitrateMode;

public final class FlacParser {

    private static final byte[] FLAC_MAGIC = new byte[] {'f', 'L', 'a', 'C'};
    private static final int ID3V2_HEADER_SIZE = 10;
    private static final int FLAC_MAGIC_SCAN_LIMIT = 4096;

    private FlacParser() {
    }

    public static FlacProbeInfo parse(byte[] bytes) throws CodecMediaException {
        int flacOffset = findFlacOffset(bytes);
        if (flacOffset < 0) {
            throw new CodecMediaException("Not a FLAC file");
        }

        int offset = flacOffset + FLAC_MAGIC.length; // skip fLaC marker
        boolean streamInfoFound = false;
        boolean lastMetadataBlockFound = false;
        int sampleRate = 0;
        int channels = 0;
        int bitsPerSample = 0;
        long totalSamples = 0;
        int audioStartOffset = -1;

        while (offset + 4 <= bytes.length) {
            int header = bytes[offset] & 0xFF;
            boolean last = (header & 0x80) != 0;
            int blockType = header & 0x7F;
            if (blockType == 0x7F) {
                throw new CodecMediaException("Invalid FLAC metadata block type: 127 is reserved");
            }
            int length = ((bytes[offset + 1] & 0xFF) << 16)
                    | ((bytes[offset + 2] & 0xFF) << 8)
                    | (bytes[offset + 3] & 0xFF);
            offset += 4;

            if (offset + length > bytes.length) {
                throw new CodecMediaException("Invalid FLAC metadata block length");
            }

            if (blockType == 0) { // STREAMINFO
                if (length != 34) {
                    throw new CodecMediaException("Invalid FLAC STREAMINFO block length: expected 34, got " + length);
                }
                long packed = readUInt64BE(bytes, offset + 10);
                sampleRate = (int) ((packed >>> 44) & 0xFFFFF);
                channels = (int) (((packed >>> 41) & 0x7) + 1);
                bitsPerSample = (int) (((packed >>> 36) & 0x1F) + 1);
                totalSamples = packed & 0xFFFFFFFFFL;
                streamInfoFound = true;
            }

            offset += length;
            if (last) {
                lastMetadataBlockFound = true;
                audioStartOffset = offset;
                break;
            }
        }

        if (!lastMetadataBlockFound) {
            throw new CodecMediaException("Invalid FLAC metadata: missing last-metadata-block flag");
        }

        if (!streamInfoFound || sampleRate <= 0 || channels <= 0 || bitsPerSample <= 0) {
            throw new CodecMediaException("FLAC STREAMINFO is missing or invalid");
        }

        long durationMillis = totalSamples > 0 ? (totalSamples * 1000L) / sampleRate : 0;
        long encodedAudioBytes = (audioStartOffset >= 0 && audioStartOffset <= bytes.length)
                ? (bytes.length - (long) audioStartOffset)
                : 0;
        int avgBitrateKbps = durationMillis > 0
                ? (int) ((encodedAudioBytes * 8L) / durationMillis)
                : 0;
        long pcmBitsPerSecond = (long) sampleRate * channels * bitsPerSample;
        int pcmEquivalentKbps = (int) (pcmBitsPerSecond / 1000L);
        int bitrateKbps = avgBitrateKbps > 0 ? avgBitrateKbps : pcmEquivalentKbps;

        return new FlacProbeInfo("flac", sampleRate, channels, bitsPerSample, bitrateKbps, BitrateMode.VBR, durationMillis);
    }

    public static boolean isLikelyFlac(byte[] bytes) {
        return findFlacOffset(bytes) >= 0;
    }

    public static Map<String, String> readVorbisCommentMetadata(byte[] bytes) throws CodecMediaException {
        int flacOffset = findFlacOffset(bytes);
        if (flacOffset < 0) {
            throw new CodecMediaException("Not a FLAC file");
        }

        int offset = flacOffset + FLAC_MAGIC.length;
        while (offset + 4 <= bytes.length) {
            int header = bytes[offset] & 0xFF;
            boolean last = (header & 0x80) != 0;
            int blockType = header & 0x7F;
            if (blockType == 0x7F) {
                throw new CodecMediaException("Invalid FLAC metadata block type: 127 is reserved");
            }
            int length = ((bytes[offset + 1] & 0xFF) << 16)
                    | ((bytes[offset + 2] & 0xFF) << 8)
                    | (bytes[offset + 3] & 0xFF);
            offset += 4;

            if (offset + length > bytes.length) {
                throw new CodecMediaException("Invalid FLAC metadata block length");
            }

            if (blockType == 4) {
                return parseVorbisCommentBlock(bytes, offset, length);
            }

            offset += length;
            if (last) {
                break;
            }
        }
        return Map.of();
    }

    private static Map<String, String> parseVorbisCommentBlock(byte[] bytes, int offset, int length) throws CodecMediaException {
        int end = offset + length;
        int pos = offset;

        int vendorLen = readLeIntAt(bytes, pos, end, "vendor length");
        pos += 4;
        if (pos + vendorLen > end) {
            throw new CodecMediaException("Invalid FLAC Vorbis comment block: vendor field exceeds block length");
        }
        pos += vendorLen;

        int commentCount = readLeIntAt(bytes, pos, end, "comment count");
        pos += 4;

        Map<String, String> raw = new HashMap<>();
        Map<String, String> out = new LinkedHashMap<>();
        for (int i = 0; i < commentCount; i++) {
            int commentLen = readLeIntAt(bytes, pos, end, "comment length");
            pos += 4;
            if (pos + commentLen > end) {
                throw new CodecMediaException("Invalid FLAC Vorbis comment block: comment field exceeds block length");
            }

            String comment = new String(bytes, pos, commentLen, StandardCharsets.UTF_8);
            int eq = comment.indexOf('=');
            if (eq > 0) {
                String key = comment.substring(0, eq).trim().toUpperCase(Locale.ROOT);
                String value = comment.substring(eq + 1).trim();
                if (!value.isEmpty()) {
                    raw.putIfAbsent(key, value);
                }
            }
            pos += commentLen;
        }

        putIfPresent(out, "title", raw, "TITLE");
        putIfPresent(out, "artist", raw, "ARTIST");
        putIfPresent(out, "album", raw, "ALBUM");
        putIfPresent(out, "comment", raw, "COMMENT");
        putIfPresent(out, "genre", raw, "GENRE");
        putIfPresent(out, "date", raw, "DATE", "YEAR");
        return out;
    }

    private static int findFlacOffset(byte[] bytes) {
        if (bytes == null || bytes.length < FLAC_MAGIC.length) {
            return -1;
        }
        if (matchesFlacMagicAt(bytes, 0)) {
            return 0;
        }

        int id3Offset = skipLeadingId3v2(bytes);
        if (id3Offset > 0 && matchesFlacMagicAt(bytes, id3Offset)) {
            return id3Offset;
        }

        int max = Math.min(bytes.length - FLAC_MAGIC.length, FLAC_MAGIC_SCAN_LIMIT);
        for (int i = 0; i <= max; i++) {
            if (matchesFlacMagicAt(bytes, i)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean matchesFlacMagicAt(byte[] bytes, int offset) {
        if (offset < 0 || offset + FLAC_MAGIC.length > bytes.length) {
            return false;
        }
        for (int i = 0; i < FLAC_MAGIC.length; i++) {
            if (bytes[offset + i] != FLAC_MAGIC[i]) {
                return false;
            }
        }
        return true;
    }

    private static int skipLeadingId3v2(byte[] bytes) {
        if (bytes.length < ID3V2_HEADER_SIZE) {
            return -1;
        }
        if (bytes[0] != 'I' || bytes[1] != 'D' || bytes[2] != '3') {
            return -1;
        }

        int flags = bytes[5] & 0xFF;
        int size = synchsafeToInt(bytes[6] & 0x7F, bytes[7] & 0x7F, bytes[8] & 0x7F, bytes[9] & 0x7F);
        int footer = ((flags & 0x10) != 0) ? ID3V2_HEADER_SIZE : 0;
        long total = (long) ID3V2_HEADER_SIZE + size + footer;
        if (total > Integer.MAX_VALUE || total > bytes.length) {
            return -1;
        }
        return (int) total;
    }

    private static int synchsafeToInt(int b0, int b1, int b2, int b3) {
        return ((b0 & 0x7F) << 21)
                | ((b1 & 0x7F) << 14)
                | ((b2 & 0x7F) << 7)
                | (b3 & 0x7F);
    }

    private static long readUInt64BE(byte[] bytes, int offset) throws CodecMediaException {
        if (offset + 8 > bytes.length) {
            throw new CodecMediaException("Unexpected end of FLAC data");
        }
        return ((long) (bytes[offset] & 0xFF) << 56)
                | ((long) (bytes[offset + 1] & 0xFF) << 48)
                | ((long) (bytes[offset + 2] & 0xFF) << 40)
                | ((long) (bytes[offset + 3] & 0xFF) << 32)
                | ((long) (bytes[offset + 4] & 0xFF) << 24)
                | ((long) (bytes[offset + 5] & 0xFF) << 16)
                | ((long) (bytes[offset + 6] & 0xFF) << 8)
                | ((long) (bytes[offset + 7] & 0xFF));
    }

    private static int readLeIntAt(byte[] bytes, int offset, int endExclusive, String fieldName) throws CodecMediaException {
        if (offset < 0 || offset + 4 > endExclusive || offset + 4 > bytes.length) {
            throw new CodecMediaException("Invalid FLAC Vorbis comment block: truncated " + fieldName);
        }
        long value = (bytes[offset] & 0xFFL)
                | ((bytes[offset + 1] & 0xFFL) << 8)
                | ((bytes[offset + 2] & 0xFFL) << 16)
                | ((bytes[offset + 3] & 0xFFL) << 24);
        if (value > Integer.MAX_VALUE) {
            throw new CodecMediaException("Invalid FLAC Vorbis comment block: " + fieldName + " is too large");
        }
        return (int) value;
    }

    private static void putIfPresent(Map<String, String> target, String targetKey, Map<String, String> source, String... sourceKeys) {
        for (String sourceKey : sourceKeys) {
            String value = source.get(sourceKey);
            if (value != null && !value.isBlank()) {
                target.putIfAbsent(targetKey, value);
                return;
            }
        }
    }
}

