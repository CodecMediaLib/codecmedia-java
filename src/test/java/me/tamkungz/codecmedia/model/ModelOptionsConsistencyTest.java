package me.tamkungz.codecmedia.model;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import me.tamkungz.codecmedia.options.ConversionOptions;
import me.tamkungz.codecmedia.options.ValidationOptions;

class ModelOptionsConsistencyTest {

    @Test
    void primaryCodecShouldBeEmptyWhenNoStreams() {
        ProbeResult probe = new ProbeResult(
                Path.of("image.png"),
                "image/png",
                "png",
                MediaType.IMAGE,
                null,
                List.of(),
                Map.of()
        );

        assertTrue(probe.primaryCodec().isEmpty());
    }

    @Test
    void primaryCodecShouldExposeFirstStreamCodec() {
        StreamInfo first = new StreamInfo(0, StreamKind.AUDIO, "mp3", 128, 44100, 2, null, null, null);
        StreamInfo second = new StreamInfo(1, StreamKind.VIDEO, "h264", 1000, null, null, 1920, 1080, 30.0);
        ProbeResult probe = new ProbeResult(
                Path.of("sample.mp4"),
                "video/mp4",
                "mp4",
                MediaType.VIDEO,
                1000L,
                List.of(first, second),
                Map.of()
        );

        assertTrue(probe.primaryCodec().isPresent());
        assertEquals("mp3", probe.primaryCodec().orElseThrow());
    }

    @Test
    void conversionDefaultsShouldUseFallbackTargetFormat() {
        ConversionOptions defaults = ConversionOptions.defaults();
        ConversionOptions blank = ConversionOptions.defaults("  ");
        ConversionOptions nulled = ConversionOptions.defaults(null);

        assertEquals("m4a", defaults.targetFormat());
        assertEquals("m4a", blank.targetFormat());
        assertEquals("m4a", nulled.targetFormat());
        assertEquals("balanced", defaults.preset());
        assertFalse(defaults.overwrite());
    }

    @Test
    void validationDefaultsShouldUseDocumentedLimit() {
        ValidationOptions defaults = ValidationOptions.defaults();

        assertFalse(defaults.strict());
        assertEquals(500L * 1024 * 1024, defaults.maxBytes());
    }
}
