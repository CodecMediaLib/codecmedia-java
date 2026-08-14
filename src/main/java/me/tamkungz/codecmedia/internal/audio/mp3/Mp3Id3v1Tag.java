package me.tamkungz.codecmedia.internal.audio.mp3;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Minimal ID3v1 reader/writer for embedded MP3 metadata support.
 */
public final class Mp3Id3v1Tag {

    private static final int TAG_SIZE = 128;

    private Mp3Id3v1Tag() {
    }

    public static Map<String, String> read(byte[] bytes) {
        Map<String, String> out = new LinkedHashMap<>();
        if (!hasId3v1(bytes)) {
            return out;
        }

        int start = bytes.length - TAG_SIZE;
        out.put("title", decodeField(bytes, start + 3, 30));
        out.put("artist", decodeField(bytes, start + 33, 30));
        out.put("album", decodeField(bytes, start + 63, 30));
        out.put("date", decodeField(bytes, start + 93, 4));
        out.put("comment", decodeField(bytes, start + 97, 30));
        int genreIndex = bytes[start + 127] & 0xFF;
        if (genreIndex != 255) {
            out.put("genre", String.valueOf(genreIndex));
        }

        out.entrySet().removeIf(e -> e.getValue() == null || e.getValue().isBlank());
        return out;
    }

    public static byte[] write(byte[] original, Map<String, String> entries) {
        Map<String, String> base = read(original);
        Map<String, String> merged = new LinkedHashMap<>(base);
        for (Map.Entry<String, String> e : entries.entrySet()) {
            String key = e.getKey() == null ? "" : e.getKey().trim().toLowerCase(Locale.ROOT);
            if (isSupportedKey(key) && e.getValue() != null) {
                merged.put(key, e.getValue());
            }
        }

        int audioEnd = hasId3v1(original) ? original.length - TAG_SIZE : original.length;
        byte[] output = new byte[audioEnd + TAG_SIZE];
        System.arraycopy(original, 0, output, 0, audioEnd);

        int t = audioEnd;
        output[t] = 'T';
        output[t + 1] = 'A';
        output[t + 2] = 'G';
        encodeField(output, t + 3, 30, merged.get("title"));
        encodeField(output, t + 33, 30, merged.get("artist"));
        encodeField(output, t + 63, 30, merged.get("album"));
        encodeField(output, t + 93, 4, normalizeYear(merged.get("date")));
        encodeField(output, t + 97, 30, merged.get("comment"));
        output[t + 127] = (byte) parseGenre(merged.get("genre"));
        return output;
    }

    private static boolean hasId3v1(byte[] bytes) {
        return bytes != null
                && bytes.length >= TAG_SIZE
                && bytes[bytes.length - TAG_SIZE] == 'T'
                && bytes[bytes.length - TAG_SIZE + 1] == 'A'
                && bytes[bytes.length - TAG_SIZE + 2] == 'G';
    }

    private static boolean isSupportedKey(String key) {
        return "title".equals(key)
                || "artist".equals(key)
                || "album".equals(key)
                || "date".equals(key)
                || "comment".equals(key)
                || "genre".equals(key);
    }

    private static String decodeField(byte[] bytes, int offset, int len) {
        String raw = new String(bytes, offset, len, StandardCharsets.ISO_8859_1);
        int end = raw.length();
        while (end > 0) {
            char c = raw.charAt(end - 1);
            if (c == '\u0000' || c == ' ') {
                end--;
            } else {
                break;
            }
        }
        return raw.substring(0, end);
    }

    private static void encodeField(byte[] out, int offset, int len, String value) {
        for (int i = 0; i < len; i++) {
            out[offset + i] = 0;
        }
        if (value == null) {
            return;
        }
        byte[] encoded = value.getBytes(StandardCharsets.ISO_8859_1);
        int copy = Math.min(len, encoded.length);
        System.arraycopy(encoded, 0, out, offset, copy);
    }

    private static String normalizeYear(String date) {
        if (date == null || date.isBlank()) {
            return "";
        }
        String trimmed = date.trim();
        if (trimmed.length() >= 4) {
            return trimmed.substring(0, 4);
        }
        return trimmed;
    }

    private static int parseGenre(String value) {
        if (value == null || value.isBlank()) {
            return 255;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed < 0 || parsed > 255) {
                return 255;
            }
            return parsed;
        } catch (NumberFormatException ignored) {
            return 255;
        }
    }
}

