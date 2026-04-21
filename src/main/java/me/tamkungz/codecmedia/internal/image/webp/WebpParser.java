package me.tamkungz.codecmedia.internal.image.webp;

import me.tamkungz.codecmedia.CodecMediaException;

public final class WebpParser {

    // Probe-level default: WebP variants are reported as 8-bit unless deeper bit-depth metadata is parsed.
    private static final int ASSUMED_WEBP_BIT_DEPTH = 8;
    private static final int RIFF_HEADER_SIZE = 12;
    private static final int CHUNK_HEADER_SIZE = 8;
    private static final int FIRST_CHUNK_OFFSET = RIFF_HEADER_SIZE;
    private static final int FIRST_CHUNK_DATA_OFFSET = FIRST_CHUNK_OFFSET + CHUNK_HEADER_SIZE;

    private WebpParser() {
    }

    public static boolean isLikelyWebp(byte[] bytes) {
        if (bytes == null || bytes.length < RIFF_HEADER_SIZE) {
            return false;
        }
        boolean magic = bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
        if (!magic) {
            return false;
        }

        long riffDeclaredSize = readLeUInt32Unchecked(bytes, 4);
        long riffTotalBytes = riffDeclaredSize + 8L;
        return riffTotalBytes <= bytes.length;
    }

    public static WebpProbeInfo parse(byte[] bytes) throws CodecMediaException {
        if (!isLikelyWebp(bytes)) {
            throw new CodecMediaException("Not a WebP file");
        }
        if (bytes.length < FIRST_CHUNK_DATA_OFFSET) {
            throw new CodecMediaException("WebP is too small");
        }

        String chunkType = fourcc(bytes, FIRST_CHUNK_OFFSET);
        return switch (chunkType) {
            case "VP8 " -> parseVp8(bytes);
            case "VP8L" -> parseVp8L(bytes);
            case "VP8X" -> parseVp8X(bytes);
            default -> throw new CodecMediaException("Unsupported WebP chunk type: " + chunkType);
        };
    }

    private static WebpProbeInfo parseVp8X(byte[] bytes) throws CodecMediaException {
        ensureFirstChunkPayload(bytes, 10, "VP8X");
        int widthMinus1 = (bytes[24] & 0xFF) | ((bytes[25] & 0xFF) << 8) | ((bytes[26] & 0xFF) << 16);
        int heightMinus1 = (bytes[27] & 0xFF) | ((bytes[28] & 0xFF) << 8) | ((bytes[29] & 0xFF) << 16);
        return ensurePositive(widthMinus1 + 1, heightMinus1 + 1, ASSUMED_WEBP_BIT_DEPTH, "VP8X");
    }

    private static WebpProbeInfo parseVp8L(byte[] bytes) throws CodecMediaException {
        ensureFirstChunkPayload(bytes, 5, "VP8L");
        if ((bytes[20] & 0xFF) != 0x2F) {
            throw new CodecMediaException("Invalid WebP VP8L signature byte");
        }
        int b1 = bytes[21] & 0xFF;
        int b2 = bytes[22] & 0xFF;
        int b3 = bytes[23] & 0xFF;
        int b4 = bytes[24] & 0xFF;
        int widthMinus1 = b1 | ((b2 & 0x3F) << 8);
        int heightMinus1 = ((b2 >> 6) & 0x03) | (b3 << 2) | ((b4 & 0x0F) << 10);
        return ensurePositive(widthMinus1 + 1, heightMinus1 + 1, ASSUMED_WEBP_BIT_DEPTH, "VP8L");
    }

    private static WebpProbeInfo parseVp8(byte[] bytes) throws CodecMediaException {
        ensureFirstChunkPayload(bytes, 10, "VP8");

        int frameTagByte0 = bytes[FIRST_CHUNK_DATA_OFFSET] & 0xFF;
        if ((frameTagByte0 & 0x01) != 0) {
            throw new CodecMediaException("Invalid WebP VP8 frame type: expected key frame");
        }
        if ((bytes[23] & 0xFF) != 0x9D || (bytes[24] & 0xFF) != 0x01 || (bytes[25] & 0xFF) != 0x2A) {
            throw new CodecMediaException("Invalid WebP VP8 frame start code");
        }

        int width = ((bytes[27] & 0x3F) << 8) | (bytes[26] & 0xFF);
        int height = ((bytes[29] & 0x3F) << 8) | (bytes[28] & 0xFF);
        return ensurePositive(width, height, ASSUMED_WEBP_BIT_DEPTH, "VP8");
    }

    private static void ensureFirstChunkPayload(byte[] bytes, int requiredPayloadBytes, String variant) throws CodecMediaException {
        long payloadLength = readLeUInt32(bytes, 16);
        long payloadStart = FIRST_CHUNK_DATA_OFFSET;
        long payloadEnd = payloadStart + payloadLength;

        if (payloadLength < requiredPayloadBytes) {
            throw new CodecMediaException("Invalid WebP " + variant + " chunk length");
        }
        if (payloadEnd > bytes.length || payloadStart + requiredPayloadBytes > bytes.length) {
            throw new CodecMediaException("Invalid WebP " + variant + " chunk bounds");
        }
    }

    private static String fourcc(byte[] bytes, int offset) throws CodecMediaException {
        if (offset + 4 > bytes.length) {
            throw new CodecMediaException("Unexpected end of WebP data");
        }
        return new String(bytes, offset, 4, java.nio.charset.StandardCharsets.US_ASCII);
    }

    private static long readLeUInt32(byte[] bytes, int offset) throws CodecMediaException {
        if (offset < 0 || offset + 4 > bytes.length) {
            throw new CodecMediaException("Unexpected end of WebP data");
        }
        return readLeUInt32Unchecked(bytes, offset);
    }

    private static long readLeUInt32Unchecked(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFFL)
                | ((bytes[offset + 1] & 0xFFL) << 8)
                | ((bytes[offset + 2] & 0xFFL) << 16)
                | ((bytes[offset + 3] & 0xFFL) << 24);
    }

    private static WebpProbeInfo ensurePositive(int width, int height, Integer bitDepth, String variant) throws CodecMediaException {
        if (width <= 0 || height <= 0) {
            throw new CodecMediaException("WebP " + variant + " has invalid dimensions");
        }
        return new WebpProbeInfo(width, height, bitDepth);
    }
}
