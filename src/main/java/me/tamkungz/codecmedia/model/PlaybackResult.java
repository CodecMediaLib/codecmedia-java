package me.tamkungz.codecmedia.model;

public record PlaybackResult(
        boolean started,
        String backend,
        MediaType mediaType,
        /** Optional diagnostic message; may be {@code null} when no message is produced. */
        String message
) {
}

