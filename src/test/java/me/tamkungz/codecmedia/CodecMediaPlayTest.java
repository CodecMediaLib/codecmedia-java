package me.tamkungz.codecmedia;

import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import me.tamkungz.codecmedia.model.MediaType;

class CodecMediaPlayTest {

    @ParameterizedTest(name = "play fixture probe: {0}")
    @MethodSource("audioFixtures")
    void playFixtureProbe_shouldDetectCoreMediaInfo(
            String fileName,
            String expectedMimeType,
            String expectedExtension,
            MediaType expectedMediaType,
            boolean expectParsedAudioStream
    ) throws Exception {
        CodecMediaEngine engine = CodecMedia.createDefault();
        Path input = Path.of("src/test/resources", fileName);

        var result = engine.probe(input);

        assertNotNull(result);
        assertEquals(input, result.input());
        assertEquals(expectedMimeType, result.mimeType());
        assertEquals(expectedExtension, result.extension());
        assertEquals(expectedMediaType, result.mediaType());
        assertNotNull(result.tags());
        assertTrue(result.tags().containsKey("sizeBytes"));

        if (expectParsedAudioStream) {
            assertNotNull(result.durationMillis());
            assertTrue(result.durationMillis() > 0);
            assertFalse(result.streams().isEmpty());
            assertNotNull(result.streams().get(0).codec());
            assertNotNull(result.streams().get(0).sampleRate());
            assertNotNull(result.streams().get(0).channels());
        } else {
            assertTrue(result.streams().isEmpty());
        }
    }

    private static Stream<Arguments> audioFixtures() {
        return Stream.of(
                Arguments.of("c-major-scale_test_ableton-live.wav", "audio/wav", "wav", MediaType.AUDIO, true),
                Arguments.of("c-major-scale_test_audacity.mp3", "audio/mpeg", "mp3", MediaType.AUDIO, true),
                Arguments.of("c-major-scale_test_ffmpeg.ogg", "audio/ogg", "ogg", MediaType.AUDIO, true),
                Arguments.of("c-major-scale_test_web-convert_mono.mp3", "audio/mpeg", "mp3", MediaType.AUDIO, true)
        );
    }
}
