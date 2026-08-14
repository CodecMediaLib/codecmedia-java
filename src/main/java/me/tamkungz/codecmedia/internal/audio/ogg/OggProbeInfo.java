package me.tamkungz.codecmedia.internal.audio.ogg;

import me.tamkungz.codecmedia.internal.audio.BitrateMode;

public record OggProbeInfo(
        String codec,
        int sampleRate,
        int channels,
        int bitrateKbps,
        BitrateMode bitrateMode,
        long durationMillis
) {
}

