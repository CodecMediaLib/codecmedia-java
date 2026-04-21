package me.tamkungz.codecmedia.internal.convert;

import me.tamkungz.codecmedia.CodecMediaException;
import me.tamkungz.codecmedia.model.ConversionResult;

public final class DefaultConversionHub implements ConversionHub {

    private final MediaConverter passthroughConverter = new SameFormatCopyConverter();
    private final MediaConverter wavPcmConverter = new WavPcmConverter();
    private final MediaConverter mp3PcmWavConverter = new Mp3PcmWavConverter();
    private final MediaConverter videoToAudioConverter = new UnsupportedRouteConverter(
            "video->audio conversion is not implemented yet (planned conversion hub path)"
    );
    private final MediaConverter mp4MovToM4aRemuxConverter = new Mp4MovToM4aRemuxConverter();
    private final MediaConverter audioToImageConverter = new UnsupportedRouteConverter(
            "audio->image (album cover) conversion is not implemented yet (planned conversion hub path)"
    );
    private final MediaConverter videoToVideoConverter = new UnsupportedRouteConverter(
            "video->video conversion is not implemented yet (planned conversion hub path)"
    );
    private final MediaConverter audioToAudioTranscodeConverter = new JavaSoundAudioTranscodeConverter();
    private final MediaConverter imageToImageTranscodeConverter = new ImageTranscodeConverter();

    @Override
    public ConversionResult convert(ConversionRequest request) throws CodecMediaException {
        if (request.sourceExtension().equals(request.targetExtension())) {
            return passthroughConverter.convert(request);
        }

        ConversionRoute route = ConversionRouteResolver.resolve(request.sourceMediaType(), request.targetMediaType());
        return switch (route) {
            case VIDEO_TO_AUDIO -> convertVideoToAudio(request);
            case AUDIO_TO_IMAGE -> audioToImageConverter.convert(request);
            case VIDEO_TO_VIDEO -> videoToVideoConverter.convert(request);
            case AUDIO_TO_AUDIO -> {
                boolean mp3DecodePair = "mp3".equals(request.sourceExtension())
                        && ("pcm".equals(request.targetExtension()) || "wav".equals(request.targetExtension()));
                if (mp3DecodePair) {
                    yield mp3PcmWavConverter.convert(request);
                }
                boolean wavPcmPair = ("wav".equals(request.sourceExtension()) && "pcm".equals(request.targetExtension()))
                        || ("pcm".equals(request.sourceExtension()) && "wav".equals(request.targetExtension()));
                if (wavPcmPair) {
                    yield wavPcmConverter.convert(request);
                }
                yield audioToAudioTranscodeConverter.convert(request);
            }
            case IMAGE_TO_IMAGE -> imageToImageTranscodeConverter.convert(request);
            case UNSUPPORTED -> throw new CodecMediaException(
                    "Unsupported conversion route: " + request.sourceMediaType() + " -> " + request.targetMediaType()
            );
        };
    }

    private ConversionResult convertVideoToAudio(ConversionRequest request) throws CodecMediaException {
        boolean mp4MovToM4a = "m4a".equals(request.targetExtension())
                && ("mp4".equals(request.sourceExtension()) || "mov".equals(request.sourceExtension()));
        if (mp4MovToM4a) {
            return mp4MovToM4aRemuxConverter.convert(request);
        }
        return videoToAudioConverter.convert(request);
    }
}

