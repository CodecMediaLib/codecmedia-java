package me.tamkungz.codecmedia.internal.image.png;

import me.tamkungz.codecmedia.CodecMediaException;

public final class PngParser {

    private static final byte[] PNG_SIGNATURE = new byte[] {
            (byte) 0x89, 0x50, 0x4E, 0x47,
            0x0D, 0x0A, 0x1A, 0x0A
    };

    private PngParser() {
    }

    public static boolean isLikelyPng(byte[] bytes) {
        if (bytes == null) {
            return false;
        }
        if (bytes.length < PNG_SIGNATURE.length) {
            return false;
        }
        for (int i = 0; i < PNG_SIGNATURE.length; i++) {
            if (bytes[i] != PNG_SIGNATURE[i]) {
                return false;
            }
        }
        return true;
    }

    public static PngProbeInfo parse(byte[] bytes) throws CodecMediaException {
        if (!isLikelyPng(bytes)) {
            throw new CodecMediaException("Not a PNG file");
        }

        if (bytes.length < 33) {
            throw new CodecMediaException("PNG is too small");
        }

        int ihdrLength = readBeInt(bytes, 8);
        if (ihdrLength != 13) {
            throw new CodecMediaException("Invalid IHDR length: " + ihdrLength);
        }

        if (!(bytes[12] == 'I' && bytes[13] == 'H' && bytes[14] == 'D' && bytes[15] == 'R')) {
            throw new CodecMediaException("PNG missing IHDR chunk");
        }

        int width = readBeInt(bytes, 16);
        int height = readBeInt(bytes, 20);
        int bitDepth = bytes[24] & 0xFF;
        int colorType = bytes[25] & 0xFF;

        if (width <= 0 || height <= 0) {
            throw new CodecMediaException("PNG has invalid dimensions");
        }
        if (!isValidBitDepth(bitDepth)) {
            throw new CodecMediaException("PNG has invalid bit depth: " + bitDepth);
        }
        if (!isValidColorType(colorType)) {
            throw new CodecMediaException("PNG has invalid color type: " + colorType);
        }
        if (!isValidBitDepthColorTypeCombination(bitDepth, colorType)) {
            throw new CodecMediaException("PNG has invalid bit depth/color type combination: bitDepth="
                    + bitDepth + ", colorType=" + colorType);
        }

        return new PngProbeInfo(width, height, bitDepth, colorType);
    }

    private static boolean isValidBitDepth(int bitDepth) {
        return bitDepth == 1 || bitDepth == 2 || bitDepth == 4 || bitDepth == 8 || bitDepth == 16;
    }

    private static boolean isValidColorType(int colorType) {
        return colorType == 0 || colorType == 2 || colorType == 3 || colorType == 4 || colorType == 6;
    }

    private static boolean isValidBitDepthColorTypeCombination(int bitDepth, int colorType) {
        return switch (colorType) {
            case 0 -> bitDepth == 1 || bitDepth == 2 || bitDepth == 4 || bitDepth == 8 || bitDepth == 16;
            case 2 -> bitDepth == 8 || bitDepth == 16;
            case 3 -> bitDepth == 1 || bitDepth == 2 || bitDepth == 4 || bitDepth == 8;
            case 4 -> bitDepth == 8 || bitDepth == 16;
            case 6 -> bitDepth == 8 || bitDepth == 16;
            default -> false;
        };
    }

    private static int readBeInt(byte[] bytes, int offset) throws CodecMediaException {
        if (offset + 4 > bytes.length) {
            throw new CodecMediaException("Unexpected end of PNG data");
        }
        return ((bytes[offset] & 0xFF) << 24)
                | ((bytes[offset + 1] & 0xFF) << 16)
                | ((bytes[offset + 2] & 0xFF) << 8)
                | (bytes[offset + 3] & 0xFF);
    }
}

