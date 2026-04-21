package me.tamkungz.codecmedia.internal.image.png;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import me.tamkungz.codecmedia.CodecMediaException;

class PngParserTest {

    @Test
    void shouldParseValidIhdr() throws Exception {
        byte[] png = createPngWithIhdr(640, 480, 8, 6);

        PngProbeInfo info = PngParser.parse(png);

        assertEquals(640, info.width());
        assertEquals(480, info.height());
        assertEquals(8, info.bitDepth());
        assertEquals(6, info.colorType());
    }

    @Test
    void shouldRejectInvalidBitDepthColorTypeCombination() {
        byte[] png = createPngWithIhdr(16, 16, 16, 3);

        CodecMediaException ex = assertThrows(CodecMediaException.class, () -> PngParser.parse(png));
        assertTrue(ex.getMessage().contains("invalid bit depth/color type combination"));
    }

    @Test
    void shouldReturnFalseForNullLikelyCheck() {
        assertFalse(PngParser.isLikelyPng(null));
    }

    @Test
    void shouldProbeViaPngCodec() throws Exception {
        byte[] png = createPngWithIhdr(320, 200, 8, 2);
        Path temp = Files.createTempFile("codecmedia-png-probe-", ".png");

        try {
            Files.write(temp, png);
            PngProbeInfo info = PngCodec.probe(temp);
            assertEquals(320, info.width());
            assertEquals(200, info.height());
            assertEquals(8, info.bitDepth());
            assertEquals(2, info.colorType());
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static byte[] createPngWithIhdr(int width, int height, int bitDepth, int colorType) {
        byte[] out = new byte[33];
        out[0] = (byte) 0x89;
        out[1] = 'P';
        out[2] = 'N';
        out[3] = 'G';
        out[4] = 0x0D;
        out[5] = 0x0A;
        out[6] = 0x1A;
        out[7] = 0x0A;

        writeBeInt(out, 8, 13);
        out[12] = 'I';
        out[13] = 'H';
        out[14] = 'D';
        out[15] = 'R';
        writeBeInt(out, 16, width);
        writeBeInt(out, 20, height);
        out[24] = (byte) bitDepth;
        out[25] = (byte) colorType;
        out[26] = 0;
        out[27] = 0;
        out[28] = 0;

        // CRC bytes are intentionally left as 0; parser currently does not validate CRC.
        return out;
    }

    private static void writeBeInt(byte[] out, int offset, int value) {
        out[offset] = (byte) ((value >>> 24) & 0xFF);
        out[offset + 1] = (byte) ((value >>> 16) & 0xFF);
        out[offset + 2] = (byte) ((value >>> 8) & 0xFF);
        out[offset + 3] = (byte) (value & 0xFF);
    }
}
