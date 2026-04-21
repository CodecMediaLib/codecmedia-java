package me.tamkungz.codecmedia.internal.image.tiff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class TiffParserTest {

    @Test
    void shouldParseLittleEndianIfdAndFirstBitsPerSampleValue() throws Exception {
        byte[] tiff = createLittleEndianTiff(320, 240, new int[] {8, 8, 8});

        TiffProbeInfo info = TiffParser.parse(tiff);

        assertEquals(320, info.width());
        assertEquals(240, info.height());
        assertEquals(8, info.bitDepth());
    }

    @Test
    void shouldParseBigEndianIfd() throws Exception {
        byte[] tiff = createBigEndianTiff(640, 480, 16);

        TiffProbeInfo info = TiffParser.parse(tiff);

        assertEquals(640, info.width());
        assertEquals(480, info.height());
        assertEquals(16, info.bitDepth());
    }

    @Test
    void shouldReturnFalseForNullLikelyCheck() {
        assertFalse(TiffParser.isLikelyTiff(null));
    }

    @Test
    void shouldProbeViaTiffCodec() throws Exception {
        byte[] tiff = createLittleEndianTiff(100, 50, new int[] {8, 8, 8});
        Path temp = Files.createTempFile("codecmedia-tiff-probe-", ".tiff");

        try {
            Files.write(temp, tiff);
            TiffProbeInfo info = TiffCodec.probe(temp);
            assertEquals(100, info.width());
            assertEquals(50, info.height());
            assertEquals(8, info.bitDepth());
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static byte[] createLittleEndianTiff(int width, int height, int[] bitsPerSample) {
        int ifdOffset = 8;
        int entryCount = 3;
        int ifdSize = 2 + (entryCount * 12) + 4;
        int bitsOffset = ifdOffset + ifdSize;
        int total = bitsOffset + (bitsPerSample.length * 2);

        byte[] out = new byte[total];
        out[0] = 'I';
        out[1] = 'I';
        writeLe16(out, 2, 42);
        writeLe32(out, 4, ifdOffset);

        writeLe16(out, ifdOffset, entryCount);
        int pos = ifdOffset + 2;

        // ImageWidth (tag 256), LONG
        writeLe16(out, pos, 256);
        writeLe16(out, pos + 2, 4);
        writeLe32(out, pos + 4, 1);
        writeLe32(out, pos + 8, width);
        pos += 12;

        // ImageLength (tag 257), LONG
        writeLe16(out, pos, 257);
        writeLe16(out, pos + 2, 4);
        writeLe32(out, pos + 4, 1);
        writeLe32(out, pos + 8, height);
        pos += 12;

        // BitsPerSample (tag 258), SHORT[count=3], offset -> array
        writeLe16(out, pos, 258);
        writeLe16(out, pos + 2, 3);
        writeLe32(out, pos + 4, bitsPerSample.length);
        writeLe32(out, pos + 8, bitsOffset);

        // Next IFD offset = 0
        writeLe32(out, ifdOffset + 2 + entryCount * 12, 0);

        for (int i = 0; i < bitsPerSample.length; i++) {
            writeLe16(out, bitsOffset + (i * 2), bitsPerSample[i]);
        }
        return out;
    }

    private static byte[] createBigEndianTiff(int width, int height, int bitDepth) {
        int ifdOffset = 8;
        int entryCount = 3;
        int ifdSize = 2 + (entryCount * 12) + 4;
        byte[] out = new byte[ifdOffset + ifdSize];

        out[0] = 'M';
        out[1] = 'M';
        writeBe16(out, 2, 42);
        writeBe32(out, 4, ifdOffset);

        writeBe16(out, ifdOffset, entryCount);
        int pos = ifdOffset + 2;

        // ImageWidth (tag 256), LONG
        writeBe16(out, pos, 256);
        writeBe16(out, pos + 2, 4);
        writeBe32(out, pos + 4, 1);
        writeBe32(out, pos + 8, width);
        pos += 12;

        // ImageLength (tag 257), LONG
        writeBe16(out, pos, 257);
        writeBe16(out, pos + 2, 4);
        writeBe32(out, pos + 4, 1);
        writeBe32(out, pos + 8, height);
        pos += 12;

        // BitsPerSample (tag 258), SHORT[count=1], inline high 16 bits
        writeBe16(out, pos, 258);
        writeBe16(out, pos + 2, 3);
        writeBe32(out, pos + 4, 1);
        writeBe32(out, pos + 8, bitDepth << 16);

        // Next IFD offset = 0
        writeBe32(out, ifdOffset + 2 + entryCount * 12, 0);

        return out;
    }

    private static void writeLe16(byte[] out, int offset, int value) {
        out[offset] = (byte) (value & 0xFF);
        out[offset + 1] = (byte) ((value >>> 8) & 0xFF);
    }

    private static void writeLe32(byte[] out, int offset, int value) {
        out[offset] = (byte) (value & 0xFF);
        out[offset + 1] = (byte) ((value >>> 8) & 0xFF);
        out[offset + 2] = (byte) ((value >>> 16) & 0xFF);
        out[offset + 3] = (byte) ((value >>> 24) & 0xFF);
    }

    private static void writeBe16(byte[] out, int offset, int value) {
        out[offset] = (byte) ((value >>> 8) & 0xFF);
        out[offset + 1] = (byte) (value & 0xFF);
    }

    private static void writeBe32(byte[] out, int offset, int value) {
        out[offset] = (byte) ((value >>> 24) & 0xFF);
        out[offset + 1] = (byte) ((value >>> 16) & 0xFF);
        out[offset + 2] = (byte) ((value >>> 8) & 0xFF);
        out[offset + 3] = (byte) (value & 0xFF);
    }
}
