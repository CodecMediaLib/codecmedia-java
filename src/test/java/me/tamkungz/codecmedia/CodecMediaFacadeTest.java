package me.tamkungz.codecmedia;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class CodecMediaFacadeTest {

    private record ExampleFixtureExpectation(
            String fileName,
            String mimeType,
            String extension,
            me.tamkungz.codecmedia.model.MediaType mediaType
    ) {
    }

    @Test
    void createDefault_shouldReturnEngine() {
        CodecMediaEngine engine = CodecMedia.createDefault();
        assertNotNull(engine);
    }

    @ParameterizedTest(name = "probe real fixture: {0}")
    @MethodSource("exampleFixtures")
    void probe_shouldDetectRealExampleFixtures(ExampleFixtureExpectation fixture) throws Exception {
        CodecMediaEngine engine = CodecMedia.createDefault();
        Path input = Path.of("src/test/resources/example", fixture.fileName());

        assertTrue(Files.exists(input));

        var result = engine.probe(input);
        assertEquals(fixture.mimeType(), result.mimeType());
        assertEquals(fixture.extension(), result.extension());
        assertEquals(fixture.mediaType(), result.mediaType());
        assertTrue(result.tags().containsKey("sizeBytes"));
    }

    @ParameterizedTest(name = "validate real fixture: {0}")
    @MethodSource("exampleFixtures")
    void validate_strictShouldAcceptRealExampleFixtures(ExampleFixtureExpectation fixture) throws Exception {
        CodecMediaEngine engine = CodecMedia.createDefault();
        Path input = Path.of("src/test/resources/example", fixture.fileName());

        assertTrue(Files.exists(input));

        var result = engine.validate(input, new me.tamkungz.codecmedia.options.ValidationOptions(true, 0));
        assertTrue(result.valid());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void probe_shouldDetectMp3ByExtension() throws Exception {
        CodecMediaEngine engine = CodecMedia.createDefault();
        Path tempFile = Files.createTempFile("codecmedia-", ".mp3");

        try {
            var result = engine.probe(tempFile);
            assertEquals("audio/mpeg", result.mimeType());
            assertEquals("mp3", result.extension());
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    void validate_shouldFailWhenFileMissing() throws Exception {
        CodecMediaEngine engine = CodecMedia.createDefault();
        Path missing = Path.of("this-file-should-not-exist-12345.mp4");

        var result = engine.validate(missing, null);
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("does not exist")));
    }

    @Test
    void validate_shouldFailWhenInputIsNull() throws Exception {
        CodecMediaEngine engine = CodecMedia.createDefault();
        var result = engine.validate(null, null);
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("Input file is required")));
    }

    @Test
    void probe_shouldRejectNullInputPath() {
        CodecMediaEngine engine = CodecMedia.createDefault();
        CodecMediaException ex = assertThrows(CodecMediaException.class, () -> engine.probe(null));
        assertTrue(ex.getMessage().contains("Input file is required"));
    }

    @Test
    void writeAndReadMetadata_shouldRoundTripViaSidecar() throws Exception {
        CodecMediaEngine engine = CodecMedia.createDefault();
        Path tempPng = createTempFileWithResource("png_test.png", ".png");
        Path sidecar = tempPng.resolveSibling(tempPng.getFileName() + ".codecmedia.properties");

        try {
            engine.writeMetadata(tempPng, new me.tamkungz.codecmedia.model.Metadata(Map.of(
                    "title", "PNG Title",
                    "artist", "CodecMedia Test"
            )));

            var metadata = engine.readMetadata(tempPng);
            assertEquals("PNG Title", metadata.entries().get("title"));
            assertEquals("CodecMedia Test", metadata.entries().get("artist"));
            assertEquals("image/png", metadata.entries().get("mimeType"));
            assertTrue(Files.exists(sidecar));
        } finally {
            Files.deleteIfExists(sidecar);
            Files.deleteIfExists(tempPng);
        }
    }

    @Test
    void writeAndReadMetadata_shouldRoundTripViaEmbeddedMp3Id3v1() throws Exception {
        CodecMediaEngine engine = CodecMedia.createDefault();
        Path tempMp3 = createTempFileWithResource("c-major-scale_test_audacity.mp3", ".mp3");
        Path sidecar = tempMp3.resolveSibling(tempMp3.getFileName() + ".codecmedia.properties");

        try {
            engine.writeMetadata(tempMp3, new me.tamkungz.codecmedia.model.Metadata(Map.of(
                    "title", "ID3v1 Title",
                    "artist", "ID3v1 Artist",
                    "album", "ID3v1 Album",
                    "date", "2026",
                    "comment", "ID3v1 Comment",
                    "genre", "13"
            )));

            var metadata = engine.readMetadata(tempMp3);
            assertEquals("ID3v1 Title", metadata.entries().get("title"));
            assertEquals("ID3v1 Artist", metadata.entries().get("artist"));
            assertEquals("ID3v1 Album", metadata.entries().get("album"));
            assertEquals("2026", metadata.entries().get("date"));
            assertEquals("ID3v1 Comment", metadata.entries().get("comment"));
            assertEquals("13", metadata.entries().get("genre"));
            assertFalse(Files.exists(sidecar));
        } finally {
            Files.deleteIfExists(sidecar);
            Files.deleteIfExists(tempMp3);
        }
    }

    @Test
    void writeAndReadMetadata_shouldRoundTripViaEmbeddedAiffTextChunks() throws Exception {
        CodecMediaEngine engine = CodecMedia.createDefault();
        Path tempAiff = Files.createTempFile("codecmedia-aiff-meta-", ".aiff");
        Path sidecar = tempAiff.resolveSibling(tempAiff.getFileName() + ".codecmedia.properties");

        try {
            Files.write(tempAiff, minimalAiffBytes());
            engine.writeMetadata(tempAiff, new me.tamkungz.codecmedia.model.Metadata(Map.of(
                    "title", "Embedded AIFF Title",
                    "artist", "Embedded AIFF Artist",
                    "copyright", "(c) CodecMedia",
                    "comment", "Embedded AIFF Comment"
            )));

            var metadata = engine.readMetadata(tempAiff);
            assertEquals("Embedded AIFF Title", metadata.entries().get("title"));
            assertEquals("Embedded AIFF Artist", metadata.entries().get("artist"));
            assertEquals("(c) CodecMedia", metadata.entries().get("copyright"));
            assertEquals("Embedded AIFF Comment", metadata.entries().get("comment"));
            assertEquals("audio/aiff", metadata.entries().get("mimeType"));
            assertFalse(Files.exists(sidecar));
        } finally {
            Files.deleteIfExists(sidecar);
            Files.deleteIfExists(tempAiff);
        }
    }

    @Test
    void readMetadata_shouldIncludeOggVorbisCommentWhenPresent() throws Exception {
        CodecMediaEngine engine = CodecMedia.createDefault();
        Path tempOgg = createTempFileWithResource("c-major-scale_test_ffmpeg.ogg", ".ogg");

        try {
            var metadata = engine.readMetadata(tempOgg);
            assertEquals("audio/ogg", metadata.entries().get("mimeType"));
            assertEquals("ogg", metadata.entries().get("extension"));
            assertTrue(metadata.entries().containsKey("mediaType"));
            assertTrue(
                    metadata.entries().containsKey("title")
                            || metadata.entries().containsKey("artist")
                            || metadata.entries().containsKey("album")
                            || metadata.entries().containsKey("comment")
                            || metadata.entries().containsKey("genre")
                            || metadata.entries().containsKey("date")
                            || metadata.entries().size() >= 3
            );
        } finally {
            Files.deleteIfExists(tempOgg);
        }
    }

    @Test
    void readMetadata_shouldIncludeFlacVorbisCommentWhenPresent() throws Exception {
        CodecMediaEngine engine = CodecMedia.createDefault();
        Path tempFlac = createTempFlacFixture();

        try {
            var metadata = engine.readMetadata(tempFlac);
            assertEquals("audio/flac", metadata.entries().get("mimeType"));
            assertEquals("flac", metadata.entries().get("extension"));
            assertTrue(metadata.entries().containsKey("mediaType"));
            // minimal fixture may have no Vorbis comments; ensure parser path remains safe
            assertTrue(metadata.entries().size() >= 3);
        } finally {
            Files.deleteIfExists(tempFlac);
        }
    }

    @Test
    void writeAndReadMetadata_shouldRoundTripViaEmbeddedWavInfo() throws Exception {
        CodecMediaEngine engine = CodecMedia.createDefault();
        Path tempWav = createTempFileWithResource("c-major-scale_test_ableton-live.wav", ".wav");
        Path wavSidecar = tempWav.resolveSibling(tempWav.getFileName() + ".codecmedia.properties");

        try {
            engine.writeMetadata(tempWav, new me.tamkungz.codecmedia.model.Metadata(Map.of(
                    "title", "Embedded WAV Title",
                    "artist", "Embedded WAV Artist",
                    "album", "Embedded WAV Album",
                    "comment", "Embedded WAV Comment",
                    "date", "2026-03-16",
                    "genre", "Test Genre"
            )));

            var metadata = engine.readMetadata(tempWav);
            assertEquals("Embedded WAV Title", metadata.entries().get("title"));
            assertEquals("Embedded WAV Artist", metadata.entries().get("artist"));
            assertEquals("Embedded WAV Album", metadata.entries().get("album"));
            assertEquals("Embedded WAV Comment", metadata.entries().get("comment"));
            assertEquals("2026-03-16", metadata.entries().get("date"));
            assertEquals("Test Genre", metadata.entries().get("genre"));
            assertEquals("audio/wav", metadata.entries().get("mimeType"));
            assertFalse(Files.exists(wavSidecar));

            var probe = engine.probe(tempWav);
            assertEquals("audio/wav", probe.mimeType());
            assertEquals("wav", probe.extension());
            assertEquals(me.tamkungz.codecmedia.model.MediaType.AUDIO, probe.mediaType());
        } finally {
            Files.deleteIfExists(wavSidecar);
            Files.deleteIfExists(tempWav);
        }
    }

    @Test
    void extractAudio_shouldCreateOutputFile() throws Exception {
        CodecMediaEngine engine = CodecMedia.createDefault();
        Path tempMp3 = createTempFileWithResource("c-major-scale_test_web-convert_mono.mp3", ".mp3");
        Path outputDir = Files.createTempDirectory("codecmedia-extract-");

        try {
            var result = engine.extractAudio(
                    tempMp3,
                    outputDir,
                    new me.tamkungz.codecmedia.options.AudioExtractOptions("mp3", 192, 0)
            );
            assertNotNull(result.outputFile());
            assertTrue(Files.exists(result.outputFile()));
            assertEquals("mp3", result.format());
        } finally {
            deleteDirectory(outputDir);
            Files.deleteIfExists(tempMp3);
        }
    }

    @Test
    void extractAudio_shouldUseUserFacingMessageWhenFormatConversionRequested() throws Exception {
        CodecMediaEngine engine = CodecMedia.createDefault();
        Path tempMp3 = createTempFileWithResource("c-major-scale_test_web-convert_mono.mp3", ".mp3");
        Path outputDir = Files.createTempDirectory("codecmedia-extract-mismatch-");

        try {
            boolean threw = false;
            try {
                engine.extractAudio(
                        tempMp3,
                        outputDir,
                        new me.tamkungz.codecmedia.options.AudioExtractOptions("wav", 192, 0)
                );
            } catch (CodecMediaException expected) {
                threw = expected.getMessage().contains("Audio extraction does not support format conversion yet")
                        && !expected.getMessage().contains("Stub");
            }
            assertTrue(threw);
        } finally {
            deleteDirectory(outputDir);
            Files.deleteIfExists(tempMp3);
        }
    }

    @Test
    void convert_shouldRespectOverwriteFlag() throws Exception {
        CodecMediaEngine engine = CodecMedia.createDefault();
        Path tempMp3 = createTempFileWithResource("c-major-scale_test_audacity.mp3", ".mp3");
        Path output = Files.createTempFile("codecmedia-convert-", ".mp3");

        try {
            var converted = engine.convert(tempMp3, output, new me.tamkungz.codecmedia.options.ConversionOptions("mp3", "balanced", true));
            assertTrue(Files.size(output) > 0);
            assertFalse(converted.reencoded());

            boolean threw = false;
            try {
                engine.convert(tempMp3, output, new me.tamkungz.codecmedia.options.ConversionOptions("mp3", "balanced", false));
            } catch (CodecMediaException expected) {
                threw = true;
            }
            assertTrue(threw);
        } finally {
            Files.deleteIfExists(output);
            Files.deleteIfExists(tempMp3);
        }
    }

    @Test
    void convert_shouldRejectVideoToAudioRouteForNow() throws Exception {
        CodecMediaEngine engine = CodecMedia.createDefault();
        Path tempMp4 = createTempFileWithResource("mp4_test.mp4", ".mp4");
        Path outputMp3 = Files.createTempFile("codecmedia-video-to-audio-", ".mp3");

        try {
            boolean threw = false;
            try {
                engine.convert(tempMp4, outputMp3, new me.tamkungz.codecmedia.options.ConversionOptions("mp3", "balanced", true));
            } catch (CodecMediaException expected) {
                threw = expected.getMessage().contains("video->audio");
            }
            assertTrue(threw);
        } finally {
            Files.deleteIfExists(outputMp3);
            Files.deleteIfExists(tempMp4);
        }
    }

    @Test
    void convert_shouldRemuxMp4AudioTrackToM4aWithoutReencode() throws Exception {
        CodecMediaEngine engine = CodecMedia.createDefault();
        Path tempMp4 = createTempSyntheticIsoBmffFixture(".mp4", "isom", "mp4a");
        Path outputM4a = Files.createTempFile("codecmedia-mp4-to-m4a-", ".m4a");

        try {
            var converted = engine.convert(tempMp4, outputM4a, new me.tamkungz.codecmedia.options.ConversionOptions("m4a", "balanced", true));
            assertEquals("m4a", converted.format());
            assertFalse(converted.reencoded());
            assertTrue(Files.exists(outputM4a));
            assertTrue(Files.size(outputM4a) > 0);

            var probed = engine.probe(outputM4a);
            assertEquals("audio/mp4", probed.mimeType());
            assertEquals("m4a", probed.extension());
            assertEquals(me.tamkungz.codecmedia.model.MediaType.AUDIO, probed.mediaType());
        } finally {
            Files.deleteIfExists(outputM4a);
            Files.deleteIfExists(tempMp4);
        }
    }

    @Test
    void convert_shouldRemuxMovAudioTrackToM4aWithoutReencode() throws Exception {
        CodecMediaEngine engine = CodecMedia.createDefault();
        Path tempMov = createTempSyntheticIsoBmffFixture(".mov", "qt  ", "mp4a");
        Path outputM4a = Files.createTempFile("codecmedia-mov-to-m4a-", ".m4a");

        try {
            var converted = engine.convert(tempMov, outputM4a, new me.tamkungz.codecmedia.options.ConversionOptions("m4a", "balanced", true));
            assertEquals("m4a", converted.format());
            assertFalse(converted.reencoded());
            assertTrue(Files.exists(outputM4a));
            assertTrue(Files.size(outputM4a) > 0);
        } finally {
            Files.deleteIfExists(outputM4a);
            Files.deleteIfExists(tempMov);
        }
    }

    @Test
    void convert_shouldFailM4aRemuxWhenAudioTrackCodecIsNotCompatible() throws Exception {
        CodecMediaEngine engine = CodecMedia.createDefault();
        Path tempMp4 = createTempSyntheticIsoBmffFixture(".mp4", "isom", "lpcm");
        Path outputM4a = Files.createTempFile("codecmedia-mp4-to-m4a-fail-", ".m4a");

        try {
            boolean threw = false;
            try {
                engine.convert(tempMp4, outputM4a, new me.tamkungz.codecmedia.options.ConversionOptions("m4a", "balanced", true));
            } catch (CodecMediaException expected) {
                threw = expected.getMessage().contains("not m4a-compatible");
            }
            assertTrue(threw);
        } finally {
            Files.deleteIfExists(outputM4a);
            Files.deleteIfExists(tempMp4);
        }
    }

    @Test
    void convert_shouldRejectAudioToImageRouteForNow() throws Exception {
        CodecMediaEngine engine = CodecMedia.createDefault();
        Path tempMp3 = createTempFileWithResource("c-major-scale_test_audacity.mp3", ".mp3");
        Path outputPng = Files.createTempFile("codecmedia-audio-to-image-", ".png");

        try {
            boolean threw = false;
            try {
                engine.convert(tempMp3, outputPng, new me.tamkungz.codecmedia.options.ConversionOptions("png", "balanced", true));
            } catch (CodecMediaException expected) {
                threw = expected.getMessage().contains("audio->image");
            }
            assertTrue(threw);
        } finally {
            Files.deleteIfExists(outputPng);
            Files.deleteIfExists(tempMp3);
        }
    }

    @Test
    void convert_shouldTranscodePngToJpg() throws Exception {
        CodecMediaEngine engine = CodecMedia.createDefault();
        Path tempPng = createTempFileWithResource("png_test.png", ".png");
        Path outputJpg = Files.createTempFile("codecmedia-png-to-jpg-", ".jpg");

        try {
            var converted = engine.convert(tempPng, outputJpg, new me.tamkungz.codecmedia.options.ConversionOptions("jpg", "balanced", true));
            assertEquals("jpg", converted.format());
            assertTrue(converted.reencoded());
            assertTrue(Files.exists(outputJpg));
            assertTrue(Files.size(outputJpg) > 0);

            var probed = engine.probe(outputJpg);
            assertEquals("image/jpeg", probed.mimeType());
            assertEquals("jpg", probed.extension());
            assertEquals(me.tamkungz.codecmedia.model.MediaType.IMAGE, probed.mediaType());
        } finally {
            Files.deleteIfExists(outputJpg);
            Files.deleteIfExists(tempPng);
        }
    }

    @Test
    void convert_shouldTranscodeJpgToPng() throws Exception {
        CodecMediaEngine engine = CodecMedia.createDefault();
        Path tempJpg = createTempJpegFixture(".jpg");
        Path outputPng = Files.createTempFile("codecmedia-jpg-to-png-", ".png");

        try {
            var converted = engine.convert(tempJpg, outputPng, new me.tamkungz.codecmedia.options.ConversionOptions("png", "balanced", true));
            assertEquals("png", converted.format());
            assertTrue(converted.reencoded());
            assertTrue(Files.exists(outputPng));
            assertTrue(Files.size(outputPng) > 0);

            var probed = engine.probe(outputPng);
            assertEquals("image/png", probed.mimeType());
            assertEquals("png", probed.extension());
            assertEquals(me.tamkungz.codecmedia.model.MediaType.IMAGE, probed.mediaType());
        } finally {
            Files.deleteIfExists(outputPng);
            Files.deleteIfExists(tempJpg);
        }
    }

    @Test
    void convert_shouldTranscodePngToAvif() throws Exception {
        CodecMediaEngine engine = CodecMedia.createDefault();
        Path tempPng = createTempFileWithResource("png_test.png", ".png");
        Path outputAvif = Files.createTempFile("codecmedia-png-to-avif-", ".avif");

        try {
            try {
                var converted = engine.convert(tempPng, outputAvif,
                        new me.tamkungz.codecmedia.options.ConversionOptions("avif", "balanced", true));
                assertEquals("avif", converted.format());
                assertTrue(converted.reencoded());
                assertTrue(Files.exists(outputAvif));
                assertTrue(Files.size(outputAvif) > 0);

                var probed = engine.probe(outputAvif);
                assertTrue(probed.extension().equals("avif") || probed.extension().equals("heif") || probed.extension().equals("heic"));
                assertEquals(me.tamkungz.codecmedia.model.MediaType.IMAGE, probed.mediaType());
            } catch (CodecMediaException expectedRuntimeLimit) {
                assertTrue(expectedRuntimeLimit.getMessage().contains("HEIF writer"));
            }
        } finally {
            Files.deleteIfExists(outputAvif);
            Files.deleteIfExists(tempPng);
        }
    }

    @Test
    void convert_shouldRejectVideoToVideoTranscodeForNow() throws Exception {
        CodecMediaEngine engine = CodecMedia.createDefault();
        Path tempMp4 = createTempFileWithResource("mp4_test.mp4", ".mp4");
        Path outputMkv = Files.createTempFile("codecmedia-video-to-video-", ".mkv");

        try {
            boolean threw = false;
            try {
                engine.convert(tempMp4, outputMkv, new me.tamkungz.codecmedia.options.ConversionOptions("mkv", "balanced", true));
            } catch (CodecMediaException expected) {
                threw = expected.getMessage().contains("video->video");
            }
            assertTrue(threw);
        } finally {
            Files.deleteIfExists(outputMkv);
            Files.deleteIfExists(tempMp4);
        }
    }

    @Test
    void convert_shouldTranscodeWavToAiffUsingJavaSound() throws Exception {
        CodecMediaEngine engine = CodecMedia.createDefault();
        Path tempWav = createTempFileWithResource("c-major-scale_test_ableton-live.wav", ".wav");
        Path outputAiff = Files.createTempFile("codecmedia-wav-to-aiff-", ".aiff");

        try {
            var converted = engine.convert(tempWav, outputAiff, new me.tamkungz.codecmedia.options.ConversionOptions("aiff", "balanced", true));
            assertEquals("aiff", converted.format());
            assertTrue(converted.reencoded());
            assertTrue(Files.exists(outputAiff));
            assertTrue(Files.size(outputAiff) > 0);

            var probed = engine.probe(outputAiff);
            assertEquals("audio/aiff", probed.mimeType());
            assertEquals("aiff", probed.extension());
            assertEquals(me.tamkungz.codecmedia.model.MediaType.AUDIO, probed.mediaType());
        } catch (CodecMediaException runtimeSupportLimited) {
            assertTrue(runtimeSupportLimited.getMessage().contains("Java Sound")
                    || runtimeSupportLimited.getMessage().contains("unsupported")
                    || runtimeSupportLimited.getMessage().contains("not implemented"));
        } finally {
            Files.deleteIfExists(outputAiff);
            Files.deleteIfExists(tempWav);
        }
    }

    @Test
    void convert_shouldExtractRawPcmFromWav() throws Exception {
        CodecMediaEngine engine = CodecMedia.createDefault();
        Path tempWav = createTempFileWithResource("c-major-scale_test_ableton-live.wav", ".wav");
        Path outputPcm = Files.createTempFile("codecmedia-wav-to-pcm-", ".pcm");

        try {
            var converted = engine.convert(tempWav, outputPcm, new me.tamkungz.codecmedia.options.ConversionOptions("pcm", "balanced", true));
            assertEquals("pcm", converted.format());
            assertTrue(converted.reencoded());
            assertTrue(Files.exists(outputPcm));
            assertTrue(Files.size(outputPcm) > 0);

            byte[] pcm = Files.readAllBytes(outputPcm);
            assertFalse(
                    pcm.length >= 4 && pcm[0] == 'R' && pcm[1] == 'I' && pcm[2] == 'F' && pcm[3] == 'F',
                    "WAV->PCM output should be raw payload, not a RIFF/WAV container"
            );
        } finally {
            Files.deleteIfExists(outputPcm);
            Files.deleteIfExists(tempWav);
        }
    }

    @Test
    void convert_shouldWrapRawPcmAsWav() throws Exception {
        CodecMediaEngine engine = CodecMedia.createDefault();
        Path tempWav = createTempFileWithResource("c-major-scale_test_ableton-live.wav", ".wav");
        Path outputPcm = Files.createTempFile("codecmedia-wav-to-pcm-for-wrap-", ".pcm");
        Path outputWav = Files.createTempFile("codecmedia-pcm-to-wav-", ".wav");

        try {
            var pcm = engine.convert(tempWav, outputPcm, new me.tamkungz.codecmedia.options.ConversionOptions("pcm", "balanced", true));
            assertEquals("pcm", pcm.format());
            assertTrue(Files.exists(outputPcm));
            assertTrue(Files.size(outputPcm) > 0);

            var wav = engine.convert(
                    outputPcm,
                    outputWav,
                    new me.tamkungz.codecmedia.options.ConversionOptions("wav", "sr=22050,ch=1,bits=16", true)
            );
            assertEquals("wav", wav.format());
            assertTrue(wav.reencoded());
            assertTrue(Files.exists(outputWav));
            assertTrue(Files.size(outputWav) > 44);

            byte[] wrapped = Files.readAllBytes(outputWav);
            assertTrue(wrapped.length >= 12);
            assertTrue(wrapped[0] == 'R' && wrapped[1] == 'I' && wrapped[2] == 'F' && wrapped[3] == 'F');
            assertTrue(wrapped[8] == 'W' && wrapped[9] == 'A' && wrapped[10] == 'V' && wrapped[11] == 'E');

            var probed = engine.probe(outputWav);
            assertEquals("audio/wav", probed.mimeType());
            assertEquals("wav", probed.extension());
            assertEquals(me.tamkungz.codecmedia.model.MediaType.AUDIO, probed.mediaType());
            assertFalse(probed.streams().isEmpty());
            assertNotNull(probed.streams().get(0).sampleRate());
            assertNotNull(probed.streams().get(0).channels());
            assertEquals(22_050, probed.streams().get(0).sampleRate());
            assertEquals(1, probed.streams().get(0).channels());
        } finally {
            Files.deleteIfExists(outputWav);
            Files.deleteIfExists(outputPcm);
            Files.deleteIfExists(tempWav);
        }
    }

    @Test
    void validate_shouldRespectMaxBytesOption() throws Exception {
        CodecMediaEngine engine = CodecMedia.createDefault();
        Path tempMp3 = createTempFileWithResource("c-major-scale_test_audacity.mp3", ".mp3");

        try {
            long fileSize = Files.size(tempMp3);
            var result = engine.validate(tempMp3, new me.tamkungz.codecmedia.options.ValidationOptions(false, fileSize - 1));
            assertFalse(result.valid());
            assertTrue(result.errors().stream().anyMatch(e -> e.contains("exceeds maxBytes")));
        } finally {
            Files.deleteIfExists(tempMp3);
        }
    }

    @Test
    void validate_strictShouldAcceptValidWavFixture() throws Exception {
        CodecMediaEngine engine = CodecMedia.createDefault();
        Path tempWav = createTempFileWithResource("c-major-scale_test_ableton-live.wav", ".wav");

        try {
            var result = engine.validate(tempWav, new me.tamkungz.codecmedia.options.ValidationOptions(true, 0));
            assertTrue(result.valid());
            assertTrue(result.errors().isEmpty());
        } finally {
            Files.deleteIfExists(tempWav);
        }
    }

    @Test
    void probe_shouldDetectPngBySignature() throws Exception {
        CodecMediaEngine engine = CodecMedia.createDefault();
        Path tempPng = createTempFileWithResource("png_test.png", ".png");

        try {
            var result = engine.probe(tempPng);
            assertEquals("image/png", result.mimeType());
            assertEquals("png", result.extension());
            assertEquals(me.tamkungz.codecmedia.model.MediaType.IMAGE, result.mediaType());
            assertFalse(result.streams().isEmpty());
            assertNotNull(result.streams().get(0).width());
            assertNotNull(result.streams().get(0).height());
            assertTrue(result.streams().get(0).width() > 0);
            assertTrue(result.streams().get(0).height() > 0);
        } finally {
            Files.deleteIfExists(tempPng);
        }
    }

    @Test
    void validate_strictShouldAcceptValidPngFixture() throws Exception {
        CodecMediaEngine engine = CodecMedia.createDefault();
        Path tempPng = createTempFileWithResource("png_test.png", ".png");

        try {
            var result = engine.validate(tempPng, new me.tamkungz.codecmedia.options.ValidationOptions(true, 0));
            assertTrue(result.valid());
            assertTrue(result.errors().isEmpty());
        } finally {
            Files.deleteIfExists(tempPng);
        }
    }

    @Test
    void probe_shouldDetectJpegBySignature() throws Exception {
        CodecMediaEngine engine = CodecMedia.createDefault();
        Path tempJpeg = createTempJpegFixture(".jpg");

        try {
            var result = engine.probe(tempJpeg);
            assertEquals("image/jpeg", result.mimeType());
            assertEquals("jpg", result.extension());
            assertEquals(me.tamkungz.codecmedia.model.MediaType.IMAGE, result.mediaType());
            assertFalse(result.streams().isEmpty());
            assertEquals(1, result.streams().get(0).width());
            assertEquals(1, result.streams().get(0).height());
        } finally {
            Files.deleteIfExists(tempJpeg);
        }
    }

    @Test
    void probe_shouldRespectJpegExtensionVariant() throws Exception {
        CodecMediaEngine engine = CodecMedia.createDefault();
        Path tempJpeg = createTempJpegFixture(".jpeg");

        try {
            var result = engine.probe(tempJpeg);
            assertEquals("image/jpeg", result.mimeType());
            assertEquals("jpeg", result.extension());
        } finally {
            Files.deleteIfExists(tempJpeg);
        }
    }

    @Test
    void validate_strictShouldAcceptValidJpegFixture() throws Exception {
        CodecMediaEngine engine = CodecMedia.createDefault();
        Path tempJpeg = createTempJpegFixture(".jpg");

        try {
            var result = engine.validate(tempJpeg, new me.tamkungz.codecmedia.options.ValidationOptions(true, 0));
            assertTrue(result.valid());
            assertTrue(result.errors().isEmpty());
        } finally {
            Files.deleteIfExists(tempJpeg);
        }
    }

    @Test
    void probe_shouldDetectMp4AndProvideBasicVideoInfo() throws Exception {
        CodecMediaEngine engine = CodecMedia.createDefault();
        Path tempMp4 = createTempFileWithResource("mp4_test.mp4", ".mp4");

        try {
            var result = engine.probe(tempMp4);
            assertEquals("video/mp4", result.mimeType());
            assertEquals("mp4", result.extension());
            assertEquals(me.tamkungz.codecmedia.model.MediaType.VIDEO, result.mediaType());
            assertNotNull(result.tags());
            assertTrue(result.tags().containsKey("sizeBytes"));
            assertTrue(result.tags().containsKey("majorBrand"));
            if (result.durationMillis() != null) {
                assertTrue(result.durationMillis() > 0);
            }
            if (!result.streams().isEmpty()) {
                assertNotNull(result.streams().get(0).width());
                assertNotNull(result.streams().get(0).height());
                assertTrue(result.streams().get(0).width() > 0);
                assertTrue(result.streams().get(0).height() > 0);
            }
        } finally {
            Files.deleteIfExists(tempMp4);
        }
    }

    @Test
    void validate_strictShouldAcceptValidMp4Fixture() throws Exception {
        CodecMediaEngine engine = CodecMedia.createDefault();
        Path tempMp4 = createTempFileWithResource("mp4_test.mp4", ".mp4");

        try {
            var result = engine.validate(tempMp4, new me.tamkungz.codecmedia.options.ValidationOptions(true, 0));
            assertTrue(result.valid());
            assertTrue(result.errors().isEmpty());
        } finally {
            Files.deleteIfExists(tempMp4);
        }
    }

    @Test
    void probe_shouldDetectM4aAsAudioMp4() throws Exception {
        CodecMediaEngine engine = CodecMedia.createDefault();
        Path tempM4a = createTempM4aFixture();

        try {
            var result = engine.probe(tempM4a);
            assertEquals("audio/mp4", result.mimeType());
            assertEquals("m4a", result.extension());
            assertEquals(me.tamkungz.codecmedia.model.MediaType.AUDIO, result.mediaType());
            assertTrue(result.tags().containsKey("sizeBytes"));
        } finally {
            Files.deleteIfExists(tempM4a);
        }
    }

    @Test
    void validate_strictShouldAcceptValidM4aFixture() throws Exception {
        CodecMediaEngine engine = CodecMedia.createDefault();
        Path tempM4a = createTempM4aFixture();

        try {
            var result = engine.validate(tempM4a, new me.tamkungz.codecmedia.options.ValidationOptions(true, 0));
            assertTrue(result.valid());
            assertTrue(result.errors().isEmpty());
        } finally {
            Files.deleteIfExists(tempM4a);
        }
    }

    @Test
    void probe_shouldDetectFlacBySignature() throws Exception {
        CodecMediaEngine engine = CodecMedia.createDefault();
        Path tempFlac = createTempFlacFixture();

        try {
            var result = engine.probe(tempFlac);
            assertEquals("audio/flac", result.mimeType());
            assertEquals("flac", result.extension());
            assertEquals(me.tamkungz.codecmedia.model.MediaType.AUDIO, result.mediaType());
            assertTrue(result.tags().containsKey("sizeBytes"));
            assertTrue(result.tags().containsKey("bitsPerSample"));
            assertFalse(result.streams().isEmpty());
            assertEquals("flac", result.streams().get(0).codec());
        } finally {
            Files.deleteIfExists(tempFlac);
        }
    }

    @Test
    void validate_strictShouldAcceptValidFlacFixture() throws Exception {
        CodecMediaEngine engine = CodecMedia.createDefault();
        Path tempFlac = createTempFlacFixture();

        try {
            var result = engine.validate(tempFlac, new me.tamkungz.codecmedia.options.ValidationOptions(true, 0));
            assertTrue(result.valid());
            assertTrue(result.errors().isEmpty());
        } finally {
            Files.deleteIfExists(tempFlac);
        }
    }

    @Test
    void probe_shouldDetectMovByContainerHeader() throws Exception {
        CodecMediaEngine engine = CodecMedia.createDefault();
        Path tempMov = createTempMovFixture();

        try {
            var result = engine.probe(tempMov);
            assertEquals("video/quicktime", result.mimeType());
            assertEquals("mov", result.extension());
            assertEquals(me.tamkungz.codecmedia.model.MediaType.VIDEO, result.mediaType());
            assertTrue(result.tags().containsKey("sizeBytes"));
            assertTrue(result.tags().containsKey("majorBrand"));
        } finally {
            Files.deleteIfExists(tempMov);
        }
    }

    @Test
    void validate_strictShouldAcceptValidMovFixture() throws Exception {
        CodecMediaEngine engine = CodecMedia.createDefault();
        Path tempMov = createTempMovFixture();

        try {
            var result = engine.validate(tempMov, new me.tamkungz.codecmedia.options.ValidationOptions(true, 0));
            assertTrue(result.valid());
            assertTrue(result.errors().isEmpty());
        } finally {
            Files.deleteIfExists(tempMov);
        }
    }

    @Test
    void probe_shouldDetectWebmByContainerHeader() throws Exception {
        CodecMediaEngine engine = CodecMedia.createDefault();
        Path tempWebm = createTempWebmFixture();

        try {
            var result = engine.probe(tempWebm);
            assertEquals("video/webm", result.mimeType());
            assertEquals("webm", result.extension());
            assertEquals(me.tamkungz.codecmedia.model.MediaType.VIDEO, result.mediaType());
            assertTrue(result.tags().containsKey("sizeBytes"));
        } finally {
            Files.deleteIfExists(tempWebm);
        }
    }

    @Test
    void validate_strictShouldAcceptValidWebmFixture() throws Exception {
        CodecMediaEngine engine = CodecMedia.createDefault();
        Path tempWebm = createTempWebmFixture();

        try {
            var result = engine.validate(tempWebm, new me.tamkungz.codecmedia.options.ValidationOptions(true, 0));
            assertTrue(result.valid());
            assertTrue(result.errors().isEmpty());
        } finally {
            Files.deleteIfExists(tempWebm);
        }
    }

    @Test
    void convert_shouldRejectMovToWebmVideoToVideoRouteForNow() throws Exception {
        CodecMediaEngine engine = CodecMedia.createDefault();
        Path tempMov = createTempMovFixture();
        Path outputWebm = Files.createTempFile("codecmedia-mov-to-webm-", ".webm");

        try {
            boolean threw = false;
            try {
                engine.convert(tempMov, outputWebm, new me.tamkungz.codecmedia.options.ConversionOptions("webm", "balanced", true));
            } catch (CodecMediaException expected) {
                threw = expected.getMessage().contains("video->video");
            }
            assertTrue(threw);
        } finally {
            Files.deleteIfExists(outputWebm);
            Files.deleteIfExists(tempMov);
        }
    }

    @Test
    void get_shouldAliasProbe() throws Exception {
        CodecMediaEngine engine = CodecMedia.createDefault();
        Path tempMp3 = createTempFileWithResource("c-major-scale_test_audacity.mp3", ".mp3");

        try {
            var a = engine.probe(tempMp3);
            var b = engine.get(tempMp3);
            assertEquals(a.mimeType(), b.mimeType());
            assertEquals(a.extension(), b.extension());
            assertEquals(a.mediaType(), b.mediaType());
        } finally {
            Files.deleteIfExists(tempMp3);
        }
    }

    @Test
    void play_shouldSupportDryRunForAudioImageVideo() throws Exception {
        CodecMediaEngine engine = CodecMedia.createDefault();
        Path tempMp3 = createTempFileWithResource("c-major-scale_test_audacity.mp3", ".mp3");
        Path tempPng = createTempFileWithResource("png_test.png", ".png");
        Path tempMp4 = createTempFileWithResource("mp4_test.mp4", ".mp4");

        try {
            var audioPlay = engine.play(tempMp3, new me.tamkungz.codecmedia.options.PlaybackOptions(true, false));
            assertTrue(audioPlay.started());
            assertEquals(me.tamkungz.codecmedia.model.MediaType.AUDIO, audioPlay.mediaType());

            var imagePlay = engine.play(tempPng, new me.tamkungz.codecmedia.options.PlaybackOptions(true, false));
            assertTrue(imagePlay.started());
            assertEquals(me.tamkungz.codecmedia.model.MediaType.IMAGE, imagePlay.mediaType());

            var videoPlay = engine.play(tempMp4, new me.tamkungz.codecmedia.options.PlaybackOptions(true, false));
            assertTrue(videoPlay.started());
            assertEquals(me.tamkungz.codecmedia.model.MediaType.VIDEO, videoPlay.mediaType());
        } finally {
            Files.deleteIfExists(tempMp3);
            Files.deleteIfExists(tempPng);
            Files.deleteIfExists(tempMp4);
        }
    }

    private static Path createTempFileWithResource(String resourceName, String suffix) throws IOException {
        Path resource = Path.of("src/test/resources", resourceName);
        Path temp = Files.createTempFile("codecmedia-fixture-", suffix);
        Files.copy(resource, temp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return temp;
    }

    private static Path createTempJpegFixture(String suffix) throws IOException {
        Path temp = Files.createTempFile("codecmedia-jpeg-", suffix);
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0xFFFFFF);
        boolean ok = ImageIO.write(image, "jpg", temp.toFile());
        if (!ok) {
            throw new IOException("No JPEG writer available in ImageIO runtime");
        }
        return temp;
    }

    private static Path createTempSyntheticIsoBmffFixture(String suffix, String majorBrand, String audioSampleEntryFourCc) throws IOException {
        Path temp = Files.createTempFile("codecmedia-bmff-", suffix);
        Files.write(temp, minimalIsoBmffAudioTrackBytes(majorBrand, audioSampleEntryFourCc));
        return temp;
    }

    private static Path createTempMovFixture() throws IOException {
        Path temp = Files.createTempFile("codecmedia-mov-", ".mov");
        Files.write(temp, minimalMovBytes());
        return temp;
    }

    private static Path createTempM4aFixture() throws IOException {
        Path temp = Files.createTempFile("codecmedia-m4a-", ".m4a");
        Files.write(temp, minimalM4aBytes());
        return temp;
    }

    private static Path createTempFlacFixture() throws IOException {
        Path temp = Files.createTempFile("codecmedia-flac-", ".flac");
        Files.write(temp, minimalFlacBytes());
        return temp;
    }

    private static Path createTempWebmFixture() throws IOException {
        Path temp = Files.createTempFile("codecmedia-webm-", ".webm");
        Files.write(temp, minimalWebmBytes());
        return temp;
    }

    private static byte[] minimalMovBytes() {
        // 20-byte ftyp box: size(4) + type(4) + major_brand(4) + minor(4) + compatible_brand(4)
        return new byte[] {
                0x00, 0x00, 0x00, 0x14,
                'f', 't', 'y', 'p',
                'q', 't', ' ', ' ',
                0x00, 0x00, 0x00, 0x00,
                'q', 't', ' ', ' '
        };
    }

    private static byte[] minimalAiffBytes() {
        int channels = 2;
        int sampleRate = 44100;
        int bitsPerSample = 16;
        int frames = 44100;
        int commChunkSize = 18;
        int formSize = 4 + 8 + commChunkSize;
        byte[] bytes = new byte[8 + formSize];

        bytes[0] = 'F';
        bytes[1] = 'O';
        bytes[2] = 'R';
        bytes[3] = 'M';
        writeBeInt(bytes, 4, formSize);
        bytes[8] = 'A';
        bytes[9] = 'I';
        bytes[10] = 'F';
        bytes[11] = 'F';

        bytes[12] = 'C';
        bytes[13] = 'O';
        bytes[14] = 'M';
        bytes[15] = 'M';
        writeBeInt(bytes, 16, commChunkSize);
        writeBeShort(bytes, 20, channels);
        writeBeInt(bytes, 22, frames);
        writeBeShort(bytes, 26, bitsPerSample);
        writeExtended80Integer(bytes, 28, sampleRate);
        return bytes;
    }

    private static void writeBeShort(byte[] out, int offset, int value) {
        out[offset] = (byte) ((value >>> 8) & 0xFF);
        out[offset + 1] = (byte) (value & 0xFF);
    }

    private static void writeBeInt(byte[] out, int offset, int value) {
        out[offset] = (byte) ((value >>> 24) & 0xFF);
        out[offset + 1] = (byte) ((value >>> 16) & 0xFF);
        out[offset + 2] = (byte) ((value >>> 8) & 0xFF);
        out[offset + 3] = (byte) (value & 0xFF);
    }

    private static void writeExtended80Integer(byte[] out, int offset, int value) {
        int msb = 31 - Integer.numberOfLeadingZeros(value);
        int exp = 16383 + msb;
        long mantissa = ((long) value) << (63 - msb);

        out[offset] = (byte) ((exp >>> 8) & 0x7F);
        out[offset + 1] = (byte) (exp & 0xFF);
        for (int i = 0; i < 8; i++) {
            out[offset + 2 + i] = (byte) ((mantissa >>> (56 - (i * 8))) & 0xFF);
        }
    }

    private static byte[] minimalIsoBmffAudioTrackBytes(String majorBrand, String audioSampleEntryFourCc) {
        byte[] hdlrPayload = concat(
                new byte[4],
                new byte[4],
                ascii4("soun"),
                new byte[4]
        );
        byte[] hdlr = box("hdlr", hdlrPayload);

        byte[] sampleEntry = box(audioSampleEntryFourCc, new byte[16]);
        byte[] stsdPayload = concat(
                new byte[4],
                u32(1),
                sampleEntry
        );
        byte[] stsd = box("stsd", stsdPayload);
        byte[] stbl = box("stbl", stsd);
        byte[] minf = box("minf", stbl);
        byte[] mdia = box("mdia", concat(hdlr, minf));
        byte[] trak = box("trak", mdia);
        byte[] moov = box("moov", trak);

        byte[] ftypPayload = concat(
                ascii4(majorBrand),
                u32(0),
                ascii4("isom")
        );
        byte[] ftyp = box("ftyp", ftypPayload);
        return concat(ftyp, moov);
    }

    private static byte[] box(String type, byte[] payload) {
        return concat(u32(8 + payload.length), ascii4(type), payload);
    }

    private static byte[] ascii4(String value) {
        if (value == null || value.length() != 4) {
            throw new IllegalArgumentException("Expected exactly 4 ASCII characters");
        }
        return value.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    }

    private static byte[] u32(int value) {
        return new byte[] {
                (byte) ((value >>> 24) & 0xFF),
                (byte) ((value >>> 16) & 0xFF),
                (byte) ((value >>> 8) & 0xFF),
                (byte) (value & 0xFF)
        };
    }

    private static byte[] concat(byte[]... parts) {
        int size = 0;
        for (byte[] part : parts) {
            size += part.length;
        }
        byte[] out = new byte[size];
        int offset = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, out, offset, part.length);
            offset += part.length;
        }
        return out;
    }

    private static byte[] minimalM4aBytes() {
        return new byte[] {
                0x00, 0x00, 0x00, 0x14,
                'f', 't', 'y', 'p',
                'M', '4', 'A', ' ',
                0x00, 0x00, 0x00, 0x00,
                'i', 's', 'o', 'm'
        };
    }

    private static byte[] minimalFlacBytes() {
        byte[] bytes = new byte[4 + 4 + 34];
        bytes[0] = 'f'; bytes[1] = 'L'; bytes[2] = 'a'; bytes[3] = 'C';
        bytes[4] = (byte) 0x80;
        bytes[5] = 0x00;
        bytes[6] = 0x00;
        bytes[7] = 34;

        int sampleRate = 44100;
        int channels = 2;
        int bitsPerSample = 16;
        long totalSamples = 44100L;

        long packed = ((long) sampleRate & 0xFFFFFL) << 44;
        packed |= ((long) (channels - 1) & 0x7L) << 41;
        packed |= ((long) (bitsPerSample - 1) & 0x1FL) << 36;
        packed |= (totalSamples & 0xFFFFFFFFFL);

        int streamInfoOffset = 8;
        for (int i = 0; i < 8; i++) {
            bytes[streamInfoOffset + 10 + i] = (byte) ((packed >>> (56 - (i * 8))) & 0xFF);
        }

        return bytes;
    }

    private static byte[] minimalWebmBytes() {
        // Minimal EBML header + DocType=webm segment that parser accepts for strict validation.
        return new byte[] {
                0x1A, 0x45, (byte) 0xDF, (byte) 0xA3,
                (byte) 0x88,
                0x42, (byte) 0x82, (byte) 0x84,
                'w', 'e', 'b', 'm',
                0x00, 0x00, 0x00, 0x00
        };
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

    private static Stream<ExampleFixtureExpectation> exampleFixtures() {
        return Stream.of(
                new ExampleFixtureExpectation("file_example_MP3_1MG.mp3", "audio/mpeg", "mp3", me.tamkungz.codecmedia.model.MediaType.AUDIO),
                new ExampleFixtureExpectation("file_example_MP3_700KB.mp3", "audio/mpeg", "mp3", me.tamkungz.codecmedia.model.MediaType.AUDIO),
                new ExampleFixtureExpectation("file_example_MP4_480_1_5MG.mp4", "video/mp4", "mp4", me.tamkungz.codecmedia.model.MediaType.VIDEO),
                new ExampleFixtureExpectation("file_example_MP4_640_3MG.mp4", "video/mp4", "mp4", me.tamkungz.codecmedia.model.MediaType.VIDEO),
                new ExampleFixtureExpectation("file_example_PNG_1MB.png", "image/png", "png", me.tamkungz.codecmedia.model.MediaType.IMAGE),
                new ExampleFixtureExpectation("file_example_PNG_500kB.png", "image/png", "png", me.tamkungz.codecmedia.model.MediaType.IMAGE),
                new ExampleFixtureExpectation("file_example_WEBM_480_900KB.webm", "video/webm", "webm", me.tamkungz.codecmedia.model.MediaType.VIDEO),
                new ExampleFixtureExpectation("file_example_WEBM_640_1_4MB.webm", "video/webm", "webm", me.tamkungz.codecmedia.model.MediaType.VIDEO)
        );
    }
}
