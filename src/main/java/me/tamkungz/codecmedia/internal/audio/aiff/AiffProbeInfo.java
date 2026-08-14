package me.tamkungz.codecmedia.internal.audio.aiff;

import me.tamkungz.codecmedia.internal.audio.BitrateMode;

public record AiffProbeInfo(
        long durationMillis,
        int bitrateKbps,
        int sampleRate,
        int channels,
        BitrateMode bitrateMode
) {
}

