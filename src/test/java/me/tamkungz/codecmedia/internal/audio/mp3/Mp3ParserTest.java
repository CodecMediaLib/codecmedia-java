package me.tamkungz.codecmedia.internal.audio.mp3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import me.tamkungz.codecmedia.CodecMediaException;
import me.tamkungz.codecmedia.internal.audio.BitrateMode;

class Mp3ParserTest {

    @Test
    void shouldParseSimpleMpeg1Layer3FrameSequenceAsCbr() throws Exception {
        byte[] frameHeader = new byte[] {(byte) 0xFF, (byte) 0xFB, (byte) 0x90, 0x00};
        int frameLength = 417; // 128 kbps @ 44100, MPEG1 L3 no padding
        byte[] frame = new byte[frameLength];
        System.arraycopy(frameHeader, 0, frame, 0, frameHeader.length);

        byte[] data = new byte[frameLength * 3];
        System.arraycopy(frame, 0, data, 0, frameLength);
        System.arraycopy(frame, 0, data, frameLength, frameLength);
        System.arraycopy(frame, 0, data, frameLength * 2, frameLength);

        Mp3ProbeInfo info = Mp3Parser.parse(data);

        assertEquals("mp3", info.codec());
        assertEquals(44100, info.sampleRate());
        assertEquals(2, info.channels());
        assertEquals(BitrateMode.CBR, info.bitrateMode());
    }

    @Test
    void shouldDetectVbrWhenFrameBitratesDiffer() throws Exception {
        byte[] frame128 = createFrame(new byte[] {(byte) 0xFF, (byte) 0xFB, (byte) 0x90, 0x00}, 417);
        byte[] frame64 = createFrame(new byte[] {(byte) 0xFF, (byte) 0xFB, (byte) 0x50, 0x00}, 208);

        byte[] data = new byte[frame128.length + frame64.length];
        System.arraycopy(frame128, 0, data, 0, frame128.length);
        System.arraycopy(frame64, 0, data, frame128.length, frame64.length);

        Mp3ProbeInfo info = Mp3Parser.parse(data);

        assertEquals(44100, info.sampleRate());
        assertEquals(BitrateMode.VBR, info.bitrateMode());
    }

    @Test
    void shouldParseMonoFromChannelMode() throws Exception {
        byte[] frame = createFrame(new byte[] {(byte) 0xFF, (byte) 0xFB, (byte) 0x90, (byte) 0xC0}, 417);
        byte[] data = new byte[frame.length * 2];
        System.arraycopy(frame, 0, data, 0, frame.length);
        System.arraycopy(frame, 0, data, frame.length, frame.length);

        Mp3ProbeInfo info = Mp3Parser.parse(data);

        assertEquals(1, info.channels());
        assertEquals(BitrateMode.CBR, info.bitrateMode());
    }

    @Test
    void shouldPrioritizeXingFrameCountForDuration() throws Exception {
        byte[] frame = createFrame(new byte[] {(byte) 0xFF, (byte) 0xFB, (byte) 0x90, 0x00}, 417);
        // MPEG1 stereo => Xing header starts at frameOffset + 4 + 32 = 36
        frame[36] = 'X';
        frame[37] = 'i';
        frame[38] = 'n';
        frame[39] = 'g';
        frame[40] = 0x00;
        frame[41] = 0x00;
        frame[42] = 0x00;
        frame[43] = 0x01; // frames field present
        frame[44] = 0x00;
        frame[45] = 0x00;
        frame[46] = 0x00;
        frame[47] = 0x64; // 100 frames

        byte[] data = new byte[frame.length * 3];
        System.arraycopy(frame, 0, data, 0, frame.length);
        System.arraycopy(frame, 0, data, frame.length, frame.length);
        System.arraycopy(frame, 0, data, frame.length * 2, frame.length);

        Mp3ProbeInfo info = Mp3Parser.parse(data);

        assertEquals(2612, info.durationMillis()); // 100 * 1152 / 44100 * 1000
    }

    @Test
    void shouldRejectNonLayer3WithClearError() {
        byte[] headerLayer2 = new byte[] {(byte) 0xFF, (byte) 0xFD, (byte) 0x90, 0x00};
        byte[] data = new byte[417 * 2];
        System.arraycopy(headerLayer2, 0, data, 0, headerLayer2.length);
        System.arraycopy(headerLayer2, 0, data, 417, headerLayer2.length);

        CodecMediaException ex = assertThrows(CodecMediaException.class, () -> Mp3Parser.parse(data));
        assertTrue(ex.getMessage().contains("Unsupported MPEG audio layer"));
    }

    @Test
    void shouldIgnoreTrailingId3v1TagForParsing() throws Exception {
        byte[] frame = createFrame(new byte[] {(byte) 0xFF, (byte) 0xFB, (byte) 0x90, 0x00}, 417);
        byte[] data = new byte[frame.length * 3 + 128];
        System.arraycopy(frame, 0, data, 0, frame.length);
        System.arraycopy(frame, 0, data, frame.length, frame.length);
        System.arraycopy(frame, 0, data, frame.length * 2, frame.length);

        int tagOffset = frame.length * 3;
        data[tagOffset] = 'T';
        data[tagOffset + 1] = 'A';
        data[tagOffset + 2] = 'G';

        Mp3ProbeInfo info = Mp3Parser.parse(data);

        assertEquals(128, info.bitrateKbps());
        assertEquals(BitrateMode.CBR, info.bitrateMode());
    }

    private static byte[] createFrame(byte[] header, int frameLength) {
        byte[] frame = new byte[frameLength];
        System.arraycopy(header, 0, frame, 0, header.length);
        return frame;
    }
}

