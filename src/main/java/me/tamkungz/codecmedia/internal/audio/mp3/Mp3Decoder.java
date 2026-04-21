package me.tamkungz.codecmedia.internal.audio.mp3;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import me.tamkungz.codecmedia.CodecMediaException;

/**
 * MP3 decoder that decodes into PCM signed 16-bit little-endian samples.
 * <p>
 * The implementation relies on Java Sound SPI available at runtime.
 */
public final class Mp3Decoder {

    private Mp3Decoder() {
    }

    public static DecodedPcm decodeToPcm(byte[] encodedMp3Data) throws CodecMediaException {
        if (encodedMp3Data == null || encodedMp3Data.length == 0) {
            throw new CodecMediaException("MP3 data is empty");
        }

        try (AudioInputStream source = AudioSystem.getAudioInputStream(new ByteArrayInputStream(encodedMp3Data))) {
            AudioFormat sourceFormat = source.getFormat();
            int channels = Math.max(1, sourceFormat.getChannels());
            float sampleRate = sourceFormat.getSampleRate() > 0 ? sourceFormat.getSampleRate() : 44_100f;
            AudioFormat pcmFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    sampleRate,
                    16,
                    channels,
                    channels * 2,
                    sampleRate,
                    false
            );

            try (AudioInputStream pcmStream = AudioSystem.getAudioInputStream(pcmFormat, source)) {
                byte[] pcmBytes = readAll(pcmStream);
                return new DecodedPcm(pcmBytes, (int) sampleRate, channels, 16);
            } catch (IllegalArgumentException e) {
                throw new CodecMediaException("Runtime does not provide MP3->PCM conversion via Java Sound", e);
            }
        } catch (UnsupportedAudioFileException e) {
            throw new CodecMediaException("Runtime does not provide an MP3 decoder via Java Sound", e);
        } catch (IOException e) {
            throw new CodecMediaException("Failed to decode MP3 stream", e);
        }
    }

    private static byte[] readAll(AudioInputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            if (read > 0) {
                out.write(buffer, 0, read);
            }
        }
        return out.toByteArray();
    }

    public record DecodedPcm(byte[] pcmS16Le, int sampleRate, int channels, int bitsPerSample) {
    }
}
