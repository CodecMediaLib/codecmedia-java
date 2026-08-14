package me.tamkungz.codecmedia.internal.convert;

import me.tamkungz.codecmedia.model.MediaType;

public final class ConversionRouteResolver {

    private ConversionRouteResolver() {
    }

    public static ConversionRoute resolve(MediaType source, MediaType target) {
        // Intentional: null, UNKNOWN, CONTAINER, and any non-mapped pairs are
        // currently treated as UNSUPPORTED until explicit route handlers exist.
        if (source == null || target == null) {
            return ConversionRoute.UNSUPPORTED;
        }
        if (source == MediaType.AUDIO && target == MediaType.AUDIO) {
            return ConversionRoute.AUDIO_TO_AUDIO;
        }
        if (source == MediaType.AUDIO && target == MediaType.IMAGE) {
            return ConversionRoute.AUDIO_TO_IMAGE;
        }
        if (source == MediaType.VIDEO && target == MediaType.AUDIO) {
            return ConversionRoute.VIDEO_TO_AUDIO;
        }
        if (source == MediaType.VIDEO && target == MediaType.VIDEO) {
            return ConversionRoute.VIDEO_TO_VIDEO;
        }
        if (source == MediaType.IMAGE && target == MediaType.IMAGE) {
            return ConversionRoute.IMAGE_TO_IMAGE;
        }
        return ConversionRoute.UNSUPPORTED;
    }
}

