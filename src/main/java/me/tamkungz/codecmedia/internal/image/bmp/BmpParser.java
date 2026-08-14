package me.tamkungz.codecmedia.internal.image.bmp;

import me.tamkungz.codecmedia.CodecMediaException;

public final class BmpParser {

    private BmpParser() {
    }

    public static boolean isLikelyBmp(byte[] bytes) {
        return bytes != null
                && bytes.length >= 26
                && bytes[0] == 'B'
                && bytes[1] == 'M';
    }

    public static BmpProbeInfo parse(byte[] bytes) throws CodecMediaException {
        if (!isLikelyBmp(bytes)) {
            throw new CodecMediaException("Not a BMP file");
        }

        int dibHeaderSize = readU32LE(bytes, 14);
        if (dibHeaderSize < 12) {
            throw new CodecMediaException("Unsupported BMP DIB header size: " + dibHeaderSize);
        }
        if (14L + dibHeaderSize > bytes.length) {
            throw new CodecMediaException("BMP DIB header truncated");
        }

        int width;
        int height;
        int bitsPerPixel;
        if (dibHeaderSize == 12) {
            width = readU16LE(bytes, 18);
            height = readU16LE(bytes, 20);
            bitsPerPixel = readU16LE(bytes, 24);
        } else {
            width = readI32LE(bytes, 18);
            height = Math.abs(readI32LE(bytes, 22));
            bitsPerPixel = readU16LE(bytes, 28);
        }

        if (width <= 0 || height <= 0) {
            throw new CodecMediaException("BMP has invalid dimensions");
        }
        if (!isValidBitsPerPixel(bitsPerPixel)) {
            throw new CodecMediaException("BMP has invalid bits-per-pixel: " + bitsPerPixel);
        }

        return new BmpProbeInfo(width, height, bitsPerPixel);
    }

    private static boolean isValidBitsPerPixel(int bitsPerPixel) {
        return bitsPerPixel == 1
                || bitsPerPixel == 2
                || bitsPerPixel == 4
                || bitsPerPixel == 8
                || bitsPerPixel == 16
                || bitsPerPixel == 24
                || bitsPerPixel == 32;
    }

    private static int readU16LE(byte[] bytes, int offset) throws CodecMediaException {
        if (offset + 2 > bytes.length) {
            throw new CodecMediaException("Unexpected end of BMP data");
        }
        return (bytes[offset] & 0xFF) | ((bytes[offset + 1] & 0xFF) << 8);
    }

    private static int readU32LE(byte[] bytes, int offset) throws CodecMediaException {
        if (offset + 4 > bytes.length) {
            throw new CodecMediaException("Unexpected end of BMP data");
        }
        return (bytes[offset] & 0xFF)
                | ((bytes[offset + 1] & 0xFF) << 8)
                | ((bytes[offset + 2] & 0xFF) << 16)
                | ((bytes[offset + 3] & 0xFF) << 24);
    }

    private static int readI32LE(byte[] bytes, int offset) throws CodecMediaException {
        // Signed 32-bit read is represented directly as Java int.
        return readU32LE(bytes, offset);
    }
}

