package me.tamkungz.codecmedia.internal.image.heif;

import java.nio.charset.StandardCharsets;

import me.tamkungz.codecmedia.CodecMediaException;

public final class HeifParser {

    private static final int FULL_BOX_HEADER_SIZE = 4;
    private static final int MAX_BOX_SEARCH_DEPTH = 64;

    private HeifParser() {
    }

    public static boolean isLikelyHeif(byte[] bytes) {
        return bytes != null
                && bytes.length >= 12
                && readAscii(bytes, 4, 4).equals("ftyp")
                && isHeifBrand(readAscii(bytes, 8, 4));
    }

    public static HeifProbeInfo parse(byte[] bytes) throws CodecMediaException {
        if (!isLikelyHeif(bytes)) {
            throw new CodecMediaException("Not a HEIF/HEIC file");
        }
        String majorBrand = readAscii(bytes, 8, 4);
        BoxData ispe = findBoxData(bytes, "ispe");
        BoxData pixi = findBoxData(bytes, "pixi");
        Integer width = extractIspeWidth(ispe);
        Integer height = extractIspeHeight(ispe);
        Integer bitDepth = extractPixiBitDepth(pixi);
        return new HeifProbeInfo(majorBrand, width, height, bitDepth);
    }

    private static boolean isHeifBrand(String brand) {
        return "heic".equals(brand)
                || "heix".equals(brand)
                || "hevc".equals(brand)
                || "hevx".equals(brand)
                || "mif1".equals(brand)
                || "msf1".equals(brand)
                || "heif".equals(brand)
                || "avif".equals(brand)
                || "avis".equals(brand);
    }

    private static String readAscii(byte[] bytes, int offset, int length) {
        if (offset + length > bytes.length) {
            return "";
        }
        return new String(bytes, offset, length, StandardCharsets.US_ASCII);
    }

    private static Integer extractIspeWidth(BoxData ispe) throws CodecMediaException {
        if (ispe == null || ispe.payloadOffset() + 12 > ispe.boxEnd()) {
            return null;
        }
        // ispe is a FullBox: [version(1) + flags(3)] + width(4) + height(4)
        int width = readBeInt(ispe.bytes(), ispe.payloadOffset() + 4);
        return width > 0 ? width : null;
    }

    private static Integer extractIspeHeight(BoxData ispe) throws CodecMediaException {
        if (ispe == null || ispe.payloadOffset() + 12 > ispe.boxEnd()) {
            return null;
        }
        // ispe is a FullBox: [version(1) + flags(3)] + width(4) + height(4)
        int height = readBeInt(ispe.bytes(), ispe.payloadOffset() + 8);
        return height > 0 ? height : null;
    }

    private static Integer extractPixiBitDepth(BoxData pixi) {
        if (pixi == null) {
            return null;
        }
        byte[] bytes = pixi.bytes();
        int payloadOffset = pixi.payloadOffset();
        int version = bytes[payloadOffset] & 0xFF;
        if (version != 0) {
            return null;
        }
        int dataOffset = payloadOffset + FULL_BOX_HEADER_SIZE;
        if (dataOffset + 1 > pixi.boxEnd()) {
            return null;
        }
        int channelCount = bytes[dataOffset] & 0xFF;
        if (channelCount <= 0 || dataOffset + 1 + channelCount > pixi.boxEnd()) {
            return null;
        }
        int minDepth = Integer.MAX_VALUE;
        for (int i = 0; i < channelCount; i++) {
            int depth = bytes[dataOffset + 1 + i] & 0xFF;
            if (depth > 0 && depth < minDepth) {
                minDepth = depth;
            }
        }
        return minDepth == Integer.MAX_VALUE ? null : minDepth;
    }

    private static BoxData findBoxData(byte[] bytes, String boxType) {
        return findBoxData(bytes, 0, bytes.length, boxType, 0);
    }

    private static BoxData findBoxData(byte[] bytes, int startOffset, int endOffset, String boxType, int depth) {
        if (depth > MAX_BOX_SEARCH_DEPTH) {
            return null;
        }
        int offset = 0;
        if (startOffset > 0) {
            offset = startOffset;
        }
        while (offset + 8 <= endOffset) {
            int start = offset;
            long size = readU32AsLong(bytes, offset);
            String type = readAscii(bytes, offset + 4, 4);
            int headerSize = 8;

            if (size == 1L) {
                if (offset + 16 > endOffset) {
                    break;
                }
                size = readU64AsLong(bytes, offset + 8);
                headerSize = 16;
            } else if (size == 0L) {
                size = endOffset - offset;
            }

            if (size < headerSize) {
                break;
            }

            long endLong = offset + size;
            if (endLong > endOffset || endLong <= offset) {
                break;
            }
            int end = (int) endLong;

            if (boxType.equals(type)) {
                return new BoxData(bytes, start, offset + headerSize, end);
            }

            if (isContainerType(type)) {
                int childStart = offset + headerSize;
                if ("meta".equals(type) && childStart + FULL_BOX_HEADER_SIZE <= end) {
                    childStart += FULL_BOX_HEADER_SIZE;
                }
                BoxData nested = findBoxData(bytes, childStart, end, boxType, depth + 1);
                if (nested != null) {
                    return nested;
                }
            }

            offset = end;
        }
        return null;
    }

    private static boolean isContainerType(String type) {
        return "meta".equals(type)
                || "moov".equals(type)
                || "trak".equals(type)
                || "mdia".equals(type)
                || "minf".equals(type)
                || "stbl".equals(type)
                || "dinf".equals(type)
                || "edts".equals(type)
                || "udta".equals(type)
                || "iprp".equals(type)
                || "ipco".equals(type)
                || "iinf".equals(type)
                || "iloc".equals(type)
                || "iref".equals(type)
                || "grpl".equals(type)
                || "strk".equals(type)
                || "meco".equals(type)
                || "mere".equals(type)
                || "traf".equals(type)
                || "mvex".equals(type)
                || "moof".equals(type)
                || "sinf".equals(type)
                || "schi".equals(type)
                || "hnti".equals(type)
                || "hinf".equals(type)
                || "wave".equals(type)
                || "ilst".equals(type)
                || "tref".equals(type)
                || "mfra".equals(type)
                || "jp2h".equals(type)
                || "res ".equals(type)
                || "uuid".equals(type)
                || "ipro".equals(type)
                || "sgrp".equals(type)
                || "fiin".equals(type)
                || "paen".equals(type)
                || "trgr".equals(type)
                || "kind".equals(type)
                || "ipma".equals(type)
                || "pitm".equals(type);
    }

    private static long readU32AsLong(byte[] bytes, int offset) {
        if (offset + 4 > bytes.length) {
            return -1L;
        }
        return ((long) (bytes[offset] & 0xFF) << 24)
                | ((long) (bytes[offset + 1] & 0xFF) << 16)
                | ((long) (bytes[offset + 2] & 0xFF) << 8)
                | (bytes[offset + 3] & 0xFFL);
    }

    private static long readU64AsLong(byte[] bytes, int offset) {
        if (offset + 8 > bytes.length) {
            return -1L;
        }
        return ((long) (bytes[offset] & 0xFF) << 56)
                | ((long) (bytes[offset + 1] & 0xFF) << 48)
                | ((long) (bytes[offset + 2] & 0xFF) << 40)
                | ((long) (bytes[offset + 3] & 0xFF) << 32)
                | ((long) (bytes[offset + 4] & 0xFF) << 24)
                | ((long) (bytes[offset + 5] & 0xFF) << 16)
                | ((long) (bytes[offset + 6] & 0xFF) << 8)
                | (bytes[offset + 7] & 0xFFL);
    }

    private static int readBeInt(byte[] bytes, int offset) throws CodecMediaException {
        if (offset < 0 || offset + 4 > bytes.length) {
            throw new CodecMediaException("Unexpected end of HEIF data");
        }
        return ((bytes[offset] & 0xFF) << 24)
                | ((bytes[offset + 1] & 0xFF) << 16)
                | ((bytes[offset + 2] & 0xFF) << 8)
                | (bytes[offset + 3] & 0xFF);
    }

    private record BoxData(
            byte[] bytes,
            int boxStart,
            int payloadOffset,
            int boxEnd
    ) {
    }
}

