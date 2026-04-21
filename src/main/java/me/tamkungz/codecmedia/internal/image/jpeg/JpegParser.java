package me.tamkungz.codecmedia.internal.image.jpeg;

import me.tamkungz.codecmedia.CodecMediaException;

public final class JpegParser {

    private JpegParser() {
    }

    public static boolean isLikelyJpeg(byte[] bytes) {
        return bytes != null
                && bytes.length >= 2
                && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8;
    }

    public static JpegProbeInfo parse(byte[] bytes) throws CodecMediaException {
        if (!isLikelyJpeg(bytes)) {
            throw new CodecMediaException("Not a JPEG file");
        }

        int pos = 2; // after SOI
        while (pos + 4 <= bytes.length) {
            int markerPrefixStart = pos;
            while (pos < bytes.length && (bytes[pos] & 0xFF) == 0xFF) {
                pos++; // skip marker prefix + fill bytes between markers
            }
            if (pos == markerPrefixStart) {
                throw new CodecMediaException("Invalid JPEG marker alignment");
            }
            if (pos >= bytes.length) {
                throw new CodecMediaException("Unexpected end of JPEG while reading marker");
            }

            int marker = bytes[pos] & 0xFF;
            pos++;

            if (marker == 0xD9 || marker == 0xDA) {
                break; // EOI or SOS; no frame header found yet
            }

            if (marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) {
                continue; // standalone markers
            }

            if (pos + 2 > bytes.length) {
                throw new CodecMediaException("Unexpected end of JPEG while reading segment length");
            }
            int segmentLength = readBeShort(bytes, pos);
            if (segmentLength < 2) {
                throw new CodecMediaException("Invalid JPEG segment length: " + segmentLength);
            }

            int segmentDataStart = pos + 2;
            int segmentDataLength = segmentLength - 2;
            int nextPos = segmentDataStart + segmentDataLength;
            if (nextPos > bytes.length) {
                throw new CodecMediaException("JPEG segment exceeds file bounds");
            }

            if (isSofMarker(marker)) {
                if (segmentDataLength < 6) {
                    throw new CodecMediaException("Invalid SOF segment length");
                }
                int bitsPerSample = bytes[segmentDataStart] & 0xFF;
                int height = readBeShort(bytes, segmentDataStart + 1);
                int width = readBeShort(bytes, segmentDataStart + 3);
                int channels = bytes[segmentDataStart + 5] & 0xFF;

                if (width <= 0 || height <= 0) {
                    throw new CodecMediaException("JPEG has invalid dimensions");
                }
                if (!isValidBitsPerSample(bitsPerSample)) {
                    throw new CodecMediaException("Invalid JPEG bit precision: " + bitsPerSample);
                }
                if (!isValidChannels(channels)) {
                    throw new CodecMediaException("Invalid JPEG component count: " + channels);
                }
                return new JpegProbeInfo(width, height, bitsPerSample, channels);
            }

            pos = nextPos;
        }

        throw new CodecMediaException("JPEG SOF segment not found");
    }

    private static int readBeShort(byte[] bytes, int offset) throws CodecMediaException {
        if (offset + 2 > bytes.length) {
            throw new CodecMediaException("Unexpected end of JPEG data");
        }
        return ((bytes[offset] & 0xFF) << 8) | (bytes[offset + 1] & 0xFF);
    }

    private static boolean isValidBitsPerSample(int bitsPerSample) {
        return bitsPerSample == 8 || bitsPerSample == 12;
    }

    private static boolean isValidChannels(int channels) {
        return channels == 1 || channels == 3 || channels == 4;
    }

    private static boolean isSofMarker(int marker) {
        return marker == 0xC0 || marker == 0xC1 || marker == 0xC2
                || marker == 0xC3 || marker == 0xC5 || marker == 0xC6
                || marker == 0xC7 || marker == 0xC9 || marker == 0xCA
                || marker == 0xCB || marker == 0xCD || marker == 0xCE
                || marker == 0xCF;
    }
}

