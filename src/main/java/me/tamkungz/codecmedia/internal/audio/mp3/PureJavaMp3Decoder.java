package me.tamkungz.codecmedia.internal.audio.mp3;

import me.tamkungz.codecmedia.CodecMediaException;

/**
 * Placeholder for a future true pure-Java MPEG Layer III decoder.
 * <p>
 * Older versions synthesized a 440 Hz PCM timeline from MP3 frame cadence.
 * That output was not decoded source audio, so this backend now fails closed
 * instead of returning misleading/corrupted media.
 */
public final class PureJavaMp3Decoder {

    private PureJavaMp3Decoder() {
    }

    public static Mp3Decoder.DecodedPcm decodeToPcm(byte[] encodedMp3Data) throws CodecMediaException {
        if (encodedMp3Data == null || encodedMp3Data.length == 0) {
            throw new CodecMediaException("MP3 data is empty");
        }
        throw new CodecMediaException(
                "decoder=pure-java/layer3 is not a real MP3 decoder yet; use decoder=javasound"
        );
    }
}
