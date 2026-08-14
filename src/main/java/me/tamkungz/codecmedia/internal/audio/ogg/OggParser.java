package me.tamkungz.codecmedia.internal.audio.ogg;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import me.tamkungz.codecmedia.CodecMediaException;
import me.tamkungz.codecmedia.internal.audio.BitrateMode;
import me.tamkungz.codecmedia.internal.io.ByteArrayReader;

public final class OggParser {

    private static final int OPUS_GRANULE_RATE = 48_000;

    private OggParser() {
    }

    public static OggProbeInfo parse(byte[] data) throws CodecMediaException {
        if (data == null || data.length < 27) {
            throw new CodecMediaException("Invalid OGG data: too small");
        }

        OggPageHeader firstPage = parsePageHeader(data, 0);
        if (firstPage == null) {
            throw new CodecMediaException("Invalid OGG stream: missing OggS header");
        }

        int identOffset = firstPage.headerSize();
        int firstPayloadSize = firstPage.payloadSize();
        if (identOffset + firstPayloadSize > data.length || firstPayloadSize <= 0) {
            throw new CodecMediaException("Invalid OGG stream: incomplete first packet payload");
        }

        AudioIdent ident = parseIdentificationPacket(data, identOffset, firstPayloadSize);
        long targetSerial = firstPage.serialNumber();

        long payloadBits = 0;
        long maxGranule = 0;
        long prevGranule = -1;
        long prevSequence = -1;
        Set<Integer> observedKbps = new HashSet<>();
        boolean hasCommentMetadata = false;
        int offset = 0;
        while (offset + 27 <= data.length) {
            OggPageHeader page = parsePageHeader(data, offset);
            if (page == null) {
                break;
            }

            if (page.serialNumber() == targetSerial) {
                if (prevSequence >= 0 && page.sequenceNumber() != (long) prevSequence + 1L) {
                    throw new CodecMediaException("Invalid OGG stream: broken page sequence for target stream");
                }
                prevSequence = page.sequenceNumber();

                payloadBits += (long) page.payloadSize() * 8;
                if (page.granulePosition() > maxGranule) {
                    maxGranule = page.granulePosition();
                }

                int payloadOffset = offset + page.headerSize();
                if (!hasCommentMetadata && payloadOffset + page.payloadSize() <= data.length
                        && containsCodecCommentSignal(data, payloadOffset, page.payloadSize(), ident.codec())) {
                    hasCommentMetadata = true;
                }

                if (prevGranule >= 0 && page.granulePosition() > prevGranule) {
                    long granuleDelta = page.granulePosition() - prevGranule;
                    int granuleRate = ident.granuleRate() > 0 ? ident.granuleRate() : ident.sampleRate();
                    if (granuleRate > 0) {
                        long millis = (granuleDelta * 1000L) / granuleRate;
                        if (millis > 0) {
                            int kbps = (int) (((long) page.payloadSize() * 8L * 1000L) / millis / 1000L);
                            if (kbps > 0) {
                                observedKbps.add(kbps);
                            }
                        }
                    }
                }
                prevGranule = page.granulePosition();
            }

            offset += page.totalPageSize();
        }

        int granuleRate = ident.granuleRate() > 0 ? ident.granuleRate() : ident.sampleRate();
        long durationMillis = (granuleRate > 0 && maxGranule > 0)
                ? (maxGranule * 1000L) / granuleRate
                : 0;

        int avgBitrate = durationMillis > 0
                ? (int) ((payloadBits * 1000L) / durationMillis / 1000L)
                : 0;

        int nominalKbps = ident.nominalBitrate() > 0 ? (int) (ident.nominalBitrate() / 1000L) : 0;
        int bitrateKbps = avgBitrate > 0 ? avgBitrate : nominalKbps;

        BitrateMode mode = switch (ident.codec()) {
            case "vorbis" -> detectVorbisBitrateMode(observedKbps, ident.nominalBitrate(), hasCommentMetadata);
            case "opus" -> BitrateMode.VBR;
            default -> BitrateMode.UNKNOWN;
        };

        return new OggProbeInfo(ident.codec(), ident.sampleRate(), ident.channels(), bitrateKbps, mode, durationMillis);
    }

    public static Map<String, String> readCommentMetadata(byte[] data) throws CodecMediaException {
        if (data == null || data.length < 27) {
            throw new CodecMediaException("Invalid OGG data: too small");
        }

        OggPageHeader firstPage = parsePageHeader(data, 0);
        if (firstPage == null) {
            throw new CodecMediaException("Invalid OGG stream: missing OggS header");
        }

        int identOffset = firstPage.headerSize();
        int firstPayloadSize = firstPage.payloadSize();
        if (identOffset + firstPayloadSize > data.length || firstPayloadSize <= 0) {
            throw new CodecMediaException("Invalid OGG stream: incomplete first packet payload");
        }
        AudioIdent ident = parseIdentificationPacket(data, identOffset, firstPayloadSize);

        int offset = 0;
        while (offset + 27 <= data.length) {
            OggPageHeader page = parsePageHeader(data, offset);
            if (page == null) {
                break;
            }
            int payloadOffset = offset + page.headerSize();
            int payloadSize = page.payloadSize();
            if (payloadOffset + payloadSize > data.length) {
                break;
            }

            Map<String, String> comments = parseCodecComments(data, payloadOffset, payloadSize, ident.codec());
            if (!comments.isEmpty()) {
                return comments;
            }
            offset += page.totalPageSize();
        }
        return Map.of();
    }

    private static BitrateMode detectVorbisBitrateMode(
            Set<Integer> observedKbps,
            long nominalBitrate,
            boolean hasCommentMetadata
    ) {
        if (observedKbps.size() > 1) {
            return BitrateMode.VBR;
        }
        if (observedKbps.size() == 1) {
            return BitrateMode.CBR;
        }
        if (nominalBitrate > 0 || hasCommentMetadata) {
            return BitrateMode.UNKNOWN;
        }
        return BitrateMode.UNKNOWN;
    }

    private static AudioIdent parseIdentificationPacket(byte[] data, int identOffset, int payloadSize) throws CodecMediaException {
        if (isVorbisIdentification(data, identOffset, payloadSize)) {
            if (payloadSize < 30) {
                throw new CodecMediaException("Invalid OGG Vorbis stream: incomplete identification packet");
            }
            ByteArrayReader ident = new ByteArrayReader(data);
            ident.position(identOffset + 7);
            ident.readU32LE(); // vorbis version
            int channels = ident.readU8();
            int sampleRate = (int) ident.readU32LE();
            long bitrateNominal = ident.readU32LE();
            return new AudioIdent("vorbis", sampleRate, channels, bitrateNominal, sampleRate);
        }

        if (isOpusIdentification(data, identOffset, payloadSize)) {
            if (payloadSize < 19) {
                throw new CodecMediaException("Invalid OGG Opus stream: incomplete OpusHead packet");
            }
            ByteArrayReader ident = new ByteArrayReader(data);
            ident.position(identOffset + 9); // OpusHead + version
            int channels = ident.readU8();
            ident.readU16LE(); // pre-skip
            int inputSampleRate = (int) ident.readU32LE();
            int sampleRate = inputSampleRate > 0 ? inputSampleRate : OPUS_GRANULE_RATE;
            return new AudioIdent("opus", sampleRate, channels, 0, OPUS_GRANULE_RATE);
        }

        throw new CodecMediaException("Unsupported OGG codec: currently Vorbis and Opus are parsed");
    }

    private static boolean isVorbisIdentification(byte[] data, int offset, int payloadSize) {
        return payloadSize >= 7
                && data[offset] == 0x01
                && data[offset + 1] == 'v'
                && data[offset + 2] == 'o'
                && data[offset + 3] == 'r'
                && data[offset + 4] == 'b'
                && data[offset + 5] == 'i'
                && data[offset + 6] == 's';
    }

    private static boolean isOpusIdentification(byte[] data, int offset, int payloadSize) {
        return payloadSize >= 8
                && data[offset] == 'O'
                && data[offset + 1] == 'p'
                && data[offset + 2] == 'u'
                && data[offset + 3] == 's'
                && data[offset + 4] == 'H'
                && data[offset + 5] == 'e'
                && data[offset + 6] == 'a'
                && data[offset + 7] == 'd';
    }

    private static OggPageHeader parsePageHeader(byte[] data, int offset) {
        if (offset < 0 || offset + 27 > data.length) {
            return null;
        }
        if (data[offset] != 'O' || data[offset + 1] != 'g' || data[offset + 2] != 'g' || data[offset + 3] != 'S') {
            return null;
        }

        ByteArrayReader r = new ByteArrayReader(data);
        r.position(offset + 4);
        int version = r.readU8();
        int headerType = r.readU8();
        long granulePosition = r.readU64LE();
        long serial = r.readU32LE();
        long sequence = r.readU32LE();
        r.readU32LE(); // checksum
        int segmentCount = r.readU8();

        if (offset + 27 + segmentCount > data.length) {
            return null;
        }

        int payload = 0;
        for (int i = 0; i < segmentCount; i++) {
            payload += data[offset + 27 + i] & 0xFF;
        }

        int headerSize = 27 + segmentCount;
        int total = headerSize + payload;
        if (offset + total > data.length) {
            return null;
        }

        return new OggPageHeader(version, headerType, granulePosition, serial, sequence, segmentCount, payload, total, headerSize);
    }

    private static boolean containsCodecCommentSignal(byte[] data, int offset, int payloadSize, String codec) {
        if (offset < 0 || payloadSize <= 0 || offset + payloadSize > data.length) {
            return false;
        }
        if ("vorbis".equals(codec)) {
            return parseVorbisCommentSignal(data, offset, payloadSize);
        }
        if ("opus".equals(codec)) {
            return parseOpusCommentSignal(data, offset, payloadSize);
        }
        return false;
    }

    private static Map<String, String> parseCodecComments(byte[] data, int offset, int payloadSize, String codec) {
        if (offset < 0 || payloadSize <= 0 || offset + payloadSize > data.length) {
            return Map.of();
        }
        return switch (codec) {
            case "vorbis" -> parseVorbisComments(data, offset, payloadSize);
            case "opus" -> parseOpusComments(data, offset, payloadSize);
            default -> Map.of();
        };
    }

    private static Map<String, String> parseVorbisComments(byte[] data, int offset, int payloadSize) {
        if (payloadSize < 11) {
            return Map.of();
        }
        if (data[offset] != 0x03
                || data[offset + 1] != 'v'
                || data[offset + 2] != 'o'
                || data[offset + 3] != 'r'
                || data[offset + 4] != 'b'
                || data[offset + 5] != 'i'
                || data[offset + 6] != 's') {
            return Map.of();
        }
        return parseCommentMap(data, offset + 7, offset + payloadSize, true);
    }

    private static Map<String, String> parseOpusComments(byte[] data, int offset, int payloadSize) {
        if (payloadSize < 12) {
            return Map.of();
        }
        if (data[offset] != 'O'
                || data[offset + 1] != 'p'
                || data[offset + 2] != 'u'
                || data[offset + 3] != 's'
                || data[offset + 4] != 'T'
                || data[offset + 5] != 'a'
                || data[offset + 6] != 'g'
                || data[offset + 7] != 's') {
            return Map.of();
        }
        return parseCommentMap(data, offset + 8, offset + payloadSize, false);
    }

    private static Map<String, String> parseCommentMap(byte[] data, int pos, int end, boolean vorbis) {
        int vendorLen = readU32LEAt(data, pos, end);
        if (vendorLen < 0) {
            return Map.of();
        }
        pos += 4;
        if (pos + vendorLen > end) {
            return Map.of();
        }
        pos += vendorLen;

        int commentCount = readU32LEAt(data, pos, end);
        if (commentCount < 0) {
            return Map.of();
        }
        pos += 4;

        Map<String, String> canonical = new LinkedHashMap<>();
        Map<String, String> raw = new HashMap<>();
        for (int i = 0; i < commentCount; i++) {
            int commentLen = readU32LEAt(data, pos, end);
            if (commentLen < 0) {
                return Map.of();
            }
            pos += 4;
            if (pos + commentLen > end) {
                return Map.of();
            }

            String text = new String(data, pos, commentLen, StandardCharsets.UTF_8);
            int eq = text.indexOf('=');
            if (eq > 0) {
                String key = text.substring(0, eq).trim().toUpperCase(Locale.ROOT);
                String value = text.substring(eq + 1).trim();
                if (!value.isEmpty()) {
                    raw.putIfAbsent(key, value);
                }
            }
            pos += commentLen;
        }

        putIfPresent(canonical, "title", raw, "TITLE");
        putIfPresent(canonical, "artist", raw, "ARTIST");
        putIfPresent(canonical, "album", raw, "ALBUM");
        putIfPresent(canonical, "comment", raw, "COMMENT");
        putIfPresent(canonical, "genre", raw, "GENRE");
        putIfPresent(canonical, "date", raw, "DATE", "YEAR");
        if (!vorbis) {
            putIfPresent(canonical, "encoder", raw, "ENCODER", "ENCODER_OPTIONS");
        }
        return canonical;
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

    private static boolean parseVorbisCommentSignal(byte[] data, int offset, int payloadSize) {
        if (payloadSize < 11) {
            return false;
        }
        if (data[offset] != 0x03
                || data[offset + 1] != 'v'
                || data[offset + 2] != 'o'
                || data[offset + 3] != 'r'
                || data[offset + 4] != 'b'
                || data[offset + 5] != 'i'
                || data[offset + 6] != 's') {
            return false;
        }
        return parseCommentListForSignals(data, offset + 7, offset + payloadSize, true);
    }

    private static boolean parseOpusCommentSignal(byte[] data, int offset, int payloadSize) {
        if (payloadSize < 12) {
            return false;
        }
        if (data[offset] != 'O'
                || data[offset + 1] != 'p'
                || data[offset + 2] != 'u'
                || data[offset + 3] != 's'
                || data[offset + 4] != 'T'
                || data[offset + 5] != 'a'
                || data[offset + 6] != 'g'
                || data[offset + 7] != 's') {
            return false;
        }
        return parseCommentListForSignals(data, offset + 8, offset + payloadSize, false);
    }

    private static boolean parseCommentListForSignals(byte[] data, int pos, int end, boolean vorbis) {
        int vendorLen = readU32LEAt(data, pos, end);
        if (vendorLen < 0) {
            return false;
        }
        pos += 4;
        if (pos + vendorLen > end) {
            return false;
        }
        pos += vendorLen;

        int commentCount = readU32LEAt(data, pos, end);
        if (commentCount < 0) {
            return false;
        }
        pos += 4;

        for (int i = 0; i < commentCount; i++) {
            int commentLen = readU32LEAt(data, pos, end);
            if (commentLen < 0) {
                return false;
            }
            pos += 4;
            if (pos + commentLen > end) {
                return false;
            }
            int eq = -1;
            for (int j = 0; j < commentLen; j++) {
                if (data[pos + j] == '=') {
                    eq = j;
                    break;
                }
            }
            if (eq > 0 && hasSignalCommentKey(data, pos, eq, vorbis)) {
                return true;
            }
            pos += commentLen;
        }
        return false;
    }

    private static int readU32LEAt(byte[] data, int offset, int endExclusive) {
        if (offset < 0 || offset + 4 > endExclusive || offset + 4 > data.length) {
            return -1;
        }
        long value = (data[offset] & 0xFFL)
                | ((data[offset + 1] & 0xFFL) << 8)
                | ((data[offset + 2] & 0xFFL) << 16)
                | ((data[offset + 3] & 0xFFL) << 24);
        if (value > Integer.MAX_VALUE) {
            return -1;
        }
        return (int) value;
    }

    private static boolean hasSignalCommentKey(byte[] data, int keyOffset, int keyLen, boolean vorbis) {
        if (startsWithAsciiIgnoreCase(data, keyOffset, keyLen, "REPLAYGAIN_")) {
            return true;
        }
        if (startsWithAsciiIgnoreCase(data, keyOffset, keyLen, "TRACKTOTAL")) {
            return true;
        }
        if (startsWithAsciiIgnoreCase(data, keyOffset, keyLen, "ALBUMGAIN")) {
            return true;
        }
        return !vorbis
                && (startsWithAsciiIgnoreCase(data, keyOffset, keyLen, "R128_TRACK_GAIN")
                || startsWithAsciiIgnoreCase(data, keyOffset, keyLen, "R128_ALBUM_GAIN"));
    }

    private static boolean startsWithAsciiIgnoreCase(byte[] data, int offset, int len, String prefix) {
        if (prefix == null || prefix.isEmpty() || len < prefix.length()) {
            return false;
        }
        for (int i = 0; i < prefix.length(); i++) {
            int b = data[offset + i] & 0xFF;
            char c = (char) (b >= 'a' && b <= 'z' ? (b - 32) : b);
            char p = Character.toUpperCase(prefix.charAt(i));
            if (c != p) {
                return false;
            }
        }
        return true;
    }

    private record AudioIdent(
            String codec,
            int sampleRate,
            int channels,
            long nominalBitrate,
            int granuleRate
    ) {
    }
}

