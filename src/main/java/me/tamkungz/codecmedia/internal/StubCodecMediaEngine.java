package me.tamkungz.codecmedia.internal;

import java.awt.Desktop;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;

import me.tamkungz.codecmedia.CodecMediaEngine;
import me.tamkungz.codecmedia.CodecMediaException;
import me.tamkungz.codecmedia.internal.audio.aiff.AiffCodec;
import me.tamkungz.codecmedia.internal.audio.aiff.AiffParser;
import me.tamkungz.codecmedia.internal.audio.aiff.AiffProbeInfo;
import me.tamkungz.codecmedia.internal.audio.flac.FlacCodec;
import me.tamkungz.codecmedia.internal.audio.flac.FlacParser;
import me.tamkungz.codecmedia.internal.audio.flac.FlacProbeInfo;
import me.tamkungz.codecmedia.internal.audio.mp3.Mp3Codec;
import me.tamkungz.codecmedia.internal.audio.mp3.Mp3Id3v1Tag;
import me.tamkungz.codecmedia.internal.audio.mp3.Mp3Parser;
import me.tamkungz.codecmedia.internal.audio.mp3.Mp3ProbeInfo;
import me.tamkungz.codecmedia.internal.audio.ogg.OggCodec;
import me.tamkungz.codecmedia.internal.audio.ogg.OggParser;
import me.tamkungz.codecmedia.internal.audio.ogg.OggProbeInfo;
import me.tamkungz.codecmedia.internal.audio.wav.WavCodec;
import me.tamkungz.codecmedia.internal.audio.wav.WavParser;
import me.tamkungz.codecmedia.internal.audio.wav.WavProbeInfo;
import me.tamkungz.codecmedia.internal.convert.ConversionHub;
import me.tamkungz.codecmedia.internal.convert.ConversionRequest;
import me.tamkungz.codecmedia.internal.convert.DefaultConversionHub;
import me.tamkungz.codecmedia.internal.image.bmp.BmpParser;
import me.tamkungz.codecmedia.internal.image.bmp.BmpProbeInfo;
import me.tamkungz.codecmedia.internal.image.heif.HeifParser;
import me.tamkungz.codecmedia.internal.image.heif.HeifProbeInfo;
import me.tamkungz.codecmedia.internal.image.jpeg.JpegParser;
import me.tamkungz.codecmedia.internal.image.jpeg.JpegProbeInfo;
import me.tamkungz.codecmedia.internal.image.png.PngParser;
import me.tamkungz.codecmedia.internal.image.png.PngProbeInfo;
import me.tamkungz.codecmedia.internal.image.tiff.TiffParser;
import me.tamkungz.codecmedia.internal.image.tiff.TiffProbeInfo;
import me.tamkungz.codecmedia.internal.image.webp.WebpParser;
import me.tamkungz.codecmedia.internal.image.webp.WebpProbeInfo;
import me.tamkungz.codecmedia.internal.video.mov.MovCodec;
import me.tamkungz.codecmedia.internal.video.mov.MovParser;
import me.tamkungz.codecmedia.internal.video.mov.MovProbeInfo;
import me.tamkungz.codecmedia.internal.video.mp4.Mp4Codec;
import me.tamkungz.codecmedia.internal.video.mp4.Mp4Parser;
import me.tamkungz.codecmedia.internal.video.mp4.Mp4ProbeInfo;
import me.tamkungz.codecmedia.internal.video.webm.WebmCodec;
import me.tamkungz.codecmedia.internal.video.webm.WebmParser;
import me.tamkungz.codecmedia.internal.video.webm.WebmProbeInfo;
import me.tamkungz.codecmedia.model.ConversionResult;
import me.tamkungz.codecmedia.model.ExtractionResult;
import me.tamkungz.codecmedia.model.MediaType;
import me.tamkungz.codecmedia.model.Metadata;
import me.tamkungz.codecmedia.model.PlaybackResult;
import me.tamkungz.codecmedia.model.ProbeResult;
import me.tamkungz.codecmedia.model.StreamInfo;
import me.tamkungz.codecmedia.model.StreamKind;
import me.tamkungz.codecmedia.model.ValidationResult;
import me.tamkungz.codecmedia.options.AudioExtractOptions;
import me.tamkungz.codecmedia.options.ConversionOptions;
import me.tamkungz.codecmedia.options.PlaybackOptions;
import me.tamkungz.codecmedia.options.ValidationOptions;

/**
 * Temporary stub implementation to bootstrap API integration.
 */
public final class StubCodecMediaEngine implements CodecMediaEngine {

    private static final long STRICT_VALIDATION_MAX_BYTES = 32L * 1024L * 1024L;
    private static final int PROBE_PREFIX_BYTES = 128 * 1024;
    private final ConversionHub conversionHub;
    private final JavaSampledPlaybackBackend javaSampledPlaybackBackend;
    private final DesktopPlaybackBackend desktopPlaybackBackend;

    public StubCodecMediaEngine() {
        this(new DefaultConversionHub(), new JdkJavaSampledPlaybackBackend(), new AwtDesktopPlaybackBackend());
    }

    StubCodecMediaEngine(
            ConversionHub conversionHub,
            JavaSampledPlaybackBackend javaSampledPlaybackBackend,
            DesktopPlaybackBackend desktopPlaybackBackend
    ) {
        this.conversionHub = conversionHub;
        this.javaSampledPlaybackBackend = javaSampledPlaybackBackend;
        this.desktopPlaybackBackend = desktopPlaybackBackend;
    }

    @Override
    public ProbeResult get(Path input) throws CodecMediaException {
        return probe(input);
    }

    @Override
    public ProbeResult probe(Path input) throws CodecMediaException {
        ensureExists(input);
        String extension = extractExtension(input);

        try {
            long size = Files.size(input);
            byte[] prefix = readProbePrefix(input);
            boolean likelyMp3 = "mp3".equals(extension) || isLikelyMp3(prefix);
            boolean likelyOgg = "ogg".equals(extension) || isLikelyOgg(prefix);
            boolean likelyWav = "wav".equals(extension) || WavParser.isLikelyWav(prefix);
            boolean likelyAiff = "aif".equals(extension) || "aiff".equals(extension) || "aifc".equals(extension) || AiffParser.isLikelyAiff(prefix);
            boolean likelyFlac = "flac".equals(extension) || FlacParser.isLikelyFlac(prefix);
            boolean likelyPng = "png".equals(extension) || PngParser.isLikelyPng(prefix);
            boolean likelyJpeg = "jpg".equals(extension) || "jpeg".equals(extension) || JpegParser.isLikelyJpeg(prefix);
            boolean likelyWebp = "webp".equals(extension) || WebpParser.isLikelyWebp(prefix);
            boolean likelyBmp = "bmp".equals(extension) || BmpParser.isLikelyBmp(prefix);
            boolean likelyTiff = "tif".equals(extension) || "tiff".equals(extension) || TiffParser.isLikelyTiff(prefix);
            boolean likelyHeif = "heic".equals(extension) || "heif".equals(extension) || "avif".equals(extension) || HeifParser.isLikelyHeif(prefix);
            boolean likelyMov = "mov".equals(extension) || MovParser.isLikelyMov(prefix);
            boolean likelyMp4 = "mp4".equals(extension) || "m4a".equals(extension) || Mp4Parser.isLikelyMp4(prefix);
            boolean likelyWebm = "webm".equals(extension) || WebmParser.isLikelyWebm(prefix);

            if (!(likelyMp3 || likelyOgg || likelyWav || likelyAiff || likelyFlac || likelyPng || likelyJpeg || likelyWebp || likelyBmp || likelyTiff || likelyHeif || likelyMov || likelyMp4 || likelyWebm)) {
                return new ProbeResult(input, mimeTypeByExtension(extension), extension, mediaTypeByExtension(extension), null, List.of(), Map.of("sizeBytes", String.valueOf(size)));
            }

            byte[] bytes = size <= prefix.length ? prefix : Files.readAllBytes(input);

            if (likelyMp3) {
                if (bytes.length >= 4) {
                    try {
                        Mp3ProbeInfo info = Mp3Codec.decode(bytes, input);
                        return new ProbeResult(
                                input,
                                "audio/mpeg",
                                "mp3",
                                MediaType.AUDIO,
                                info.durationMillis(),
                                List.of(new StreamInfo(0, StreamKind.AUDIO, info.codec(), info.bitrateKbps(), info.sampleRate(), info.channels(), null, null, null)),
                                Map.of(
                                        "sizeBytes", String.valueOf(size),
                                        "bitrateMode", info.bitrateMode().name()
                                )
                        );
                    } catch (CodecMediaException ignored) {
                        // Fall back to extension-only probe for partial/empty temporary files.
                    }
                }
                return new ProbeResult(input, "audio/mpeg", "mp3", MediaType.AUDIO, null, List.of(), Map.of("sizeBytes", String.valueOf(size)));
            }

            if (likelyOgg) {
                OggProbeInfo info = OggCodec.decode(bytes, input);
                return new ProbeResult(
                        input,
                        "audio/ogg",
                        "ogg",
                        MediaType.AUDIO,
                        info.durationMillis(),
                        List.of(new StreamInfo(0, StreamKind.AUDIO, info.codec(), info.bitrateKbps(), info.sampleRate(), info.channels(), null, null, null)),
                        Map.of(
                                "sizeBytes", String.valueOf(size),
                                "bitrateMode", info.bitrateMode().name()
                        )
                );
            }

            if (likelyWav) {
                try {
                    WavProbeInfo info = WavCodec.decode(bytes, input);
                    return new ProbeResult(
                            input,
                            "audio/wav",
                            "wav",
                            MediaType.AUDIO,
                            info.durationMillis(),
                            List.of(new StreamInfo(0, StreamKind.AUDIO, "pcm", info.bitrateKbps(), info.sampleRate(), info.channels(), null, null, null)),
                            Map.of("sizeBytes", String.valueOf(size), "bitrateMode", info.bitrateMode().name())
                    );
                } catch (CodecMediaException ignored) {
                    // Fall back to extension-only probe for malformed/partial files.
                }
                return new ProbeResult(input, "audio/wav", "wav", MediaType.AUDIO, null, List.of(), Map.of("sizeBytes", String.valueOf(size)));
            }

            if (likelyAiff) {
                try {
                    AiffProbeInfo info = AiffCodec.decode(bytes, input);
                    String outputExt = "aif".equals(extension) ? "aif" : ("aifc".equals(extension) ? "aifc" : "aiff");
                    return new ProbeResult(
                            input,
                            "audio/aiff",
                            outputExt,
                            MediaType.AUDIO,
                            info.durationMillis(),
                            List.of(new StreamInfo(0, StreamKind.AUDIO, "pcm", info.bitrateKbps(), info.sampleRate(), info.channels(), null, null, null)),
                            Map.of("sizeBytes", String.valueOf(size), "bitrateMode", info.bitrateMode().name())
                    );
                } catch (CodecMediaException ignored) {
                    // Fall back to extension-only probe for malformed/partial files.
                }
                String outputExt = "aif".equals(extension) ? "aif" : ("aifc".equals(extension) ? "aifc" : "aiff");
                return new ProbeResult(input, "audio/aiff", outputExt, MediaType.AUDIO, null, List.of(), Map.of("sizeBytes", String.valueOf(size)));
            }

            if (likelyFlac) {
                try {
                    FlacProbeInfo info = FlacCodec.decode(bytes, input);
                    return new ProbeResult(
                            input,
                            "audio/flac",
                            "flac",
                            MediaType.AUDIO,
                            info.durationMillis(),
                            List.of(new StreamInfo(0, StreamKind.AUDIO, info.codec(), info.bitrateKbps(), info.sampleRate(), info.channels(), null, null, null)),
                            Map.of(
                                    "sizeBytes", String.valueOf(size),
                                    "bitrateMode", info.bitrateMode().name(),
                                    "bitsPerSample", String.valueOf(info.bitsPerSample())
                            )
                    );
                } catch (CodecMediaException ignored) {
                    // Fall back to extension-only probe for malformed/partial files.
                }
                return new ProbeResult(input, "audio/flac", "flac", MediaType.AUDIO, null, List.of(), Map.of("sizeBytes", String.valueOf(size)));
            }

            if (likelyPng) {
                try {
                    PngProbeInfo info = PngParser.parse(bytes);
                    return new ProbeResult(
                            input,
                            "image/png",
                            "png",
                            MediaType.IMAGE,
                            null,
                            List.of(new StreamInfo(0, StreamKind.VIDEO, "png", null, null, null, info.width(), info.height(), null)),
                            Map.of(
                                    "sizeBytes", String.valueOf(size),
                                    "bitDepth", String.valueOf(info.bitDepth()),
                                    "colorType", String.valueOf(info.colorType())
                            )
                    );
                } catch (CodecMediaException ignored) {
                    // Fall back to extension-only probe for malformed/partial files.
                }
                return new ProbeResult(input, "image/png", "png", MediaType.IMAGE, null, List.of(), Map.of("sizeBytes", String.valueOf(size)));
            }

            if (likelyJpeg) {
                String outputExt = "jpeg".equals(extension) ? "jpeg" : "jpg";
                try {
                    JpegProbeInfo info = JpegParser.parse(bytes);
                    return new ProbeResult(
                            input,
                            "image/jpeg",
                            outputExt,
                            MediaType.IMAGE,
                            null,
                            List.of(new StreamInfo(0, StreamKind.VIDEO, "jpeg", null, null, null, info.width(), info.height(), null)),
                            Map.of(
                                    "sizeBytes", String.valueOf(size),
                                    "bitsPerSample", String.valueOf(info.bitsPerSample()),
                                    "channels", String.valueOf(info.channels())
                            )
                    );
                } catch (CodecMediaException ignored) {
                    // Fall back to extension-only probe for malformed/partial files.
                }
                return new ProbeResult(input, "image/jpeg", outputExt, MediaType.IMAGE, null, List.of(), Map.of("sizeBytes", String.valueOf(size)));
            }

            if (likelyWebp) {
                try {
                    WebpProbeInfo info = WebpParser.parse(bytes);
                    java.util.LinkedHashMap<String, String> tags = new java.util.LinkedHashMap<>();
                    tags.put("sizeBytes", String.valueOf(size));
                    if (info.bitDepth() != null) {
                        tags.put("bitDepth", String.valueOf(info.bitDepth()));
                    }
                    return new ProbeResult(
                            input,
                            "image/webp",
                            "webp",
                            MediaType.IMAGE,
                            null,
                            List.of(new StreamInfo(0, StreamKind.VIDEO, "webp", null, null, null, info.width(), info.height(), null)),
                            tags
                    );
                } catch (CodecMediaException ignored) {
                    // Fall back to extension-only probe for malformed/partial files.
                }
                return new ProbeResult(input, "image/webp", "webp", MediaType.IMAGE, null, List.of(), Map.of("sizeBytes", String.valueOf(size)));
            }

            if (likelyBmp) {
                try {
                    BmpProbeInfo info = BmpParser.parse(bytes);
                    return new ProbeResult(
                            input,
                            "image/bmp",
                            "bmp",
                            MediaType.IMAGE,
                            null,
                            List.of(new StreamInfo(0, StreamKind.VIDEO, "bmp", null, null, null, info.width(), info.height(), null)),
                            Map.of(
                                    "sizeBytes", String.valueOf(size),
                                    "bitsPerPixel", String.valueOf(info.bitsPerPixel())
                            )
                    );
                } catch (CodecMediaException ignored) {
                    // Fall back to extension-only probe for malformed/partial files.
                }
                return new ProbeResult(input, "image/bmp", "bmp", MediaType.IMAGE, null, List.of(), Map.of("sizeBytes", String.valueOf(size)));
            }

            if (likelyTiff) {
                String outputExt = "tiff".equals(extension) ? "tiff" : "tif";
                try {
                    TiffProbeInfo info = TiffParser.parse(bytes);
                    java.util.LinkedHashMap<String, String> tags = new java.util.LinkedHashMap<>();
                    tags.put("sizeBytes", String.valueOf(size));
                    if (info.bitDepth() != null) {
                        tags.put("bitDepth", String.valueOf(info.bitDepth()));
                    }
                    return new ProbeResult(
                            input,
                            "image/tiff",
                            outputExt,
                            MediaType.IMAGE,
                            null,
                            List.of(new StreamInfo(0, StreamKind.VIDEO, "tiff", null, null, null, info.width(), info.height(), null)),
                            tags
                    );
                } catch (CodecMediaException ignored) {
                    // Fall back to extension-only probe for malformed/partial files.
                }
                return new ProbeResult(input, "image/tiff", outputExt, MediaType.IMAGE, null, List.of(), Map.of("sizeBytes", String.valueOf(size)));
            }

            if (likelyHeif) {
                String outputExt = "heif".equals(extension) ? "heif" : "heic";
                if ("avif".equals(extension)) {
                    outputExt = "avif";
                }
                String mimeType = "image/" + outputExt;
                try {
                    HeifProbeInfo info = HeifParser.parse(bytes);
                    String majorBrand = info.majorBrand();
                    if ("avif".equals(majorBrand) || "avis".equals(majorBrand)) {
                        outputExt = "avif";
                        mimeType = "image/avif";
                    }
                    java.util.List<StreamInfo> streams = List.of();
                    if (info.width() != null && info.height() != null) {
                        streams = List.of(new StreamInfo(0, StreamKind.VIDEO, outputExt, null, null, null, info.width(), info.height(), null));
                    }
                    java.util.LinkedHashMap<String, String> tags = new java.util.LinkedHashMap<>();
                    tags.put("sizeBytes", String.valueOf(size));
                    tags.put("majorBrand", majorBrand);
                    if (info.bitDepth() != null) {
                        tags.put("bitDepth", String.valueOf(info.bitDepth()));
                    }
                    return new ProbeResult(
                            input,
                            mimeType,
                            outputExt,
                            MediaType.IMAGE,
                            null,
                            streams,
                            tags
                    );
                } catch (CodecMediaException ignored) {
                    // Fall back to extension-only probe for malformed/partial files.
                }
                return new ProbeResult(input, mimeType, outputExt, MediaType.IMAGE, null, List.of(), Map.of("sizeBytes", String.valueOf(size)));
            }

            if (likelyMov) {
                try {
                    MovProbeInfo info = MovCodec.decode(bytes, input);
                    java.util.LinkedHashMap<String, String> tags = new java.util.LinkedHashMap<>();
                    tags.put("sizeBytes", String.valueOf(size));
                    if (info.majorBrand() != null && !info.majorBrand().isBlank()) {
                        tags.put("majorBrand", info.majorBrand());
                    }
                    if (info.videoCodec() != null && !info.videoCodec().isBlank()) {
                        tags.put("videoCodec", info.videoCodec());
                    }
                    if (info.audioCodec() != null && !info.audioCodec().isBlank()) {
                        tags.put("audioCodec", info.audioCodec());
                    }
                    if (info.displayAspectRatio() != null && !info.displayAspectRatio().isBlank()) {
                        tags.put("displayAspectRatio", info.displayAspectRatio());
                    }
                    if (info.bitDepth() != null && info.bitDepth() > 0) {
                        tags.put("bitDepth", String.valueOf(info.bitDepth()));
                    }
                    if (info.videoBitrateKbps() != null && info.videoBitrateKbps() > 0) {
                        tags.put("videoBitrateKbps", String.valueOf(info.videoBitrateKbps()));
                    }
                    if (info.audioBitrateKbps() != null && info.audioBitrateKbps() > 0) {
                        tags.put("audioBitrateKbps", String.valueOf(info.audioBitrateKbps()));
                    }

                    java.util.ArrayList<StreamInfo> streams = new java.util.ArrayList<>();
                    if (info.width() != null && info.height() != null && info.width() > 0 && info.height() > 0) {
                        streams.add(new StreamInfo(0, StreamKind.VIDEO, info.videoCodec() != null ? info.videoCodec() : "unknown", info.videoBitrateKbps(), null, null, info.width(), info.height(), info.frameRate()));
                    }
                    if (info.sampleRate() != null && info.channels() != null && info.sampleRate() > 0 && info.channels() > 0) {
                        streams.add(new StreamInfo(
                                streams.size(),
                                StreamKind.AUDIO,
                                info.audioCodec() != null ? info.audioCodec() : "unknown",
                                info.audioBitrateKbps(),
                                info.sampleRate(),
                                info.channels(),
                                null,
                                null,
                                null
                        ));
                    }

                    return new ProbeResult(
                            input,
                            "video/quicktime",
                            "mov",
                            MediaType.VIDEO,
                            info.durationMillis(),
                            List.copyOf(streams),
                            tags
                    );
                } catch (CodecMediaException ignored) {
                    // Fall back to extension-only probe for malformed/partial files.
                }
                return new ProbeResult(input, "video/quicktime", "mov", MediaType.VIDEO, null, List.of(), Map.of("sizeBytes", String.valueOf(size)));
            }

            if (likelyMp4) {
                String outputExt = "m4a".equals(extension) ? "m4a" : "mp4";
                String mimeType = "m4a".equals(outputExt) ? "audio/mp4" : "video/mp4";
                MediaType mediaType = "m4a".equals(outputExt) ? MediaType.AUDIO : MediaType.VIDEO;
                try {
                    Mp4ProbeInfo info = Mp4Codec.decode(bytes, input);
                    java.util.LinkedHashMap<String, String> tags = new java.util.LinkedHashMap<>();
                    tags.put("sizeBytes", String.valueOf(size));
                    if (info.majorBrand() != null && !info.majorBrand().isBlank()) {
                        tags.put("majorBrand", info.majorBrand());
                    }
                    if (info.videoCodec() != null && !info.videoCodec().isBlank()) {
                        tags.put("videoCodec", info.videoCodec());
                    }
                    if (info.audioCodec() != null && !info.audioCodec().isBlank()) {
                        tags.put("audioCodec", info.audioCodec());
                    }
                    if (info.displayAspectRatio() != null && !info.displayAspectRatio().isBlank()) {
                        tags.put("displayAspectRatio", info.displayAspectRatio());
                    }
                    if (info.bitDepth() != null && info.bitDepth() > 0) {
                        tags.put("bitDepth", String.valueOf(info.bitDepth()));
                    }
                    if (info.videoBitrateKbps() != null && info.videoBitrateKbps() > 0) {
                        tags.put("videoBitrateKbps", String.valueOf(info.videoBitrateKbps()));
                    }
                    if (info.audioBitrateKbps() != null && info.audioBitrateKbps() > 0) {
                        tags.put("audioBitrateKbps", String.valueOf(info.audioBitrateKbps()));
                    }

                    java.util.ArrayList<StreamInfo> streams = new java.util.ArrayList<>();
                    if (mediaType == MediaType.VIDEO && info.width() != null && info.height() != null && info.width() > 0 && info.height() > 0) {
                        streams.add(new StreamInfo(
                                streams.size(),
                                StreamKind.VIDEO,
                                info.videoCodec() != null ? info.videoCodec() : "unknown",
                                info.videoBitrateKbps(),
                                null,
                                null,
                                info.width(),
                                info.height(),
                                info.frameRate()
                        ));
                    }
                    if (info.sampleRate() != null && info.channels() != null && info.sampleRate() > 0 && info.channels() > 0) {
                        streams.add(new StreamInfo(
                                streams.size(),
                                StreamKind.AUDIO,
                                info.audioCodec() != null ? info.audioCodec() : "unknown",
                                info.audioBitrateKbps(),
                                info.sampleRate(),
                                info.channels(),
                                null,
                                null,
                                null
                        ));
                    }

                    return new ProbeResult(
                            input,
                            mimeType,
                            outputExt,
                            mediaType,
                            info.durationMillis(),
                            List.copyOf(streams),
                            tags
                    );
                } catch (CodecMediaException ignored) {
                    // Fall back to extension-only probe for malformed/partial files.
                }
                return new ProbeResult(input, mimeType, outputExt, mediaType, null, List.of(), Map.of("sizeBytes", String.valueOf(size)));
            }

            if (likelyWebm) {
                try {
                    WebmProbeInfo info = WebmCodec.decode(bytes, input);
                    java.util.LinkedHashMap<String, String> tags = new java.util.LinkedHashMap<>();
                    tags.put("sizeBytes", String.valueOf(size));
                    if (info.videoCodec() != null && !info.videoCodec().isBlank()) {
                        tags.put("videoCodec", info.videoCodec());
                    }
                    if (info.audioCodec() != null && !info.audioCodec().isBlank()) {
                        tags.put("audioCodec", info.audioCodec());
                    }
                    if (info.displayAspectRatio() != null && !info.displayAspectRatio().isBlank()) {
                        tags.put("displayAspectRatio", info.displayAspectRatio());
                    }
                    if (info.bitDepth() != null && info.bitDepth() > 0) {
                        tags.put("bitDepth", String.valueOf(info.bitDepth()));
                    }
                    if (info.videoBitrateKbps() != null && info.videoBitrateKbps() > 0) {
                        tags.put("videoBitrateKbps", String.valueOf(info.videoBitrateKbps()));
                    }
                    if (info.audioBitrateKbps() != null && info.audioBitrateKbps() > 0) {
                        tags.put("audioBitrateKbps", String.valueOf(info.audioBitrateKbps()));
                    }

                    java.util.ArrayList<StreamInfo> streams = new java.util.ArrayList<>();
                    if (info.width() != null && info.height() != null && info.width() > 0 && info.height() > 0) {
                        streams.add(new StreamInfo(0, StreamKind.VIDEO, info.videoCodec() != null ? info.videoCodec() : "unknown", info.videoBitrateKbps(), null, null, info.width(), info.height(), info.frameRate()));
                    }
                    if (info.sampleRate() != null && info.channels() != null && info.sampleRate() > 0 && info.channels() > 0) {
                        streams.add(new StreamInfo(
                                streams.size(),
                                StreamKind.AUDIO,
                                info.audioCodec() != null ? info.audioCodec() : "unknown",
                                info.audioBitrateKbps(),
                                info.sampleRate(),
                                info.channels(),
                                null,
                                null,
                                null
                        ));
                    }

                    return new ProbeResult(
                            input,
                            "video/webm",
                            "webm",
                            MediaType.VIDEO,
                            info.durationMillis(),
                            List.copyOf(streams),
                            tags
                    );
                } catch (CodecMediaException ignored) {
                    // Fall back to extension-only probe for malformed/partial files.
                }
                return new ProbeResult(input, "video/webm", "webm", MediaType.VIDEO, null, List.of(), Map.of("sizeBytes", String.valueOf(size)));
            }

            String mimeType = mimeTypeByExtension(extension);
            MediaType mediaType = mediaTypeByExtension(extension);

            return new ProbeResult(input, mimeType, extension, mediaType, null, List.of(), Map.of("sizeBytes", String.valueOf(size)));
        } catch (IOException e) {
            throw new CodecMediaException("Failed to probe file: " + input, e);
        }
    }

    @Override
    public Metadata readMetadata(Path input) throws CodecMediaException {
        ensureExists(input);
        ProbeResult probe = probe(input);
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("mimeType", probe.mimeType());
        entries.put("extension", probe.extension());
        entries.put("mediaType", probe.mediaType().name());
        String normalizedExtension = normalizeExtension(probe.extension());
        Map<String, String> embeddedEntries = readEmbeddedMetadata(input, normalizedExtension);
        for (Map.Entry<String, String> entry : embeddedEntries.entrySet()) {
            entries.put(entry.getKey(), entry.getValue());
        }

        Path sidecar = metadataSidecarPath(input);
        if (Files.exists(sidecar)) {
            Properties properties = new Properties();
            try (InputStream in = Files.newInputStream(sidecar)) {
                properties.load(in);
            } catch (IOException e) {
                throw new CodecMediaException("Failed to read metadata sidecar: " + sidecar, e);
            }
            for (String key : properties.stringPropertyNames()) {
                if (!isCoreMetadataKey(key)) {
                    entries.putIfAbsent(key, properties.getProperty(key));
                }
            }
        }

        return new Metadata(entries);
    }

    @Override
    public void writeMetadata(Path input, Metadata metadata) throws CodecMediaException {
        ensureExists(input);
        if (metadata == null || metadata.entries() == null) {
            throw new CodecMediaException("Metadata is required");
        }

        for (Map.Entry<String, String> entry : metadata.entries().entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                throw new CodecMediaException("Metadata key must not be null/blank");
            }
            if (entry.getValue() == null) {
                throw new CodecMediaException("Metadata value must not be null for key: " + entry.getKey());
            }
        }

        String extension = normalizeExtension(extractExtension(input));
        if ("wav".equals(extension)) {
            try {
                byte[] wavBytes = Files.readAllBytes(input);
                byte[] withMetadata = WavParser.writeInfoMetadata(wavBytes, metadata.entries());
                writeFileAtomically(input, withMetadata);
                deleteSidecarIfExists(input);
                return;
            } catch (IOException e) {
                throw new CodecMediaException("Failed to write WAV metadata: " + input, e);
            }
        }

        if ("aif".equals(extension) || "aiff".equals(extension) || "aifc".equals(extension)) {
            try {
                byte[] aiffBytes = Files.readAllBytes(input);
                byte[] withMetadata = AiffParser.writeTextMetadata(aiffBytes, metadata.entries());
                writeFileAtomically(input, withMetadata);
                deleteSidecarIfExists(input);
                return;
            } catch (IOException e) {
                throw new CodecMediaException("Failed to write AIFF metadata: " + input, e);
            }
        }

        if ("mp3".equals(extension)) {
            try {
                byte[] mp3Bytes = Files.readAllBytes(input);
                byte[] withTag = Mp3Id3v1Tag.write(mp3Bytes, metadata.entries());
                writeFileAtomically(input, withTag);
                deleteSidecarIfExists(input);
                return;
            } catch (IOException e) {
                throw new CodecMediaException("Failed to write MP3 metadata: " + input, e);
            }
        }

        Path sidecar = metadataSidecarPath(input);
        Properties properties = new Properties();
        Map<String, String> sorted = new TreeMap<>(metadata.entries());
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            properties.setProperty(entry.getKey(), entry.getValue());
        }

        try {
            writePropertiesAtomically(sidecar, properties);
        } catch (IOException e) {
            throw new CodecMediaException("Failed to write metadata sidecar: " + sidecar, e);
        }
    }

    @Override
    public ExtractionResult extractAudio(Path input, Path outputDir, AudioExtractOptions options) throws CodecMediaException {
        ensureExists(input);
        if (outputDir == null) {
            throw new CodecMediaException("Output directory is required");
        }

        ProbeResult probe = probe(input);
        AudioExtractOptions effective = options != null
                ? options
                : AudioExtractOptions.defaults(normalizeExtension(probe.extension()));

        if (probe.mediaType() != MediaType.AUDIO) {
            throw new CodecMediaException("Input is not an audio file: " + input);
        }

        String sourceExtension = normalizeExtension(probe.extension());
        String requestedExtension = normalizeExtension(effective.targetFormat());
        if (requestedExtension.isBlank()) {
            requestedExtension = sourceExtension;
        }
        if (!requestedExtension.equals(sourceExtension)) {
            throw new CodecMediaException(
                    "Audio extraction does not support format conversion yet. Requested format '" + requestedExtension
                            + "' must match source format '" + sourceExtension + "'"
            );
        }

        try {
            Files.createDirectories(outputDir);
            String baseName = baseName(input.getFileName().toString());
            String extension = sourceExtension;
            Path outputFile = outputDir.resolve(baseName + "_audio." + extension);
            Files.copy(input, outputFile, StandardCopyOption.REPLACE_EXISTING);
            return new ExtractionResult(outputFile, extension);
        } catch (IOException e) {
            throw new CodecMediaException("Failed to extract audio: " + input, e);
        }
    }

    @Override
    public ConversionResult convert(Path input, Path output, ConversionOptions options) throws CodecMediaException {
        ensureExists(input);
        if (output == null) {
            throw new CodecMediaException("Output file is required");
        }

        String sourceExtension = normalizeExtension(extractExtension(input));
        String inferredTargetFormat = extractExtension(output);
        ConversionOptions effective = options != null ? options : ConversionOptions.defaults(inferredTargetFormat);
        if (effective.targetFormat() == null || effective.targetFormat().isBlank()) {
            throw new CodecMediaException("ConversionOptions.targetFormat is required");
        }
        String requestedExtension = normalizeExtension(effective.targetFormat());
        String outputExtension = normalizeExtension(extractExtension(output));
        if (!outputExtension.isBlank() && !requestedExtension.equals(outputExtension)) {
            throw new CodecMediaException(
                    "Output extension must match target format: expected ." + requestedExtension
                            + " but got ." + outputExtension
            );
        }

        ProbeResult sourceProbe = probe(input);
        me.tamkungz.codecmedia.model.MediaType targetMediaType = mediaTypeByExtension(requestedExtension);
        ConversionRequest request = new ConversionRequest(
                input,
                output,
                sourceExtension,
                requestedExtension,
                sourceProbe.mediaType(),
                targetMediaType,
                effective
        );
        return conversionHub.convert(request);
    }

    @Override
    public PlaybackResult play(Path input, PlaybackOptions options) throws CodecMediaException {
        ensureExists(input);
        ProbeResult probe = probe(input);
        PlaybackOptions effective = options != null ? options : PlaybackOptions.defaults();

        if (probe.mediaType() == MediaType.UNKNOWN) {
            throw new CodecMediaException("Playback is not supported for unknown media type: " + input);
        }

        if (effective.dryRun()) {
            return new PlaybackResult(true, "dry-run", probe.mediaType(), "Playback simulation successful");
        }

        CodecMediaException javaSampledFailure = null;
        if (probe.mediaType() == MediaType.AUDIO && supportsJavaSampledExtension(probe.extension())) {
            try {
                javaSampledPlaybackBackend.play(input);
                return new PlaybackResult(true, "java-sampled", probe.mediaType(), "Started playback using javax.sound.sampled");
            } catch (CodecMediaException e) {
                javaSampledFailure = e;
            }
        }

        if (effective.allowExternalApp() && desktopPlaybackBackend.isSupported()) {
            try {
                desktopPlaybackBackend.open(input);
                return new PlaybackResult(true, "desktop-open", probe.mediaType(), "Opened with system default application");
            } catch (IOException | RuntimeException e) {
                if (javaSampledFailure != null) {
                    throw new CodecMediaException(
                            "Failed to start internal Java sampled playback and external application fallback: " + input,
                            e
                    );
                }
                throw new CodecMediaException("Failed to open media with system player/viewer: " + input, e);
            }
        }

        if (javaSampledFailure != null) {
            throw new CodecMediaException(
                    "No playback backend available after Java sampled attempt: " + javaSampledFailure.getMessage(),
                    javaSampledFailure
            );
        }

        throw new CodecMediaException("No playback backend available. Try dryRun=true or allowExternalApp=true");
    }

    @Override
    public ValidationResult validate(Path input, ValidationOptions options) {
        if (input == null) {
            return new ValidationResult(false, List.of(), List.of("Input file is required"));
        }
        ValidationOptions effective = options != null ? options : ValidationOptions.defaults();
        boolean exists = Files.exists(input);
        if (!exists) {
            return new ValidationResult(false, List.of(), List.of("File does not exist: " + input));
        }

        try {
            long size = Files.size(input);
            if (effective.maxBytes() > 0 && size > effective.maxBytes()) {
                return new ValidationResult(
                        false,
                        List.of(),
                        List.of("File exceeds maxBytes: " + size + " > " + effective.maxBytes())
                );
            }

            if (effective.strict()) {
                String extension = extractExtension(input);
                if (size > STRICT_VALIDATION_MAX_BYTES) {
                    return new ValidationResult(
                            false,
                            List.of(),
                            List.of("Strict validation is limited to files <= " + STRICT_VALIDATION_MAX_BYTES + " bytes")
                    );
                }

                byte[] bytes = Files.readAllBytes(input);
                if ("mp3".equals(extension)) {
                    try {
                        Mp3Parser.parse(bytes);
                    } catch (CodecMediaException e) {
                        return new ValidationResult(false, List.of(), List.of("Strict validation failed for mp3: " + e.getMessage()));
                    }
                } else if ("ogg".equals(extension)) {
                    try {
                        OggParser.parse(bytes);
                    } catch (CodecMediaException e) {
                        return new ValidationResult(false, List.of(), List.of("Strict validation failed for ogg: " + e.getMessage()));
                    }
                } else if ("wav".equals(extension)) {
                    try {
                        WavParser.parse(bytes);
                    } catch (CodecMediaException e) {
                        return new ValidationResult(false, List.of(), List.of("Strict validation failed for wav: " + e.getMessage()));
                    }
                } else if ("aif".equals(extension) || "aiff".equals(extension) || "aifc".equals(extension)) {
                    try {
                        AiffParser.parse(bytes);
                    } catch (CodecMediaException e) {
                        return new ValidationResult(false, List.of(), List.of("Strict validation failed for aiff: " + e.getMessage()));
                    }
                } else if ("flac".equals(extension)) {
                    try {
                        FlacParser.parse(bytes);
                    } catch (CodecMediaException e) {
                        return new ValidationResult(false, List.of(), List.of("Strict validation failed for flac: " + e.getMessage()));
                    }
                } else if ("png".equals(extension)) {
                    try {
                        PngParser.parse(bytes);
                    } catch (CodecMediaException e) {
                        return new ValidationResult(false, List.of(), List.of("Strict validation failed for png: " + e.getMessage()));
                    }
                } else if ("jpg".equals(extension) || "jpeg".equals(extension)) {
                    try {
                        JpegParser.parse(bytes);
                    } catch (CodecMediaException e) {
                        return new ValidationResult(false, List.of(), List.of("Strict validation failed for jpg/jpeg: " + e.getMessage()));
                    }
                } else if ("mov".equals(extension)) {
                    try {
                        MovParser.parse(bytes);
                    } catch (CodecMediaException e) {
                        return new ValidationResult(false, List.of(), List.of("Strict validation failed for mov: " + e.getMessage()));
                    }
                } else if ("mp4".equals(extension) || "m4a".equals(extension)) {
                    try {
                        Mp4Parser.parse(bytes);
                    } catch (CodecMediaException e) {
                        return new ValidationResult(false, List.of(), List.of("Strict validation failed for " + extension + ": " + e.getMessage()));
                    }
                } else if ("webm".equals(extension)) {
                    try {
                        WebmParser.parse(bytes);
                    } catch (CodecMediaException e) {
                        return new ValidationResult(false, List.of(), List.of("Strict validation failed for webm: " + e.getMessage()));
                    }
                } else if ("webp".equals(extension)) {
                    try {
                        WebpParser.parse(bytes);
                    } catch (CodecMediaException e) {
                        return new ValidationResult(false, List.of(), List.of("Strict validation failed for webp: " + e.getMessage()));
                    }
                } else if ("bmp".equals(extension)) {
                    try {
                        BmpParser.parse(bytes);
                    } catch (CodecMediaException e) {
                        return new ValidationResult(false, List.of(), List.of("Strict validation failed for bmp: " + e.getMessage()));
                    }
                } else if ("tif".equals(extension) || "tiff".equals(extension)) {
                    try {
                        TiffParser.parse(bytes);
                    } catch (CodecMediaException e) {
                        return new ValidationResult(false, List.of(), List.of("Strict validation failed for tif/tiff: " + e.getMessage()));
                    }
                } else if ("heic".equals(extension) || "heif".equals(extension) || "avif".equals(extension)) {
                    try {
                        HeifParser.parse(bytes);
                    } catch (CodecMediaException e) {
                        return new ValidationResult(false, List.of(), List.of("Strict validation failed for heic/heif/avif: " + e.getMessage()));
                    }
                }
            }

            return new ValidationResult(true, List.of(), List.of());
        } catch (IOException e) {
            return new ValidationResult(false, List.of(), List.of("Failed to validate file: " + e.getMessage()));
        }
    }

    private static void ensureExists(Path input) throws CodecMediaException {
        if (input == null) {
            throw new CodecMediaException("Input file is required");
        }
        if (!Files.exists(input)) {
            throw new CodecMediaException("File does not exist: " + input);
        }
        if (!Files.isRegularFile(input)) {
            throw new CodecMediaException("Input must be a regular file: " + input);
        }
    }

    private static String extractExtension(Path input) {
        if (input == null) {
            return "";
        }
        Path fileName = input.getFileName();
        if (fileName == null) {
            return "";
        }
        String name = fileName.toString();
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == name.length() - 1) {
            return "";
        }
        return name.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private static boolean isLikelyOgg(byte[] bytes) {
        return bytes.length >= 4
                && bytes[0] == 'O'
                && bytes[1] == 'g'
                && bytes[2] == 'g'
                && bytes[3] == 'S';
    }

    private static boolean isLikelyMp3(byte[] bytes) {
        if (bytes.length < 3) {
            return false;
        }
        if (bytes[0] == 'I' && bytes[1] == 'D' && bytes[2] == '3') {
            return true;
        }
        if (bytes.length < 2) {
            return false;
        }
        return (bytes[0] & (byte) 0xFF) == (byte) 0xFF && (bytes[1] & (byte) 0xE0) == (byte) 0xE0;
    }

    private static byte[] readProbePrefix(Path input) throws IOException {
        int expected = (int) Math.min(PROBE_PREFIX_BYTES, Math.max(0L, Files.size(input)));
        if (expected == 0) {
            return new byte[0];
        }
        try (InputStream in = Files.newInputStream(input)) {
            return in.readNBytes(expected);
        }
    }

    private static String mimeTypeByExtension(String extension) {
        return switch (extension) {
            case "mp4" -> "video/mp4";
            case "mov" -> "video/quicktime";
            case "webm" -> "video/webm";
            case "m4a" -> "audio/mp4";
            case "mp3" -> "audio/mpeg";
            case "ogg" -> "audio/ogg";
            case "wav" -> "audio/wav";
            case "aif", "aiff", "aifc" -> "audio/aiff";
            case "flac" -> "audio/flac";
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "webp" -> "image/webp";
            case "bmp" -> "image/bmp";
            case "tif", "tiff" -> "image/tiff";
            case "heic" -> "image/heic";
            case "heif" -> "image/heif";
            case "avif" -> "image/avif";
            default -> "application/octet-stream";
        };
    }

    private static Path metadataSidecarPath(Path input) {
        return input.resolveSibling(input.getFileName() + ".codecmedia.properties");
    }

    private static void deleteSidecarIfExists(Path input) throws IOException {
        Files.deleteIfExists(metadataSidecarPath(input));
    }

    private static void writeFileAtomically(Path target, byte[] bytes) throws IOException {
        Path parent = target.getParent();
        if (parent == null) {
            parent = Path.of(".").toAbsolutePath().normalize();
        }
        Files.createDirectories(parent);

        Path temp = Files.createTempFile(parent, target.getFileName().toString(), ".tmp");
        boolean moved = false;
        try {
            Files.write(temp, bytes, StandardOpenOption.TRUNCATE_EXISTING);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temp);
            }
        }
    }

    private static void writePropertiesAtomically(Path target, Properties properties) throws IOException {
        Path parent = target.getParent();
        if (parent == null) {
            parent = Path.of(".").toAbsolutePath().normalize();
        }
        Files.createDirectories(parent);

        Path temp = Files.createTempFile(parent, target.getFileName().toString(), ".tmp");
        boolean moved = false;
        try (OutputStream out = Files.newOutputStream(temp, StandardOpenOption.TRUNCATE_EXISTING)) {
            properties.store(out, "CodecMedia metadata sidecar");
        }
        try {
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temp);
            }
        }
    }

    private static boolean isCoreMetadataKey(String key) {
        return "mimeType".equals(key) || "extension".equals(key) || "mediaType".equals(key);
    }

    private static Map<String, String> readEmbeddedMetadata(Path input, String normalizedExtension) throws CodecMediaException {
        try {
            byte[] bytes = Files.readAllBytes(input);
            return switch (normalizedExtension) {
                case "wav" -> WavParser.readInfoMetadata(bytes);
                case "aif", "aiff", "aifc" -> AiffParser.readTextMetadata(bytes);
                case "mp3" -> Mp3Id3v1Tag.read(bytes);
                case "ogg" -> OggParser.readCommentMetadata(bytes);
                case "flac" -> FlacParser.readVorbisCommentMetadata(bytes);
                default -> Map.of();
            };
        } catch (IOException e) {
            throw new CodecMediaException("Failed to read embedded metadata: " + input, e);
        }
    }

    private static String normalizeExtension(String format) {
        if (format == null) {
            return "";
        }
        String value = format.trim().toLowerCase(Locale.ROOT);
        return value.startsWith(".") ? value.substring(1) : value;
    }

    private static String baseName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private static boolean supportsJavaSampledExtension(String extension) {
        String normalized = normalizeExtension(extension);
        return "wav".equals(normalized)
                || "aif".equals(normalized)
                || "aiff".equals(normalized)
                || "aifc".equals(normalized);
    }

    private static MediaType mediaTypeByExtension(String extension) {
        return switch (normalizeExtension(extension)) {
            case "mp3", "ogg", "wav", "aif", "aiff", "aifc", "pcm", "m4a", "aac", "flac" -> MediaType.AUDIO;
            case "mp4", "mkv", "mov", "avi", "webm" -> MediaType.VIDEO;
            case "png", "jpg", "jpeg", "gif", "bmp", "webp", "tif", "tiff", "heic", "heif", "avif" -> MediaType.IMAGE;
            default -> MediaType.UNKNOWN;
        };
    }

    interface JavaSampledPlaybackBackend {
        void play(Path input) throws CodecMediaException;
    }

    interface DesktopPlaybackBackend {
        boolean isSupported();

        void open(Path input) throws IOException;
    }

    private static final class JdkJavaSampledPlaybackBackend implements JavaSampledPlaybackBackend {
        @Override
        public void play(Path input) throws CodecMediaException {
            try (AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(input.toFile())) {
                Clip clip = AudioSystem.getClip();
                boolean started = false;
                try {
                    clip.open(audioInputStream);
                    clip.addLineListener(event -> {
                        if (event.getType() == LineEvent.Type.STOP || event.getType() == LineEvent.Type.CLOSE) {
                            clip.close();
                        }
                    });
                    clip.start();
                    started = true;
                } finally {
                    if (!started) {
                        clip.close();
                    }
                }
            } catch (UnsupportedAudioFileException | LineUnavailableException | IOException | RuntimeException e) {
                throw new CodecMediaException("Java sampled playback failed for " + input + ": " + e.getMessage(), e);
            }
        }
    }

    private static final class AwtDesktopPlaybackBackend implements DesktopPlaybackBackend {
        @Override
        public boolean isSupported() {
            return Desktop.isDesktopSupported();
        }

        @Override
        public void open(Path input) throws IOException {
            Desktop.getDesktop().open(input.toFile());
        }
    }
}
