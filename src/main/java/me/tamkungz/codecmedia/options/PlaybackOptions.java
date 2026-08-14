package me.tamkungz.codecmedia.options;

public record PlaybackOptions(
        boolean dryRun,
        boolean allowExternalApp
) {

    public static PlaybackOptions defaults() {
        return new PlaybackOptions(false, true);
    }
}

