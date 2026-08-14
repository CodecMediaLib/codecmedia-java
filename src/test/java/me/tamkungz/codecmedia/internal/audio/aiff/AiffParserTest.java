package me.tamkungz.codecmedia.internal.audio.aiff;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import me.tamkungz.codecmedia.CodecMediaException;
import me.tamkungz.codecmedia.internal.audio.BitrateMode;

class AiffParserTest {

    @Test
    void shouldParseBasicAiffComm() throws Exception {
        byte[] aiff = createMinimalAiff(2, 44100, 16, 44100);

        AiffProbeInfo info = AiffParser.parse(aiff);

        assertEquals(2, info.channels());
        assertEquals(44100, info.sampleRate());
        assertEquals(1411, info.bitrateKbps());
        assertEquals(1000, info.durationMillis());
        assertEquals(BitrateMode.CBR, info.bitrateMode());
    }

    @Test
    void shouldParseAifcWithPcmCompressionTypeNone() throws Exception {
        byte[] aifc = createMinimalAifc(2, 44100, 16, 44100, "NONE");

        AiffProbeInfo info = AiffParser.parse(aifc);

        assertEquals(2, info.channels());
        assertEquals(44100, info.sampleRate());
        assertEquals(1411, info.bitrateKbps());
    }

    @Test
    void shouldRejectUnsupportedAifcCompressionType() {
        byte[] aifc = createMinimalAifc(2, 44100, 16, 44100, "ulaw");

        CodecMediaException ex = assertThrows(CodecMediaException.class, () -> AiffParser.parse(aifc));
        assertEquals("Unsupported AIFC compression type: ulaw", ex.getMessage());
    }

    @Test
    void shouldReadAiffTextMetadataChunks() throws Exception {
        byte[] base = createMinimalAiff(2, 44100, 16, 44100);
        byte[] withMetadata = AiffParser.writeTextMetadata(base, Map.of(
                "title", "AIFF Title",
                "artist", "AIFF Artist",
                "copyright", "(c) CodecMedia",
                "comment", "AIFF Note"
        ));

        Map<String, String> metadata = AiffParser.readTextMetadata(withMetadata);
        assertEquals("AIFF Title", metadata.get("title"));
        assertEquals("AIFF Artist", metadata.get("artist"));
        assertEquals("(c) CodecMedia", metadata.get("copyright"));
        assertEquals("AIFF Note", metadata.get("comment"));
    }

    @Test
    void shouldWriteAiffTextMetadataWithoutBreakingProbe() throws Exception {
        byte[] base = createMinimalAiff(2, 44100, 16, 44100);
        byte[] withMetadata = AiffParser.writeTextMetadata(base, Map.of(
                "title", "After Write"
        ));

        AiffProbeInfo info = AiffParser.parse(withMetadata);
        assertEquals(2, info.channels());
        assertEquals(44100, info.sampleRate());
        assertTrue(withMetadata.length >= base.length);
    }

    private static byte[] createMinimalAiff(int channels, int sampleRate, int bitsPerSample, int frames) {
        int commChunkSize = 18;
        int formSize = 4 + 8 + commChunkSize;
        byte[] bytes = new byte[8 + formSize];

        bytes[0] = 'F'; bytes[1] = 'O'; bytes[2] = 'R'; bytes[3] = 'M';
        writeBeInt(bytes, 4, formSize);
        bytes[8] = 'A'; bytes[9] = 'I'; bytes[10] = 'F'; bytes[11] = 'F';

        bytes[12] = 'C'; bytes[13] = 'O'; bytes[14] = 'M'; bytes[15] = 'M';
        writeBeInt(bytes, 16, commChunkSize);
        writeBeShort(bytes, 20, channels);
        writeBeInt(bytes, 22, frames);
        writeBeShort(bytes, 26, bitsPerSample);
        writeExtended80Integer(bytes, 28, sampleRate);

        return bytes;
    }

    private static byte[] createMinimalAifc(int channels, int sampleRate, int bitsPerSample, int frames, String compressionType) {
        int commChunkSize = 22;
        int formSize = 4 + 8 + commChunkSize;
        byte[] bytes = new byte[8 + formSize];

        bytes[0] = 'F'; bytes[1] = 'O'; bytes[2] = 'R'; bytes[3] = 'M';
        writeBeInt(bytes, 4, formSize);
        bytes[8] = 'A'; bytes[9] = 'I'; bytes[10] = 'F'; bytes[11] = 'C';

        bytes[12] = 'C'; bytes[13] = 'O'; bytes[14] = 'M'; bytes[15] = 'M';
        writeBeInt(bytes, 16, commChunkSize);
        writeBeShort(bytes, 20, channels);
        writeBeInt(bytes, 22, frames);
        writeBeShort(bytes, 26, bitsPerSample);
        writeExtended80Integer(bytes, 28, sampleRate);
        bytes[38] = (byte) compressionType.charAt(0);
        bytes[39] = (byte) compressionType.charAt(1);
        bytes[40] = (byte) compressionType.charAt(2);
        bytes[41] = (byte) compressionType.charAt(3);

        return bytes;
    }

    private static void writeExtended80Integer(byte[] out, int offset, int value) {
        // Integer encoded as normalized 80-bit extended float.
        int msb = 31 - Integer.numberOfLeadingZeros(value);
        int exp = 16383 + msb;
        long mantissa = ((long) value) << (63 - msb);

        out[offset] = (byte) ((exp >>> 8) & 0x7F);
        out[offset + 1] = (byte) (exp & 0xFF);
        for (int i = 0; i < 8; i++) {
            out[offset + 2 + i] = (byte) ((mantissa >>> (56 - (i * 8))) & 0xFF);
        }
    }

    private static void writeBeShort(byte[] out, int offset, int value) {
        out[offset] = (byte) ((value >>> 8) & 0xFF);
        out[offset + 1] = (byte) (value & 0xFF);
    }

    private static void writeBeInt(byte[] out, int offset, int value) {
        out[offset] = (byte) ((value >>> 24) & 0xFF);
        out[offset + 1] = (byte) ((value >>> 16) & 0xFF);
        out[offset + 2] = (byte) ((value >>> 8) & 0xFF);
        out[offset + 3] = (byte) (value & 0xFF);
    }
}

