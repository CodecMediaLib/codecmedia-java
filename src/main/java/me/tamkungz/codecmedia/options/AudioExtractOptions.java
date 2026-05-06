package me.tamkungz.codecmedia.options;

public record AudioExtractOptions(
        String targetFormat,
        Integer bitrateKbps,
        Integer streamIndex
) {

    public static AudioExtractOptions defaults() {
        return new AudioExtractOptions(null, 192, 0);
    }

    public static AudioExtractOptions defaults(String targetFormat) {
        String effective = (targetFormat == null || targetFormat.isBlank()) ? "m4a" : targetFormat;
        return new AudioExtractOptions(effective, 192, 0);
    }
}
