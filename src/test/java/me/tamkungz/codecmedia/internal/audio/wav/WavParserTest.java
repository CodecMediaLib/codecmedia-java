package me.tamkungz.codecmedia.internal.audio.wav;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import me.tamkungz.codecmedia.CodecMediaException;
import me.tamkungz.codecmedia.internal.audio.BitrateMode;

class WavParserTest {

    @Test
    void shouldParsePcm16Stereo44100() throws Exception {
        byte[] wav = createPcmWav(2, 44100, 16, 44100 * 2 * 2);

        WavProbeInfo info = WavParser.parse(wav);

        assertEquals(2, info.channels());
        assertEquals(44100, info.sampleRate());
        assertEquals(1411, info.bitrateKbps());
        assertEquals(1000, info.durationMillis());
        assertEquals(BitrateMode.CBR, info.bitrateMode());
    }

    @Test
    void shouldParsePcm8Mono22050() throws Exception {
        byte[] wav = createPcmWav(1, 22050, 8, 22050);

        WavProbeInfo info = WavParser.parse(wav);

        assertEquals(1, info.channels());
        assertEquals(22050, info.sampleRate());
        assertEquals(176, info.bitrateKbps());
        assertEquals(1000, info.durationMillis());
        assertEquals(BitrateMode.CBR, info.bitrateMode());
    }

    @Test
    void shouldRejectUnsupportedCompressedWavFormat() {
        byte[] wav = createPcmWav(2, 44100, 16, 44100 * 2 * 2);
        writeLeShort(wav, 20, 0x0002); // ADPCM

        CodecMediaException ex = assertThrows(CodecMediaException.class, () -> WavParser.parse(wav));
        assertTrue(ex.getMessage().contains("Unsupported WAV audio format"));
    }

    @Test
    void shouldParseRf64DataSizeFromDs64() throws Exception {
        byte[] wav = createRf64WithDs64Data(2, 48000, 16, 48000L * 2 * 2);

        WavProbeInfo info = WavParser.parse(wav);

        assertEquals(2, info.channels());
        assertEquals(48000, info.sampleRate());
        assertEquals(1536, info.bitrateKbps());
        assertEquals(1000, info.durationMillis());
        assertEquals(BitrateMode.CBR, info.bitrateMode());
    }

    @Test
    void shouldWriteAndReadListInfoMetadata() throws Exception {
        byte[] wav = createPcmWav(2, 44100, 16, 44100 * 2 * 2);

        byte[] withMetadata = WavParser.writeInfoMetadata(wav, Map.of(
                "title", "CodecMedia Song",
                "artist", "CodecMedia Artist",
                "album", "CodecMedia Album",
                "comment", "Embedded RIFF INFO",
                "date", "2026-03-16",
                "genre", "Test"
        ));

        assertNotNull(withMetadata);
        assertTrue(withMetadata.length > wav.length);

        Map<String, String> extracted = WavParser.readInfoMetadata(withMetadata);
        assertEquals("CodecMedia Song", extracted.get("title"));
        assertEquals("CodecMedia Artist", extracted.get("artist"));
        assertEquals("CodecMedia Album", extracted.get("album"));
        assertEquals("Embedded RIFF INFO", extracted.get("comment"));
        assertEquals("2026-03-16", extracted.get("date"));
        assertEquals("Test", extracted.get("genre"));

        WavProbeInfo info = WavParser.parse(withMetadata);
        assertEquals(2, info.channels());
        assertEquals(44100, info.sampleRate());
    }

    @Test
    void shouldRemoveExistingInfoListWhenWritingEmptyMetadata() throws Exception {
        byte[] wav = createPcmWav(2, 44100, 16, 44100 * 2 * 2);
        byte[] withMetadata = WavParser.writeInfoMetadata(wav, Map.of("title", "Before"));

        byte[] withoutMetadata = WavParser.writeInfoMetadata(withMetadata, Map.of());
        Map<String, String> extracted = WavParser.readInfoMetadata(withoutMetadata);

        assertFalse(extracted.containsKey("title"));
        WavProbeInfo info = WavParser.parse(withoutMetadata);
        assertEquals(2, info.channels());
        assertEquals(44100, info.sampleRate());
    }

    private static byte[] createPcmWav(int channels, int sampleRate, int bitsPerSample, int dataSize) {
        int chunkSize = 36 + dataSize;
        byte[] out = new byte[44 + dataSize];

        out[0] = 'R'; out[1] = 'I'; out[2] = 'F'; out[3] = 'F';
        writeLeInt(out, 4, chunkSize);
        out[8] = 'W'; out[9] = 'A'; out[10] = 'V'; out[11] = 'E';

        out[12] = 'f'; out[13] = 'm'; out[14] = 't'; out[15] = ' ';
        writeLeInt(out, 16, 16);
        writeLeShort(out, 20, 1);
        writeLeShort(out, 22, channels);
        writeLeInt(out, 24, sampleRate);
        int byteRate = sampleRate * channels * bitsPerSample / 8;
        writeLeInt(out, 28, byteRate);
        int blockAlign = channels * bitsPerSample / 8;
        writeLeShort(out, 32, blockAlign);
        writeLeShort(out, 34, bitsPerSample);

        out[36] = 'd'; out[37] = 'a'; out[38] = 't'; out[39] = 'a';
        writeLeInt(out, 40, dataSize);
        return out;
    }

    private static byte[] createRf64WithDs64Data(int channels, int sampleRate, int bitsPerSample, long ds64DataSize) {
        int fmtSize = 16;
        int ds64Size = 28;
        int dataPayload = (int) ds64DataSize;
        int total = 12 + 8 + ds64Size + 8 + fmtSize + 8 + dataPayload;
        byte[] out = new byte[total];

        out[0] = 'R'; out[1] = 'F'; out[2] = '6'; out[3] = '4';
        writeLeInt(out, 4, -1); // RF64 sentinel
        out[8] = 'W'; out[9] = 'A'; out[10] = 'V'; out[11] = 'E';

        int o = 12;
        out[o] = 'd'; out[o + 1] = 's'; out[o + 2] = '6'; out[o + 3] = '4';
        writeLeInt(out, o + 4, ds64Size);
        writeLeLong(out, o + 8, (long) total - 8); // riffSize64
        writeLeLong(out, o + 16, ds64DataSize); // dataSize64
        writeLeLong(out, o + 24, 0); // sampleCount
        writeLeInt(out, o + 32, 0); // table length
        o += 8 + ds64Size;

        out[o] = 'f'; out[o + 1] = 'm'; out[o + 2] = 't'; out[o + 3] = ' ';
        writeLeInt(out, o + 4, fmtSize);
        writeLeShort(out, o + 8, 1);
        writeLeShort(out, o + 10, channels);
        writeLeInt(out, o + 12, sampleRate);
        int byteRate = sampleRate * channels * bitsPerSample / 8;
        writeLeInt(out, o + 16, byteRate);
        int blockAlign = channels * bitsPerSample / 8;
        writeLeShort(out, o + 20, blockAlign);
        writeLeShort(out, o + 22, bitsPerSample);
        o += 8 + fmtSize;

        out[o] = 'd'; out[o + 1] = 'a'; out[o + 2] = 't'; out[o + 3] = 'a';
        writeLeInt(out, o + 4, -1); // RF64 sentinel size
        return out;
    }

    private static void writeLeShort(byte[] out, int offset, int value) {
        out[offset] = (byte) (value & 0xFF);
        out[offset + 1] = (byte) ((value >>> 8) & 0xFF);
    }

    private static void writeLeInt(byte[] out, int offset, int value) {
        out[offset] = (byte) (value & 0xFF);
        out[offset + 1] = (byte) ((value >>> 8) & 0xFF);
        out[offset + 2] = (byte) ((value >>> 16) & 0xFF);
        out[offset + 3] = (byte) ((value >>> 24) & 0xFF);
    }

    private static void writeLeLong(byte[] out, int offset, long value) {
        out[offset] = (byte) (value & 0xFF);
        out[offset + 1] = (byte) ((value >>> 8) & 0xFF);
        out[offset + 2] = (byte) ((value >>> 16) & 0xFF);
        out[offset + 3] = (byte) ((value >>> 24) & 0xFF);
        out[offset + 4] = (byte) ((value >>> 32) & 0xFF);
        out[offset + 5] = (byte) ((value >>> 40) & 0xFF);
        out[offset + 6] = (byte) ((value >>> 48) & 0xFF);
        out[offset + 7] = (byte) ((value >>> 56) & 0xFF);
    }
}
