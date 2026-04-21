# CodecMedia

[![MvnRepository](https://badges.mvnrepository.com/badge/me.tamkungz.codecmedia/codecmedia/badge.svg?label=MvnRepository)](https://mvnrepository.com/artifact/me.tamkungz.codecmedia/codecmedia)
[![Sonatype Central](https://img.shields.io/badge/Sonatype%20Central-codecmedia-1f6feb)](https://central.sonatype.com/artifact/me.tamkungz.codecmedia/codecmedia)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java](https://img.shields.io/badge/Java-17%2B-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Maven](https://img.shields.io/badge/Maven-3.9%2B-C71A36?logo=apachemaven&logoColor=white)](https://maven.apache.org/)

CodecMedia is a Java library for media probing, validation, metadata persistence (embedded WAV/AIFF/MP3 where supported with sidecar fallback for other formats), audio extraction, playback workflow handling, and conversion routing.

<p align="center">
  <img src="https://codecmedia.tamkungz.me/CodecMedia_Full_Logo.png" width="70%" alt="CodecMedia Logo">
</p>

## What can you do with CodecMedia?

- Extract audio from video in 3 lines
- Validate media files before upload
- Read metadata (duration, bitrate, format)
- Build media pipelines in pure Java

## Approaches
- Zero-Dependency
- Self-Contained
- Multi-Platform

## Features

- Media engine facade via `CodecMedia.createDefault()`
- Probing support for:
  - MP3
  - OGG/Vorbis/Opus
  - WAV (RIFF/WAVE)
  - AIFF/AIF/AIFC (COMM-based parsing)
  - M4A (MP4 audio profile)
  - FLAC (STREAMINFO parsing)
  - PNG
  - JPEG
  - WebP
  - BMP
  - TIFF
  - HEIC/HEIF/AVIF (basic BMFF parsing)
  - MOV (QuickTime container parsing)
  - MP4 (basic ISO BMFF parsing)
  - WebM (EBML container parsing)
- Validation with size limits and strict parser checks for MP3/OGG/WAV/AIFF/FLAC/PNG/JPEG/WebP/BMP/TIFF/HEIC/HEIF/AVIF/MOV/MP4/WebM
- MOV/MP4/WebM probe tags now include richer video metadata when present (for example `displayAspectRatio`, `bitDepth`, `videoBitrateKbps`, `audioBitrateKbps`)
- Metadata read/write with embedded WAV LIST/INFO, AIFF text chunks (`NAME`/`AUTH`/`(c) `/`ANNO`), and MP3 ID3v1 support, plus sidecar persistence (`.codecmedia.properties`) for non-embedded fallback/compatibility paths
- In-Java extraction and conversion file operations
- Image-to-image conversion in Java for: `png`, `jpg`/`jpeg`, `webp`, `bmp`, `tif`/`tiff`, `heic`/`heif`/`avif`
- Playback API with dry-run support, internal Java sampled backend for WAV/AIFF family, and optional desktop-open fallback
- Conversion hub routing with explicit unsupported routes, a real `wav <-> pcm` path (`WAV -> PCM` data-chunk extraction, `PCM -> WAV` wrapping), MP3 decode routes (`mp3 -> pcm`/`mp3 -> wav`) with selectable decoder backend (`decoder=javasound` default, `decoder=pure-java`/`decoder=layer3` experimental), JDK Java Sound audio targets (`wav`/`aiff`/`au`), and MP4/MOV audio-track remux to `m4a` when codec-compatible

## API Behavior Summary

- `get(input)`: alias of `probe(input)` for convenience.
- `probe(input)`: detects media/container characteristics and returns technical stream info for supported formats.
- `readMetadata(input)`: returns derived probe metadata plus embedded metadata where supported (WAV LIST/INFO, AIFF text chunks, MP3 ID3v1, OGG/FLAC comments), then merges sidecar entries as fallback when present.
- `writeMetadata(input, metadata)`: validates and writes embedded metadata where supported (WAV LIST/INFO, AIFF text chunks, MP3 ID3v1); for embedded-capable formats, stale sidecar files are removed; sidecar remains for compatibility/non-embedded paths.
- `extractAudio(input, outputDir, options)`: validates audio input and writes extracted output into `outputDir`.
- `convert(input, output, options)`: performs routed conversion behavior and enforces `overwrite` handling.
- `play(input, options)`: supports dry-run playback, routes WAV/AIFF-family playback through an internal Java sampled backend, and falls back to optional system default app launch.
- `validate(input, options)`: validates existence, max size, and optional strict parser-level checks.

## Notes and Limitations

- Current probing focuses on **technical media info** (mime/type/streams/basic tags).
- Probe routing now performs a lightweight header-prefix sniff before full decode to reduce unnecessary full-file reads for clearly unsupported/unknown inputs.
- `readMetadata` supports embedded metadata for WAV (LIST/INFO), AIFF text chunks, MP3 (ID3v1), and OGG/FLAC comments; it is **not** a full embedded tag extractor for advanced tag families (for example ID3v2 APIC/album art).
- Audio-to-audio conversion is partially implemented with JDK Java Sound targets (`wav`/`aiff`/`au`) plus decode-focused routes (`wav <-> pcm`, `mp3 -> pcm`, `mp3 -> wav`); general compressed-target transcode cases (for example `mp3 -> ogg`) are still not implemented.
- Implemented decode-focused audio routes:
- `wav -> pcm`: extracts raw PCM payload from WAV `data` chunk
- `pcm -> wav`: wraps PCM into PCM WAV container
- Optional PCM->WAV preset tuning via `ConversionOptions.preset`, for example: `sr=22050,ch=1,bits=16`
- `mp3 -> pcm` / `mp3 -> wav`: decodes MP3 via selectable backend using `ConversionOptions.preset`, for example: `decoder=javasound` (default), `decoder=pure-java`, `decoder=layer3`
- Container/unknown conversion routes are intentionally unsupported unless explicitly mapped by the conversion route resolver.
- TIFF probe currently reads the **first IFD/image** only (multi-page TIFF traversal is not implemented in probe mode).
- WebP probe currently reports `bitDepth` as an assumed default (`8`) for `VP8`/`VP8L`/`VP8X` unless deeper profile metadata parsing is added.
- For OpenAL workflows that require OGG from MP3 input, use an external transcoder first (for example ffmpeg), then play the produced OGG.

## Requirements

- Java 17+
- Maven 3.9+

## Build

```bash
mvn clean verify
```

## Fixture-Driven Tests

Fixtures are in `src/test/resources`, including:

- `c-major-scale_test_ableton-live.wav`
- `c-major-scale_test_audacity.mp3`
- `c-major-scale_test_ffmpeg.ogg`
- `c-major-scale_test_web-convert_mono.mp3`
- `mp4_test.mp4`
- `png_test.png`

Run probe fixture test only:

```bash
mvn -Dtest=CodecMediaPlayTest test
```

Run facade-focused tests only:

```bash
mvn -Dtest=CodecMediaFacadeTest test
```

## Full Test Suite

```bash
mvn test
```

## License

This project is licensed under the Apache License 2.0.

---

*by TamKungZ_*
