package me.tamkungz.codecmedia.internal.audio.ogg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

import me.tamkungz.codecmedia.CodecMediaException;
import me.tamkungz.codecmedia.internal.audio.BitrateMode;

class OggParserTest {

    @Test
    void shouldParseVorbisIdentificationFromFirstOggPage() throws Exception {
        byte[] data = createMinimalVorbisOgg();

        OggProbeInfo info = OggParser.parse(data);

        assertEquals("vorbis", info.codec());
        assertEquals(2, info.channels());
        assertEquals(44100, info.sampleRate());
        assertEquals(BitrateMode.UNKNOWN, info.bitrateMode());
    }

    @Test
    void shouldParseOpusIdentificationFromFirstOggPage() throws Exception {
        byte[] data = createMinimalOpusOgg();

        OggProbeInfo info = OggParser.parse(data);

        assertEquals("opus", info.codec());
        assertEquals(2, info.channels());
        assertEquals(48000, info.sampleRate());
        assertEquals(BitrateMode.VBR, info.bitrateMode());
    }

    @Test
    void shouldDetectVorbisVbrFromObservedPageBitrates() throws Exception {
        byte[] ident = createVorbisIdentPayload(2, 44100, 0);
        byte[] page1 = createOggPage(1, 0, 2, 0, ident);
        byte[] page2 = createOggPage(1, 1, 0, 44100, new byte[16000]);
        byte[] page3 = createOggPage(1, 2, 0, 88200, new byte[32000]);

        OggProbeInfo info = OggParser.parse(concat(page1, page2, page3));

        assertEquals("vorbis", info.codec());
        assertEquals(BitrateMode.VBR, info.bitrateMode());
    }

    @Test
    void shouldDetectVorbisCbrWhenObservedBitrateStaysConstant() throws Exception {
        byte[] ident = createVorbisIdentPayload(2, 44100, 0);
        byte[] page1 = createOggPage(1, 0, 2, 0, ident);
        byte[] page2 = createOggPage(1, 1, 0, 44100, new byte[16000]);
        byte[] page3 = createOggPage(1, 2, 0, 88200, new byte[16000]);

        OggProbeInfo info = OggParser.parse(concat(page1, page2, page3));

        assertEquals(BitrateMode.CBR, info.bitrateMode());
    }

    @Test
    void shouldFailOnBrokenSequenceForTargetStream() {
        byte[] ident = createVorbisIdentPayload(2, 44100, 128000);
        byte[] page1 = createOggPage(1, 0, 2, 0, ident);
        byte[] page2 = createOggPage(1, 2, 0, 44100, new byte[16000]); // missing seq=1

        CodecMediaException ex = assertThrows(CodecMediaException.class, () -> OggParser.parse(concat(page1, page2)));
        assertEquals("Invalid OGG stream: broken page sequence for target stream", ex.getMessage());
    }

    @Test
    void shouldIgnoreMultiplexedPagesFromOtherStreamsForMetrics() throws Exception {
        byte[] ident = createVorbisIdentPayload(2, 44100, 0);
        byte[] target1 = createOggPage(1, 0, 2, 0, ident);
        byte[] foreign = createOggPage(99, 0, 2, 123456, new byte[40000]);
        byte[] target2 = createOggPage(1, 1, 0, 44100, new byte[16000]);
        byte[] target3 = createOggPage(1, 2, 0, 88200, new byte[16000]);

        OggProbeInfo info = OggParser.parse(concat(target1, foreign, target2, target3));

        assertEquals("vorbis", info.codec());
        assertEquals(2000, info.durationMillis());
        assertEquals(128, info.bitrateKbps());
        assertEquals(BitrateMode.CBR, info.bitrateMode());
    }

    private static byte[] createMinimalVorbisOgg() {
        byte[] ident = createVorbisIdentPayload(2, 44100, 128000);
        return createOggPage(1, 0, 2, 0, ident);
    }

    private static byte[] createMinimalOpusOgg() {
        byte[] payload = new byte[19];
        payload[0] = 'O';
        payload[1] = 'p';
        payload[2] = 'u';
        payload[3] = 's';
        payload[4] = 'H';
        payload[5] = 'e';
        payload[6] = 'a';
        payload[7] = 'd';
        payload[8] = 1; // version
        payload[9] = 2; // channels
        payload[10] = 0; // pre-skip LE
        payload[11] = 0;

        int sampleRate = 48000;
        payload[12] = (byte) (sampleRate & 0xFF);
        payload[13] = (byte) ((sampleRate >>> 8) & 0xFF);
        payload[14] = (byte) ((sampleRate >>> 16) & 0xFF);
        payload[15] = (byte) ((sampleRate >>> 24) & 0xFF);

        payload[16] = 0; // output gain LE
        payload[17] = 0;
        payload[18] = 0; // channel mapping family

        return createOggPage(1, 0, 2, 0, payload);
    }

    private static byte[] createVorbisIdentPayload(int channels, int sampleRate, int nominalBitrate) {
        byte[] payload = new byte[30];
        payload[0] = 0x01;
        payload[1] = 'v';
        payload[2] = 'o';
        payload[3] = 'r';
        payload[4] = 'b';
        payload[5] = 'i';
        payload[6] = 's';
        payload[7] = 0;
        payload[8] = 0;
        payload[9] = 0;
        payload[10] = 0; // vorbis version LE
        payload[11] = (byte) channels;

        payload[12] = (byte) (sampleRate & 0xFF);
        payload[13] = (byte) ((sampleRate >>> 8) & 0xFF);
        payload[14] = (byte) ((sampleRate >>> 16) & 0xFF);
        payload[15] = (byte) ((sampleRate >>> 24) & 0xFF);

        payload[20] = (byte) (nominalBitrate & 0xFF);
        payload[21] = (byte) ((nominalBitrate >>> 8) & 0xFF);
        payload[22] = (byte) ((nominalBitrate >>> 16) & 0xFF);
        payload[23] = (byte) ((nominalBitrate >>> 24) & 0xFF);

        payload[28] = (byte) 0xB0;
        payload[29] = 1;
        return payload;
    }

    private static byte[] createOggPage(long serial, int sequence, int headerType, long granule, byte[] payload) {
        int segmentCount = (payload.length + 254) / 255;
        byte[] page = new byte[27 + segmentCount + payload.length];
        page[0] = 'O';
        page[1] = 'g';
        page[2] = 'g';
        page[3] = 'S';
        page[4] = 0; // version
        page[5] = (byte) headerType;

        for (int i = 0; i < 8; i++) {
            page[6 + i] = (byte) ((granule >>> (8 * i)) & 0xFF);
        }
        for (int i = 0; i < 4; i++) {
            page[14 + i] = (byte) ((serial >>> (8 * i)) & 0xFF);
            page[18 + i] = (byte) ((sequence >>> (8 * i)) & 0xFF);
        }
        page[26] = (byte) segmentCount;

        int remain = payload.length;
        int segPos = 27;
        while (remain > 0) {
            int l = Math.min(255, remain);
            page[segPos++] = (byte) l;
            remain -= l;
        }

        System.arraycopy(payload, 0, page, 27 + segmentCount, payload.length);
        return page;
    }

    private static byte[] concat(byte[]... arrays) {
        int total = 0;
        for (byte[] array : arrays) {
            total += array.length;
        }
        byte[] out = new byte[total];
        int offset = 0;
        for (byte[] array : arrays) {
            System.arraycopy(array, 0, out, offset, array.length);
            offset += array.length;
        }
        return out;
    }
}

