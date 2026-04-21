package me.tamkungz.codecmedia.internal.audio.mp3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;

import org.junit.jupiter.api.Test;

class PureJavaMp3DecoderTest {

    @Test
    void shouldDecodeMp3IntoExperimentalPcm() throws Exception {
        byte[] encoded = readResource("c-major-scale_test_web-convert_mono.mp3");
        Mp3Decoder.DecodedPcm decoded = PureJavaMp3Decoder.decodeToPcm(encoded);

        assertTrue(decoded.pcmS16Le().length > 0);
        assertTrue(decoded.sampleRate() >= 8_000);
        assertTrue(decoded.channels() >= 1);
        assertEquals(16, decoded.bitsPerSample());
    }

    private static byte[] readResource(String resourceName) throws IOException {
        try (InputStream in = PureJavaMp3DecoderTest.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (in == null) {
                throw new IOException("Missing test resource: " + resourceName);
            }
            return in.readAllBytes();
        }
    }
}
