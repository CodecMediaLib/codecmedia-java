package me.tamkungz.codecmedia.internal.image.heif;

public record HeifProbeInfo(
        String majorBrand,
        Integer width,
        Integer height,
        Integer bitDepth
) {
}

