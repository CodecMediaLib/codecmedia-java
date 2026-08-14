package me.tamkungz.codecmedia.internal.image.jpeg;

public record JpegProbeInfo(
        int width,
        int height,
        int bitsPerSample,
        int channels
) {
}

