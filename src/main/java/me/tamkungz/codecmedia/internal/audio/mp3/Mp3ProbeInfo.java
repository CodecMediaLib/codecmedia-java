package me.tamkungz.codecmedia.internal.audio.mp3;

import me.tamkungz.codecmedia.internal.audio.BitrateMode;

public record Mp3ProbeInfo(
        String codec,
        int sampleRate,
        int channels,
        int bitrateKbps,
        BitrateMode bitrateMode,
        long durationMillis
) {
}

