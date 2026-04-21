package me.tamkungz.codecmedia.internal.image.bmp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import me.tamkungz.codecmedia.CodecMediaException;

class BmpParserTest {

    @Test
    void shouldParseBitmapInfoHeader() throws Exception {
        byte[] bmp = createBmpInfoHeader(320, 240, 24, false);

        BmpProbeInfo info = BmpParser.parse(bmp);

        assertEquals(320, info.width());
        assertEquals(240, info.height());
        assertEquals(24, info.bitsPerPixel());
    }

    @Test
    void shouldParseBitmapCoreHeader() throws Exception {
        byte[] bmp = createBmpCoreHeader(64, 32, 8);

        BmpProbeInfo info = BmpParser.parse(bmp);

        assertEquals(64, info.width());
        assertEquals(32, info.height());
        assertEquals(8, info.bitsPerPixel());
    }

    @Test
    void shouldTreatTopDownHeightAsPositive() throws Exception {
        byte[] bmp = createBmpInfoHeader(128, 72, 32, true);

        BmpProbeInfo info = BmpParser.parse(bmp);

        assertEquals(128, info.width());
        assertEquals(72, info.height());
        assertEquals(32, info.bitsPerPixel());
    }

    @Test
    void shouldRejectTruncatedDibHeader() {
        byte[] bmp = createBmpWithTruncatedDib(124);

        CodecMediaException ex = assertThrows(CodecMediaException.class, () -> BmpParser.parse(bmp));
        assertEquals("BMP DIB header truncated", ex.getMessage());
    }

    @Test
    void shouldProbeViaBmpCodec() throws Exception {
        byte[] bmp = createBmpInfoHeader(200, 100, 24, false);
        Path temp = Files.createTempFile("codecmedia-bmp-probe-", ".bmp");

        try {
            Files.write(temp, bmp);
            BmpProbeInfo info = BmpCodec.probe(temp);
            assertEquals(200, info.width());
            assertEquals(100, info.height());
            assertEquals(24, info.bitsPerPixel());
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static byte[] createBmpInfoHeader(int width, int height, int bitsPerPixel, boolean topDown) {
        int fileSize = 14 + 40;
        byte[] out = new byte[fileSize];
        out[0] = 'B';
        out[1] = 'M';
        writeLe32(out, 2, fileSize);
        writeLe32(out, 10, 54);
        writeLe32(out, 14, 40);
        writeLe32(out, 18, width);
        writeLe32(out, 22, topDown ? -height : height);
        writeLe16(out, 26, 1);
        writeLe16(out, 28, bitsPerPixel);
        return out;
    }

    private static byte[] createBmpCoreHeader(int width, int height, int bitsPerPixel) {
        int fileSize = 14 + 12;
        byte[] out = new byte[fileSize];
        out[0] = 'B';
        out[1] = 'M';
        writeLe32(out, 2, fileSize);
        writeLe32(out, 10, 26);
        writeLe32(out, 14, 12);
        writeLe16(out, 18, width);
        writeLe16(out, 20, height);
        writeLe16(out, 22, 1);
        writeLe16(out, 24, bitsPerPixel);
        return out;
    }

    private static byte[] createBmpWithTruncatedDib(int dibHeaderSize) {
        byte[] out = new byte[14 + 40];
        out[0] = 'B';
        out[1] = 'M';
        writeLe32(out, 2, out.length);
        writeLe32(out, 10, 54);
        writeLe32(out, 14, dibHeaderSize);
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
}
