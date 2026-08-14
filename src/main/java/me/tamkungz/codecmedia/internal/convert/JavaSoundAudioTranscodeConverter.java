package me.tamkungz.codecmedia.internal.convert;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import me.tamkungz.codecmedia.CodecMediaException;
import me.tamkungz.codecmedia.model.ConversionResult;

/**
 * Audio transcoder that uses JDK Java Sound SPI only (zero external dependencies).
 * <p>
 * Supported target formats are constrained to Java Sound built-ins:
 * WAV/AIFF/AU.
 */
public final class JavaSoundAudioTranscodeConverter implements MediaConverter {

    @Override
    public ConversionResult convert(ConversionRequest request) throws CodecMediaException {
        AudioFileFormat.Type targetType = mapTargetType(request.targetExtension());
        if (targetType == null) {
            throw new CodecMediaException(
                    "audio->audio transcoding is not implemented yet (JDK path supports wav/aif/aiff/aifc/au only)"
            );
        }

        Path output = request.output();
        try {
            Path parent = output.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            if (Files.exists(output) && !request.options().overwrite()) {
                throw new CodecMediaException("Output already exists and overwrite is disabled: " + output);
            }

            long written = transcodeWithJavaSound(request.input(), output, targetType);
            if (written <= 0) {
                throw new CodecMediaException("Java Sound wrote zero bytes for target format: " + request.targetExtension());
            }
            return new ConversionResult(output, request.targetExtension(), true);
        } catch (IOException e) {
            throw new CodecMediaException("Failed to convert file: " + request.input(), e);
        }
    }

    private static long transcodeWithJavaSound(Path input, Path output, AudioFileFormat.Type targetType) throws CodecMediaException {
        try (AudioInputStream source = AudioSystem.getAudioInputStream(input.toFile())) {
            if (AudioSystem.isFileTypeSupported(targetType, source)) {
                return AudioSystem.write(source, targetType, output.toFile());
            }

            AudioFormat src = source.getFormat();
            int channels = Math.max(1, src.getChannels());
            float sampleRate = src.getSampleRate() > 0 ? src.getSampleRate() : 44_100f;
            AudioFormat pcm = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    sampleRate,
                    16,
                    channels,
                    channels * 2,
                    sampleRate,
                    false
            );

            try (AudioInputStream decoded = AudioSystem.getAudioInputStream(pcm, source)) {
                if (!AudioSystem.isFileTypeSupported(targetType, decoded)) {
                    throw new CodecMediaException("Target file type is not supported by Java Sound runtime: " + targetType);
                }
                return AudioSystem.write(decoded, targetType, output.toFile());
            }
        } catch (UnsupportedAudioFileException | IllegalArgumentException e) {
            throw new CodecMediaException("Java Sound audio transcoding failed: unsupported source/target combination", e);
        } catch (IOException e) {
            throw new CodecMediaException("Java Sound audio transcoding failed", e);
        }
    }

    private static AudioFileFormat.Type mapTargetType(String extension) {
        if (extension == null) {
            return null;
        }
        return switch (extension.toLowerCase(java.util.Locale.ROOT)) {
            case "wav" -> AudioFileFormat.Type.WAVE;
            case "aif", "aiff", "aifc" -> AudioFileFormat.Type.AIFF;
            case "au" -> AudioFileFormat.Type.AU;
            default -> null;
        };
    }
}

