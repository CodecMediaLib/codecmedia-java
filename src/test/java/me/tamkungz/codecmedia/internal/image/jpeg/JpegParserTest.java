package me.tamkungz.codecmedia.internal.image.jpeg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import me.tamkungz.codecmedia.CodecMediaException;

class JpegParserTest {

    @Test
    void shouldParseSofDimensionsFromMinimalJpeg() throws Exception {
        byte[] jpeg = createMinimalJpeg(48, 32, 8, 3);

        JpegProbeInfo info = JpegParser.parse(jpeg);

        assertEquals(48, info.width());
        assertEquals(32, info.height());
        assertEquals(8, info.bitsPerSample());
        assertEquals(3, info.channels());
    }

    @Test
    void shouldTreatTwoByteSoiAsLikelyJpeg() {
        assertTrue(JpegParser.isLikelyJpeg(new byte[] {(byte) 0xFF, (byte) 0xD8}));
        assertFalse(JpegParser.isLikelyJpeg(new byte[] {(byte) 0xFF}));
        assertFalse(JpegParser.isLikelyJpeg(null));
    }

    @Test
    void shouldRejectInvalidMarkerAlignmentAfterSoi() {
        byte[] invalid = new byte[] {(byte) 0xFF, (byte) 0xD8, 0x00, 0x11, 0x22, 0x33, 0x44};

        CodecMediaException ex = assertThrows(CodecMediaException.class, () -> JpegParser.parse(invalid));
        assertTrue(ex.getMessage().contains("Invalid JPEG marker alignment"));
    }

    @Test
    void shouldProbeViaJpegCodec() throws Exception {
        byte[] jpeg = createMinimalJpeg(120, 90, 8, 3);
        Path temp = Files.createTempFile("codecmedia-jpeg-probe-", ".jpg");

        try {
            Files.write(temp, jpeg);
            JpegProbeInfo info = JpegCodec.probe(temp);
            assertEquals(120, info.width());
            assertEquals(90, info.height());
            assertEquals(8, info.bitsPerSample());
            assertEquals(3, info.channels());
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static byte[] createMinimalJpeg(int width, int height, int bitsPerSample, int channels) {
        return new byte[] {
                (byte) 0xFF, (byte) 0xD8,
                (byte) 0xFF, (byte) 0xE0, 0x00, 0x04, 0x00, 0x00,
                (byte) 0xFF, (byte) 0xC0, 0x00, 0x08,
                (byte) bitsPerSample,
                (byte) ((height >>> 8) & 0xFF), (byte) (height & 0xFF),
                (byte) ((width >>> 8) & 0xFF), (byte) (width & 0xFF),
                (byte) channels,
                (byte) 0xFF, (byte) 0xD9
        };
    }
}
