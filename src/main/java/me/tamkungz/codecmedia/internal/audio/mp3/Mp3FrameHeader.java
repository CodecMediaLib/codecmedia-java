package me.tamkungz.codecmedia.internal.audio.mp3;

record Mp3FrameHeader(
        int versionBits,
        int layerBits,
        int bitrateKbps,
        int sampleRate,
        int channels,
        int frameLength,
        int samplesPerFrame
) {
}

