package me.tamkungz.codecmedia.internal.video.mp4;

public record Mp4ProbeInfo(
        Long durationMillis,
        Integer width,
        Integer height,
        String majorBrand,
        String videoCodec,
        String audioCodec,
        Integer sampleRate,
        Integer channels,
        Double frameRate,
        Integer videoBitrateKbps,
        Integer audioBitrateKbps,
        Integer bitDepth,
        String displayAspectRatio
) {
}

