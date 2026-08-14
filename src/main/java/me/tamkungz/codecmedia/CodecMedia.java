package me.tamkungz.codecmedia;

import me.tamkungz.codecmedia.internal.StubCodecMediaEngine;

/**
 * Entry point for creating CodecMedia engine instances.
 */
public final class CodecMedia {

    private CodecMedia() {
    }

    public static CodecMediaEngine createDefault() {
        return new StubCodecMediaEngine();
    }
}
