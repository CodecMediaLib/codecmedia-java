package me.tamkungz.codecmedia.model;

public record StreamInfo(
        int index,
        StreamKind kind,
        String codec,
        Integer bitrateKbps,
        Integer sampleRate,
        Integer channels,
        Integer width,
        Integer height,
        Double frameRate
) {
}
