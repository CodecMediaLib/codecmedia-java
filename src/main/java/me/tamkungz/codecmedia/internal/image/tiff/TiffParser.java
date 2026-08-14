package me.tamkungz.codecmedia.internal.image.tiff;

import me.tamkungz.codecmedia.CodecMediaException;

public final class TiffParser {

    private TiffParser() {
    }

    public static boolean isLikelyTiff(byte[] bytes) {
        return bytes != null
                && bytes.length >= 8
                && ((bytes[0] == 'I' && bytes[1] == 'I' && bytes[2] == 42 && bytes[3] == 0)
                || (bytes[0] == 'M' && bytes[1] == 'M' && bytes[2] == 0 && bytes[3] == 42));
    }

    public static TiffProbeInfo parse(byte[] bytes) throws CodecMediaException {
        if (!isLikelyTiff(bytes)) {
            throw new CodecMediaException("Not a TIFF file");
        }

        boolean littleEndian = bytes[0] == 'I';
        int ifdOffset = readU32(bytes, 4, littleEndian);
        if (ifdOffset < 8 || ifdOffset + 2 > bytes.length) {
            throw new CodecMediaException("Invalid TIFF IFD offset");
        }

        int entryCount = readU16(bytes, ifdOffset, littleEndian);
        int pos = ifdOffset + 2;
        int maxEntries = (bytes.length - pos) / 12;
        if (entryCount > maxEntries) {
            throw new CodecMediaException("TIFF IFD entry count exceeds available data");
        }

        Integer width = null;
        Integer height = null;
        Integer bitDepth = null;
        for (int i = 0; i < entryCount; i++) {
            if (pos + 12 > bytes.length) {
                throw new CodecMediaException("Invalid TIFF IFD entry bounds");
            }
            int tag = readU16(bytes, pos, littleEndian);
            int type = readU16(bytes, pos + 2, littleEndian);
            int count = readU32(bytes, pos + 4, littleEndian);
            int valueOrOffset = readU32(bytes, pos + 8, littleEndian);

            if ((tag == 256 || tag == 257) && count >= 1) {
                Integer v = readTagFirstShortOrLongValue(bytes, type, count, valueOrOffset, littleEndian);
                if (v != null && v > 0) {
                    if (tag == 256) {
                        width = v;
                    } else {
                        height = v;
                    }
                }
            } else if (tag == 258 && count >= 1) {
                // For multi-component samples (for example RGB 8/8/8), probe currently
                // reports the first BitsPerSample component only.
                Integer v = readTagFirstShortOrLongValue(bytes, type, count, valueOrOffset, littleEndian);
                if (v != null && v > 0) {
                    bitDepth = v;
                }
            }
            pos += 12;
        }

        if (width == null || height == null || width <= 0 || height <= 0) {
            throw new CodecMediaException("TIFF missing width/height tags");
        }
        return new TiffProbeInfo(width, height, bitDepth);
    }

    private static Integer readTagFirstShortOrLongValue(
            byte[] bytes,
            int type,
            int count,
            int valueOrOffset,
            boolean littleEndian
    )
            throws CodecMediaException {
        if (type == 3) {
            if (count == 1) {
                return littleEndian ? (valueOrOffset & 0xFFFF) : ((valueOrOffset >>> 16) & 0xFFFF);
            }
            return readU16(bytes, valueOrOffset, littleEndian);
        }
        if (type == 4) {
            if (count == 1) {
                return valueOrOffset;
            }
            return readU32(bytes, valueOrOffset, littleEndian);
        }
        return null;
    }

    private static int readU16(byte[] bytes, int offset, boolean littleEndian) throws CodecMediaException {
        if (offset + 2 > bytes.length) {
            throw new CodecMediaException("Unexpected end of TIFF data");
        }
        if (littleEndian) {
            return (bytes[offset] & 0xFF) | ((bytes[offset + 1] & 0xFF) << 8);
        }
        return ((bytes[offset] & 0xFF) << 8) | (bytes[offset + 1] & 0xFF);
    }

    private static int readU32(byte[] bytes, int offset, boolean littleEndian) throws CodecMediaException {
        if (offset + 4 > bytes.length) {
            throw new CodecMediaException("Unexpected end of TIFF data");
        }
        if (littleEndian) {
            return (bytes[offset] & 0xFF)
                    | ((bytes[offset + 1] & 0xFF) << 8)
                    | ((bytes[offset + 2] & 0xFF) << 16)
                    | ((bytes[offset + 3] & 0xFF) << 24);
        }
        return ((bytes[offset] & 0xFF) << 24)
                | ((bytes[offset + 1] & 0xFF) << 16)
                | ((bytes[offset + 2] & 0xFF) << 8)
                | (bytes[offset + 3] & 0xFF);
    }
}

