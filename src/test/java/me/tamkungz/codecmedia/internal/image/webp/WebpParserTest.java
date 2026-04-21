package me.tamkungz.codecmedia.internal.image.webp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import me.tamkungz.codecmedia.CodecMediaException;

class WebpParserTest {

    @Test
    void shouldParseVp8Dimensions() throws Exception {
        byte[] vp8Payload = new byte[] {
                0x10, 0x00, 0x00,
                (byte) 0x9D, 0x01, 0x2A,
                (byte) 0x80, 0x02,
                (byte) 0xE0, 0x01
        };

        WebpProbeInfo info = WebpParser.parse(wrapWebp("VP8 ", vp8Payload));

        assertEquals(640, info.width());
        assertEquals(480, info.height());
        assertEquals(8, info.bitDepth());
    }

    @Test
    void shouldParseVp8lDimensions() throws Exception {
        byte[] vp8lPayload = new byte[] {
                0x2F,
                0x3F,
                (byte) 0xC1,
                0x3B,
                0x00
        };

        WebpProbeInfo info = WebpParser.parse(wrapWebp("VP8L", vp8lPayload));

        assertEquals(320, info.width());
        assertEquals(240, info.height());
    }

    @Test
    void shouldParseVp8xDimensions() throws Exception {
        byte[] vp8xPayload = new byte[] {
                0x00, 0x00, 0x00, 0x00,
                (byte) 0xFF, 0x03, 0x00,
                (byte) 0xCF, 0x02, 0x00
        };

        WebpProbeInfo info = WebpParser.parse(wrapWebp("VP8X", vp8xPayload));

        assertEquals(1024, info.width());
        assertEquals(720, info.height());
    }

    @Test
    void shouldRejectTruncatedRiffByDeclaredSize() {
        byte[] vp8Payload = new byte[] {
                0x10, 0x00, 0x00,
                (byte) 0x9D, 0x01, 0x2A,
                0x01, 0x00,
                0x01, 0x00
        };
        byte[] bytes = wrapWebp("VP8 ", vp8Payload);
        // Corrupt RIFF size to larger than buffer length.
        bytes[4] = (byte) 0xFF;
        bytes[5] = (byte) 0xFF;
        bytes[6] = (byte) 0xFF;
        bytes[7] = 0x7F;

        assertFalse(WebpParser.isLikelyWebp(bytes));
        assertThrows(CodecMediaException.class, () -> WebpParser.parse(bytes));
    }

    @Test
    void shouldRejectVp8NonKeyFrame() {
        byte[] vp8Payload = new byte[] {
                0x11, 0x00, 0x00,
                (byte) 0x9D, 0x01, 0x2A,
                0x01, 0x00,
                0x01, 0x00
        };

        CodecMediaException ex = assertThrows(CodecMediaException.class,
                () -> WebpParser.parse(wrapWebp("VP8 ", vp8Payload)));
        assertEquals("Invalid WebP VP8 frame type: expected key frame", ex.getMessage());
    }

    private static byte[] wrapWebp(String chunkType, byte[] chunkPayload) {
        int riffSize = 4 + 8 + chunkPayload.length;
        byte[] out = new byte[8 + riffSize];
        out[0] = 'R';
        out[1] = 'I';
        out[2] = 'F';
        out[3] = 'F';
        writeLeInt(out, 4, riffSize);
        out[8] = 'W';
        out[9] = 'E';
        out[10] = 'B';
        out[11] = 'P';

        out[12] = (byte) chunkType.charAt(0);
        out[13] = (byte) chunkType.charAt(1);
        out[14] = (byte) chunkType.charAt(2);
        out[15] = (byte) chunkType.charAt(3);
        writeLeInt(out, 16, chunkPayload.length);
        System.arraycopy(chunkPayload, 0, out, 20, chunkPayload.length);
        return out;
    }

    private static void writeLeInt(byte[] out, int offset, int value) {
        out[offset] = (byte) (value & 0xFF);
        out[offset + 1] = (byte) ((value >>> 8) & 0xFF);
        out[offset + 2] = (byte) ((value >>> 16) & 0xFF);
        out[offset + 3] = (byte) ((value >>> 24) & 0xFF);
    }
}
