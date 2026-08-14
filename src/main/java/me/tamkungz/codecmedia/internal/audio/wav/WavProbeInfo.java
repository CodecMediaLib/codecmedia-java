package me.tamkungz.codecmedia.internal.audio.wav;

import me.tamkungz.codecmedia.internal.audio.BitrateMode;

public record WavProbeInfo(
        long durationMillis,
        int bitrateKbps,
        int sampleRate,
        int channels,
        BitrateMode bitrateMode
) {
}

