package me.tamkungz.codecmedia.internal.video.webm;

public record WebmProbeInfo(
        Long durationMillis,
        Integer width,
        Integer height,
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

