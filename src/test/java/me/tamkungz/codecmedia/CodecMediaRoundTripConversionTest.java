package me.tamkungz.codecmedia;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import me.tamkungz.codecmedia.model.MediaType;
import me.tamkungz.codecmedia.options.ConversionOptions;

class CodecMediaRoundTripConversionTest {

    private record FixtureExpectation(
            String fileName,
            String extension,
            String mimeType,
            MediaType mediaType
    ) {
    }

    @ParameterizedTest(name = "round-trip same extension: {0}")
    @MethodSource("exampleFixtures")
    void convert_roundTripSameExtension_shouldWorkForAllExampleFixtures(FixtureExpectation fixture) throws Exception {
        CodecMediaEngine engine = CodecMedia.createDefault();
        Path input = Path.of("src/test/resources/example", fixture.fileName());
        Path workDir = Files.createTempDirectory("codecmedia-roundtrip-");

        try {
            assertTrue(Files.exists(input));

            Path firstOutput = workDir.resolve("step1." + fixture.extension());
            var first = engine.convert(input, firstOutput, new ConversionOptions(fixture.extension(), "balanced", true));

            assertTrue(Files.exists(first.outputFile()));
            assertTrue(Files.size(first.outputFile()) > 0);
            assertEquals(fixture.extension(), first.format());
            assertFalse(first.reencoded());

            Path secondOutput = workDir.resolve("step2." + fixture.extension());
            var second = engine.convert(first.outputFile(), secondOutput, new ConversionOptions(fixture.extension(), "balanced", true));

            assertTrue(Files.exists(second.outputFile()));
            assertTrue(Files.size(second.outputFile()) > 0);
            assertEquals(fixture.extension(), second.format());
            assertFalse(second.reencoded());

            var probed = engine.probe(second.outputFile());
            assertEquals(fixture.mimeType(), probed.mimeType());
            assertEquals(fixture.extension(), probed.extension());
            assertEquals(fixture.mediaType(), probed.mediaType());
        } finally {
            deleteDirectory(workDir);
        }
    }

    private static Stream<FixtureExpectation> exampleFixtures() {
        return Stream.of(
                new FixtureExpectation("file_example_MP3_1MG.mp3", "mp3", "audio/mpeg", MediaType.AUDIO),
                new FixtureExpectation("file_example_MP3_700KB.mp3", "mp3", "audio/mpeg", MediaType.AUDIO),
                new FixtureExpectation("file_example_MP4_480_1_5MG.mp4", "mp4", "video/mp4", MediaType.VIDEO),
                new FixtureExpectation("file_example_MP4_640_3MG.mp4", "mp4", "video/mp4", MediaType.VIDEO),
                new FixtureExpectation("file_example_PNG_1MB.png", "png", "image/png", MediaType.IMAGE),
                new FixtureExpectation("file_example_PNG_500kB.png", "png", "image/png", MediaType.IMAGE),
                new FixtureExpectation("file_example_WEBM_480_900KB.webm", "webm", "video/webm", MediaType.VIDEO),
                new FixtureExpectation("file_example_WEBM_640_1_4MB.webm", "webm", "video/webm", MediaType.VIDEO)
        );
    }

    private static void deleteDirectory(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (var walk = Files.walk(dir)) {
            walk.sorted((a, b) -> b.compareTo(a)).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // ignore cleanup errors in tests
                }
            });
        }
    }
}
