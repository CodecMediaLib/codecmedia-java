package me.tamkungz.codecmedia.internal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.Test;

import me.tamkungz.codecmedia.CodecMediaException;
import me.tamkungz.codecmedia.internal.convert.ConversionHub;
import me.tamkungz.codecmedia.internal.convert.ConversionRequest;
import me.tamkungz.codecmedia.model.ConversionResult;
import me.tamkungz.codecmedia.model.MediaType;
import me.tamkungz.codecmedia.options.PlaybackOptions;

class StubCodecMediaEnginePlaybackRoutingTest {

    @Test
    void play_dryRunShouldRemainUnchanged() throws Exception {
        Path tempWav = createTempFileWithResource("c-major-scale_test_ableton-live.wav", ".wav");
        StubCodecMediaEngine engine = engineWithBackends(
                input -> fail("Java sampled backend must not be called during dry-run"),
                new StubDesktopBackend(false, false)
        );

        try {
            var result = engine.play(tempWav, new PlaybackOptions(true, false));
            assertTrue(result.started());
            assertEquals("dry-run", result.backend());
            assertEquals(MediaType.AUDIO, result.mediaType());
            assertEquals("Playback simulation successful", result.message());
        } finally {
            Files.deleteIfExists(tempWav);
        }
    }

    @Test
    void play_wavShouldPreferJavaSampledBackend() throws Exception {
        Path tempWav = createTempFileWithResource("c-major-scale_test_ableton-live.wav", ".wav");
        AtomicInteger javaCalls = new AtomicInteger();
        StubDesktopBackend desktop = new StubDesktopBackend(true, false);
        StubCodecMediaEngine engine = engineWithBackends(
                input -> javaCalls.incrementAndGet(),
                desktop
        );

        try {
            var result = engine.play(tempWav, new PlaybackOptions(false, true));
            assertTrue(result.started());
            assertEquals("java-sampled", result.backend());
            assertEquals(1, javaCalls.get());
            assertEquals(0, desktop.openCalls.get());
        } finally {
            Files.deleteIfExists(tempWav);
        }
    }

    @Test
    void play_wavShouldFallbackToDesktopWhenJavaSampledFails() throws Exception {
        Path tempWav = createTempFileWithResource("c-major-scale_test_ableton-live.wav", ".wav");
        AtomicInteger javaCalls = new AtomicInteger();
        StubDesktopBackend desktop = new StubDesktopBackend(true, false);
        StubCodecMediaEngine engine = engineWithBackends(
                input -> {
                    javaCalls.incrementAndGet();
                    throw new CodecMediaException("Simulated sampled-audio failure");
                },
                desktop
        );

        try {
            var result = engine.play(tempWav, new PlaybackOptions(false, true));
            assertTrue(result.started());
            assertEquals("desktop-open", result.backend());
            assertEquals(1, javaCalls.get());
            assertEquals(1, desktop.openCalls.get());
        } finally {
            Files.deleteIfExists(tempWav);
        }
    }

    @Test
    void play_wavShouldFailClearlyWhenJavaSampledFailsAndExternalNotAllowed() throws Exception {
        Path tempWav = createTempFileWithResource("c-major-scale_test_ableton-live.wav", ".wav");
        StubCodecMediaEngine engine = engineWithBackends(
                input -> {
                    throw new CodecMediaException("Simulated sampled-audio failure");
                },
                new StubDesktopBackend(true, false)
        );

        try {
            try {
                engine.play(tempWav, new PlaybackOptions(false, false));
                fail("Expected CodecMediaException");
            } catch (CodecMediaException expected) {
                assertTrue(expected.getMessage().contains("No playback backend available after Java sampled attempt"));
                assertTrue(expected.getMessage().contains("Simulated sampled-audio failure"));
            }
        } finally {
            Files.deleteIfExists(tempWav);
        }
    }

    @Test
    void play_mp3ShouldBypassJavaSampledAndUseDesktopFallback() throws Exception {
        Path tempMp3 = createTempFileWithResource("c-major-scale_test_audacity.mp3", ".mp3");
        AtomicInteger javaCalls = new AtomicInteger();
        StubDesktopBackend desktop = new StubDesktopBackend(true, false);
        StubCodecMediaEngine engine = engineWithBackends(
                input -> javaCalls.incrementAndGet(),
                desktop
        );

        try {
            var result = engine.play(tempMp3, new PlaybackOptions(false, true));
            assertTrue(result.started());
            assertEquals("desktop-open", result.backend());
            assertEquals(0, javaCalls.get());
            assertEquals(1, desktop.openCalls.get());
        } finally {
            Files.deleteIfExists(tempMp3);
        }
    }

    private static StubCodecMediaEngine engineWithBackends(
            StubCodecMediaEngine.JavaSampledPlaybackBackend sampledBackend,
            StubDesktopBackend desktopBackend
    ) {
        ConversionHub noOpConversionHub = new ConversionHub() {
            @Override
            public ConversionResult convert(ConversionRequest request) throws CodecMediaException {
                throw new CodecMediaException("Conversion is not exercised in this playback routing test");
            }
        };
        return new StubCodecMediaEngine(noOpConversionHub, sampledBackend, desktopBackend);
    }

    private static Path createTempFileWithResource(String resourceName, String suffix) throws IOException {
        Path resource = Path.of("src/test/resources", resourceName);
        Path temp = Files.createTempFile("codecmedia-play-routing-", suffix);
        Files.copy(resource, temp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return temp;
    }

    private static final class StubDesktopBackend implements StubCodecMediaEngine.DesktopPlaybackBackend {
        private final boolean supported;
        private final boolean shouldFail;
        private final AtomicInteger openCalls = new AtomicInteger();

        private StubDesktopBackend(boolean supported, boolean shouldFail) {
            this.supported = supported;
            this.shouldFail = shouldFail;
        }

        @Override
        public boolean isSupported() {
            return supported;
        }

        @Override
        public void open(Path input) throws IOException {
            openCalls.incrementAndGet();
            if (shouldFail) {
                throw new IOException("Simulated desktop-open failure");
            }
        }
    }
}
