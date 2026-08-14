package me.tamkungz.codecmedia.internal.audio.mp3;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;

import org.junit.jupiter.api.Test;

import me.tamkungz.codecmedia.CodecMediaException;

class PureJavaMp3DecoderTest {

    @Test
    void shouldFailClosedUntilRealPureJavaLayer3DecoderExists() throws Exception {
        byte[] encoded = readResource("c-major-scale_test_web-convert_mono.mp3");

        CodecMediaException error = assertThrows(
                CodecMediaException.class,
                () -> PureJavaMp3Decoder.decodeToPcm(encoded)
        );

        assertTrue(error.getMessage().contains("not a real MP3 decoder yet"));
        assertTrue(error.getMessage().contains("decoder=javasound"));
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
