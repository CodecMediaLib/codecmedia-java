package me.tamkungz.codecmedia.internal.convert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import me.tamkungz.codecmedia.CodecMedia;
import me.tamkungz.codecmedia.CodecMediaEngine;
import me.tamkungz.codecmedia.CodecMediaException;
import me.tamkungz.codecmedia.options.ConversionOptions;

class Mp3PcmWavConverterTest {

    @Test
    void shouldResolveDefaultDecoderBackendToJavaSound() throws Exception {
        assertEquals(Mp3PcmWavConverter.DecoderBackend.JAVA_SOUND, Mp3PcmWavConverter.resolveDecoderBackend(null));
        assertEquals(Mp3PcmWavConverter.DecoderBackend.JAVA_SOUND, Mp3PcmWavConverter.resolveDecoderBackend(""));
        assertEquals(Mp3PcmWavConverter.DecoderBackend.JAVA_SOUND, Mp3PcmWavConverter.resolveDecoderBackend("balanced"));
        assertEquals(Mp3PcmWavConverter.DecoderBackend.JAVA_SOUND, Mp3PcmWavConverter.resolveDecoderBackend("decoder=javasound"));
    }

    @Test
    void shouldResolvePureJavaDecoderBackendAliases() throws Exception {
        assertEquals(Mp3PcmWavConverter.DecoderBackend.PURE_JAVA,
                Mp3PcmWavConverter.resolveDecoderBackend("decoder=pure-java"));
        assertEquals(Mp3PcmWavConverter.DecoderBackend.PURE_JAVA,
                Mp3PcmWavConverter.resolveDecoderBackend("decoder=layer3"));
        assertEquals(Mp3PcmWavConverter.DecoderBackend.PURE_JAVA,
                Mp3PcmWavConverter.resolveDecoderBackend("balanced,decoder=layer3"));
    }

    @Test
    void shouldRejectUnsupportedDecoderToken() {
        CodecMediaException ex = assertThrows(CodecMediaException.class,
                () -> Mp3PcmWavConverter.resolveDecoderBackend("decoder=native"));
        assertTrue(ex.getMessage().contains("Unsupported MP3 decoder token"));
        assertTrue(ex.getMessage().contains("decoder=javasound"));
    }

    @Test
    void shouldRejectUnsupportedPresetTokenForMp3Decode() {
        CodecMediaException ex = assertThrows(CodecMediaException.class,
                () -> Mp3PcmWavConverter.resolveDecoderBackend("sr=44100"));
        assertTrue(ex.getMessage().contains("Unsupported preset token for mp3 decode"));
    }

    @Test
    void shouldConvertMp3ToWavViaMp3DecoderRoute() throws Exception {
        CodecMediaEngine engine = CodecMedia.createDefault();
        Path inputMp3 = createTempFileWithResource("c-major-scale_test_audacity.mp3", ".mp3");
        Path outputWav = Files.createTempFile("codecmedia-mp3-decode-", ".wav");

        try {
            var converted = engine.convert(inputMp3, outputWav, new ConversionOptions("wav", "balanced", true));
            assertEquals("wav", converted.format());
            assertTrue(converted.reencoded());

            byte[] out = Files.readAllBytes(outputWav);
            assertTrue(out.length > 44);
            assertTrue(out[0] == 'R' && out[1] == 'I' && out[2] == 'F' && out[3] == 'F');
            assertTrue(out[8] == 'W' && out[9] == 'A' && out[10] == 'V' && out[11] == 'E');
        } catch (CodecMediaException runtimeSupportLimited) {
            assertTrue(runtimeSupportLimited.getMessage().contains("Java Sound")
                    || runtimeSupportLimited.getMessage().contains("MP3 decoder")
                    || runtimeSupportLimited.getMessage().contains("unsupported"));
        } finally {
            Files.deleteIfExists(outputWav);
            Files.deleteIfExists(inputMp3);
        }
    }

    @Test
    void shouldConvertMp3ToPcmViaMp3DecoderRoute() throws Exception {
        CodecMediaEngine engine = CodecMedia.createDefault();
        Path inputMp3 = createTempFileWithResource("c-major-scale_test_web-convert_mono.mp3", ".mp3");
        Path outputPcm = Files.createTempFile("codecmedia-mp3-to-pcm-", ".pcm");

        try {
            var converted = engine.convert(inputMp3, outputPcm, new ConversionOptions("pcm", "balanced", true));
            assertEquals("pcm", converted.format());
            assertTrue(converted.reencoded());

            byte[] out = Files.readAllBytes(outputPcm);
            assertTrue(out.length > 0);
            boolean looksRiff = out.length >= 4 && out[0] == 'R' && out[1] == 'I' && out[2] == 'F' && out[3] == 'F';
            assertTrue(!looksRiff);
        } catch (CodecMediaException runtimeSupportLimited) {
            assertTrue(runtimeSupportLimited.getMessage().contains("Java Sound")
                    || runtimeSupportLimited.getMessage().contains("MP3 decoder")
                    || runtimeSupportLimited.getMessage().contains("unsupported"));
        } finally {
            Files.deleteIfExists(outputPcm);
            Files.deleteIfExists(inputMp3);
        }
    }

    private static Path createTempFileWithResource(String resourceName, String suffix) throws IOException {
        Path temp = Files.createTempFile("codecmedia-mp3-decoder-test-", suffix);
        try (InputStream in = Mp3PcmWavConverterTest.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (in == null) {
                throw new IOException("Missing test resource: " + resourceName);
            }
            Files.write(temp, in.readAllBytes());
        }
        return temp;
    }
}
