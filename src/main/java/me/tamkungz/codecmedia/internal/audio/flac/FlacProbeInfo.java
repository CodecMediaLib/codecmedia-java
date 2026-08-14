package me.tamkungz.codecmedia.internal.audio.flac;

import me.tamkungz.codecmedia.internal.audio.BitrateMode;

public record FlacProbeInfo(
        String codec,
        int sampleRate,
        int channels,
        int bitsPerSample,
        int bitrateKbps,
        BitrateMode bitrateMode,
        long durationMillis
) {
}

