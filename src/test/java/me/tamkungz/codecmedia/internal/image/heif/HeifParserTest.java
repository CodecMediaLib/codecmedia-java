package me.tamkungz.codecmedia.internal.image.heif;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class HeifParserTest {

    @Test
    void shouldParseHeifDimensionsAndBitDepthFromBoxes() throws Exception {
        byte[] bytes = createHeifSample("heic", 1280, 720, 0, new int[] {10, 10, 10});

        HeifProbeInfo info = HeifParser.parse(bytes);

        assertEquals("heic", info.majorBrand());
        assertEquals(1280, info.width());
        assertEquals(720, info.height());
        assertEquals(10, info.bitDepth());
    }

    @Test
    void shouldIgnorePixiWhenVersionIsNotZero() throws Exception {
        byte[] bytes = createHeifSample("avif", 1920, 1080, 1, new int[] {8, 8, 8});

        HeifProbeInfo info = HeifParser.parse(bytes);

        assertEquals("avif", info.majorBrand());
        assertEquals(1920, info.width());
        assertEquals(1080, info.height());
        assertNull(info.bitDepth());
    }

    @Test
    void shouldReturnFalseForNullLikelyCheck() {
        assertFalse(HeifParser.isLikelyHeif(null));
    }

    @Test
    void shouldProbeViaHeifCodecAndNormalizeAvif() throws Exception {
        byte[] bytes = createHeifSample("avif", 640, 360, 0, new int[] {8, 8, 8});
        Path temp = Files.createTempFile("codecmedia-heif-probe-", ".avif");

        try {
            Files.write(temp, bytes);
            HeifProbeInfo info = HeifCodec.probe(temp);
            assertEquals(640, info.width());
            assertEquals(360, info.height());
            assertEquals(8, info.bitDepth());
        } finally {
            Files.deleteIfExists(temp);
        }

        assertEquals("avif", HeifCodec.normalizeTargetExtension("avif"));
        assertEquals("heif", HeifCodec.normalizeTargetExtension("heif"));
        assertEquals("heic", HeifCodec.normalizeTargetExtension("heic"));
    }

    private static byte[] createHeifSample(String majorBrand, int width, int height, int pixiVersion, int[] channelDepths) {
        byte[] ftyp = createFtypBox(majorBrand);
        byte[] ispe = createIspeBox(width, height);
        byte[] pixi = createPixiBox(pixiVersion, channelDepths);

        byte[] out = new byte[ftyp.length + ispe.length + pixi.length];
        System.arraycopy(ftyp, 0, out, 0, ftyp.length);
        System.arraycopy(ispe, 0, out, ftyp.length, ispe.length);
        System.arraycopy(pixi, 0, out, ftyp.length + ispe.length, pixi.length);
        return out;
    }

    private static byte[] createFtypBox(String majorBrand) {
        byte[] box = new byte[16];
        writeBe32(box, 0, 16);
        writeAscii(box, 4, "ftyp");
        writeAscii(box, 8, majorBrand);
        writeBe32(box, 12, 0);
        return box;
    }

    private static byte[] createIspeBox(int width, int height) {
        byte[] box = new byte[20];
        writeBe32(box, 0, 20);
        writeAscii(box, 4, "ispe");
        // FullBox version+flags = 0
        writeBe32(box, 8, 0);
        writeBe32(box, 12, width);
        writeBe32(box, 16, height);
        return box;
    }

    private static byte[] createPixiBox(int version, int[] channelDepths) {
        byte[] box = new byte[8 + 4 + 1 + channelDepths.length];
        writeBe32(box, 0, box.length);
        writeAscii(box, 4, "pixi");
        box[8] = (byte) version;
        box[9] = 0;
        box[10] = 0;
        box[11] = 0;
        box[12] = (byte) channelDepths.length;
        for (int i = 0; i < channelDepths.length; i++) {
            box[13 + i] = (byte) channelDepths[i];
        }
        return box;
    }

    private static void writeBe32(byte[] out, int offset, int value) {
        out[offset] = (byte) ((value >>> 24) & 0xFF);
        out[offset + 1] = (byte) ((value >>> 16) & 0xFF);
        out[offset + 2] = (byte) ((value >>> 8) & 0xFF);
        out[offset + 3] = (byte) (value & 0xFF);
    }

    private static void writeAscii(byte[] out, int offset, String value) {
        for (int i = 0; i < 4; i++) {
            out[offset + i] = (byte) value.charAt(i);
        }
    }
}
