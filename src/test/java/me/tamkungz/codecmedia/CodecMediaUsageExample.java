package me.tamkungz.codecmedia;

import java.nio.file.Path;
import java.util.Map;

import me.tamkungz.codecmedia.model.Metadata;
import me.tamkungz.codecmedia.options.ConversionOptions;
import me.tamkungz.codecmedia.options.ValidationOptions;

/**
 * Minimal runnable example for common CodecMedia flows.
 *
 * Run this class from your IDE after adjusting input/output paths.
 */
public final class CodecMediaUsageExample {

    private CodecMediaUsageExample() {
    }

    public static void main(String[] args) throws Exception {
        CodecMediaEngine engine = CodecMedia.createDefault();

        imageFlow(engine);
        mp3ToOggForOpenAlFlow(engine);
    }

    private static void imageFlow(CodecMediaEngine engine) throws Exception {
        Path input = Path.of("src/test/resources/png_test.png");
        Path output = Path.of("target/example-output.jpg");

        // 1) Probe
        var probe = engine.probe(input);
        System.out.println("Probe mime=" + probe.mimeType() + ", ext=" + probe.extension() + ", type=" + probe.mediaType());

        // 2) Convert image -> image
        var converted = engine.convert(input, output, new ConversionOptions("jpg", "balanced", true));
        System.out.println("Converted format=" + converted.format() + ", reencoded=" + converted.reencoded() + ", output=" + converted.outputFile());

        // 3) Validate output (strict)
        var validation = engine.validate(output, new ValidationOptions(true, 64L * 1024L * 1024L));
        System.out.println("Validation valid=" + validation.valid() + ", errors=" + validation.errors());

        // 4) Metadata read/write sidecar example
        engine.writeMetadata(output, new Metadata(Map.of("title", "Example", "author", "CodecMedia")));
        var metadata = engine.readMetadata(output);
        System.out.println("Metadata entries=" + metadata.entries());
    }

    /**
     * Example for your case: you want OGG for OpenAL but input file is MP3.
     *
     * Current stub engine does NOT support MP3->OGG transcoding yet.
     * This method shows how to detect that and apply an external transcode step.
     */
    private static void mp3ToOggForOpenAlFlow(CodecMediaEngine engine) throws Exception {
        Path mp3Input = Path.of("src/test/resources/c-major-scale_test_audacity.mp3");
        Path oggOutput = Path.of("target/openal-input.ogg");

        try {
            // This currently throws CodecMediaException (audio->audio transcoding not implemented).
            engine.convert(mp3Input, oggOutput, new ConversionOptions("ogg", "balanced", true));
        } catch (CodecMediaException ex) {
            System.out.println("CodecMedia cannot transcode MP3->OGG yet: " + ex.getMessage());

            // Workaround: external transcode (e.g. ffmpeg), then load resulting OGG into your OpenAL pipeline.
            // Windows cmd example:
            //   ffmpeg -y -i src/test/resources/c-major-scale_test_audacity.mp3 target/openal-input.ogg
            System.out.println("Run external transcode command, then feed target/openal-input.ogg to OpenAL decoder.");
        }
    }
}
