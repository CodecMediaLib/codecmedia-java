<p align="center">
  <img src="https://pub-df28fb9f69aa4326a1c6e10fb1f2abdc.r2.dev/assets-image/codecmedia/CodecMedia_Full_Logo.png" width="50%" alt="CodecMedia Logo">
</p>

# CodecMedia

[![MvnRepository](https://badges.mvnrepository.com/badge/me.tamkungz.codecmedia/codecmedia/badge.svg?label=MvnRepository)](https://mvnrepository.com/artifact/me.tamkungz.codecmedia/codecmedia)
[![Sonatype Central](https://img.shields.io/badge/Sonatype%20Central-codecmedia-1f6feb)](https://central.sonatype.com/artifact/me.tamkungz.codecmedia/codecmedia)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java](https://img.shields.io/badge/Java-17%2B-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Maven](https://img.shields.io/badge/Maven-3.9%2B-C71A36?logo=apachemaven&logoColor=white)](https://maven.apache.org/)

CodecMedia is a Java 17 library for inspecting and handling media files with no runtime dependencies declared in the Maven project.

The current implementation focuses on:

- Media probing for selected audio, image, and video/container formats
- File validation with optional strict parser checks
- Basic metadata read/write workflows
- Limited conversion routes that are implemented in Java or through JDK APIs
- Playback workflow routing for Java Sound and desktop-open use cases

CodecMedia is not a complete transcoding framework. Unsupported routes fail explicitly instead of silently shelling out to external tools.

## Table of Contents

- [CodecMedia](#codecmedia)
  - [Table of Contents](#table-of-contents)
  - [Overview](#overview)
  - [Features](#features)
  - [Current Status](#current-status)
  - [Installation](#installation)
    - [Prerequisites](#prerequisites)
    - [Maven](#maven)
    - [Gradle](#gradle)
    - [Build From Source](#build-from-source)
  - [Quick Start](#quick-start)
    - [Probe And Validate A File](#probe-and-validate-a-file)
    - [Convert An Image](#convert-an-image)
    - [Read And Write Metadata](#read-and-write-metadata)
  - [Configuration](#configuration)
    - [`ValidationOptions`](#validationoptions)
    - [`ConversionOptions`](#conversionoptions)
    - [`AudioExtractOptions`](#audioextractoptions)
    - [`PlaybackOptions`](#playbackoptions)
  - [Architecture](#architecture)
  - [Supported Features](#supported-features)
    - [Probing](#probing)
    - [Validation](#validation)
    - [Metadata](#metadata)
    - [Audio Extraction](#audio-extraction)
    - [Conversion](#conversion)
    - [Playback](#playback)
  - [Limitations](#limitations)
  - [Development](#development)
  - [Testing](#testing)
  - [Roadmap](#roadmap)
    - [Implemented](#implemented)
    - [Planned](#planned)
    - [TODO](#todo)
  - [Contributing](#contributing)
  - [License](#license)

## Overview

Use CodecMedia when you need a small Java API for common media-file checks and simple processing tasks.

The public entry point is `CodecMedia.createDefault()`, which returns a `CodecMediaEngine`.

Main API methods:

- `get(Path)` - alias for `probe(Path)`
- `probe(Path)` - detect media type, MIME type, extension, duration, streams, and format-specific tags
- `validate(Path, ValidationOptions)` - validate file existence, size limits, and optional strict parser checks
- `readMetadata(Path)` - read base probe metadata plus supported embedded or sidecar metadata
- `writeMetadata(Path, Metadata)` - write supported embedded metadata, or a sidecar file when embedded writes are not supported
- `extractAudio(Path, Path, AudioExtractOptions)` - copy supported audio input into an output directory without transcoding
- `convert(Path, Path, ConversionOptions)` - run an implemented conversion route
- `play(Path, PlaybackOptions)` - attempt dry-run, Java sampled playback, or desktop-open playback

## Features

- Java 17 API using `Path` and Java records
- Maven artifact: `me.tamkungz.codecmedia:codecmedia:1.2.2`
- No runtime dependencies declared in `pom.xml`
- Parser-backed probing for selected media formats
- Strict validation for supported parsers
- Embedded metadata support for selected audio formats
- Sidecar metadata fallback using `.codecmedia.properties`
- Conversion hub with explicit route handling
- CI coverage for Java 17 and Java 21

## Current Status

CodecMedia is usable for the implemented routes listed in this README, but the project is still evolving.

Important status notes:

- Current version: `1.2.2`
- Minimum Java version: 17
- Build tool: Maven
- Default implementation class is still named `StubCodecMediaEngine`; this name does not mean every feature is a stub, but it is a sign that the implementation is still being consolidated.
- `decoder=pure-java` / `decoder=layer3` is currently disabled and fails explicitly. Older versions synthesized a timing-correct 440 Hz PCM signal rather than decoding source MP3 audio, which was misleading. Use `decoder=javasound` for real decoding.
- General-purpose transcoding is not implemented.
- Some image conversion routes depend on ImageIO reader/writer support available in the runtime.

## Installation

### Prerequisites

- JDK 17 or newer
- Maven 3.9 or newer if building from source

### Maven

Add the dependency to your project:

```xml
<dependency>
  <groupId>me.tamkungz.codecmedia</groupId>
  <artifactId>codecmedia</artifactId>
  <version>1.2.2</version>
</dependency>
```

### Gradle

```kotlin
dependencies {
    implementation("me.tamkungz.codecmedia:codecmedia:1.2.2")
}
```

### Build From Source

```bash
git clone https://github.com/CodecMediaLib/codecmedia-java.git
cd codecmedia-java
mvn -Dgpg.skip=true clean verify
```

`-Dgpg.skip=true` is useful for local development because the project signs release artifacts during `verify`.

## Quick Start

### Probe And Validate A File

```java
import java.nio.file.Path;

import me.tamkungz.codecmedia.CodecMedia;
import me.tamkungz.codecmedia.CodecMediaEngine;
import me.tamkungz.codecmedia.options.ValidationOptions;

public class ProbeExample {
    public static void main(String[] args) throws Exception {
        CodecMediaEngine media = CodecMedia.createDefault();
        Path input = Path.of(args.length > 0 ? args[0] : "src/test/resources/png_test.png");

        var probe = media.probe(input);
        System.out.println(probe.mimeType());
        System.out.println(probe.mediaType());
        System.out.println(probe.primaryCodec().orElse("unknown"));

        var validation = media.validate(input, ValidationOptions.defaults());
        System.out.println("valid=" + validation.valid());
    }
}
```

### Convert An Image

```java
import java.nio.file.Path;

import me.tamkungz.codecmedia.CodecMedia;
import me.tamkungz.codecmedia.options.ConversionOptions;

public class ImageConvertExample {
    public static void main(String[] args) throws Exception {
        var media = CodecMedia.createDefault();

        var result = media.convert(
                Path.of("src/test/resources/png_test.png"),
                Path.of("target/example-output.jpg"),
                new ConversionOptions("jpg", "balanced", true)
        );

        System.out.println(result.outputFile());
        System.out.println(result.format());
    }
}
```

### Read And Write Metadata

```java
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

import me.tamkungz.codecmedia.CodecMedia;
import me.tamkungz.codecmedia.model.Metadata;

public class MetadataExample {
    public static void main(String[] args) throws Exception {
        var media = CodecMedia.createDefault();
        Path input = Path.of("src/test/resources/png_test.png");
        Path workFile = Path.of("target/metadata-example.png");

        Files.createDirectories(workFile.getParent());
        Files.copy(input, workFile, StandardCopyOption.REPLACE_EXISTING);

        media.writeMetadata(workFile, new Metadata(Map.of("title", "Example")));
        Metadata metadata = media.readMetadata(workFile);

        System.out.println(metadata.entries());
    }
}
```

For formats without embedded write support, metadata is written to a sibling sidecar file named `<file>.codecmedia.properties`.

## Configuration

Configuration is passed through option records.

### `ValidationOptions`

- `strict` - when `true`, runs parser-level checks for supported formats
- `maxBytes` - rejects files larger than this size when greater than `0`
- `ValidationOptions.defaults()` - non-strict validation with a 500 MiB size limit

Example:

```java
var options = new ValidationOptions(true, 64L * 1024L * 1024L);
```

### `ConversionOptions`

- `targetFormat` - required output format, such as `jpg`, `wav`, `pcm`, or `m4a`
- `preset` - route-specific string, defaulting to `balanced`
- `overwrite` - controls whether existing output files may be replaced
- `ConversionOptions.defaults()` - defaults to target format `m4a`, preset `balanced`, and `overwrite=false`

Known preset tokens:

- `pcm -> wav`: `sr=<sampleRate>`, `ch=<channels>`, `bits=<bitsPerSample>`
- `mp3 -> pcm` and `mp3 -> wav`: use `decoder=javasound`; `decoder=pure-java` / `decoder=layer3` is retained as an explicit unsupported placeholder until a real Layer III decoder exists

### `AudioExtractOptions`

- `targetFormat` - must match the source format in the current implementation
- `bitrateKbps` - accepted by the options record, but no transcoding is performed by `extractAudio`
- `streamIndex` - accepted by the options record, but multi-stream extraction is not implemented
- `AudioExtractOptions.defaults()` - uses source format resolution in the engine

### `PlaybackOptions`

- `dryRun` - returns a successful playback result without opening or playing the file
- `allowExternalApp` - allows fallback to the operating system default application
- `PlaybackOptions.defaults()` - `dryRun=false`, `allowExternalApp=true`

## Architecture

CodecMedia is organized around a small public facade and internal format-specific components.

- `CodecMedia` creates the default engine.
- `CodecMediaEngine` defines the public API contract.
- `StubCodecMediaEngine` coordinates probing, validation, metadata, extraction, conversion, and playback.
- Internal parser packages handle audio, image, and container formats.
- `DefaultConversionHub` routes conversions to specific converters or explicit unsupported-route failures.
- Image conversion uses JDK `ImageIO` and any reader/writer plugins present at runtime.
- Audio playback uses Java Sound for WAV/AIFF-family inputs and can fall back to `java.awt.Desktop`.

## Supported Features

### Probing

Probe results include the detected MIME type, extension, media type, duration when available, stream information, and format-specific tags.

Supported probe formats:

- Audio: `mp3`, `ogg`, `wav`, `aif`, `aiff`, `aifc`, `flac`, `m4a`
- Images: `png`, `jpg`, `jpeg`, `webp`, `bmp`, `tif`, `tiff`, `heic`, `heif`, `avif`
- Video/containers: `mov`, `mp4`, `webm`

### Validation

Validation supports:

- File existence checks
- Maximum file size checks
- Optional strict parser checks for `mp3`, `ogg`, `wav`, `aif`, `aiff`, `aifc`, `flac`, `png`, `jpg`, `jpeg`, `mov`, `mp4`, `m4a`, `webm`, `webp`, `bmp`, `tif`, `tiff`, `heic`, `heif`, and `avif`

Strict validation currently reads the full file and is limited to files up to 32 MiB.

### Metadata

`readMetadata` always includes base probe fields:

- `mimeType`
- `extension`
- `mediaType`

Embedded metadata support:

- WAV: read/write RIFF `LIST/INFO`
- AIFF/AIF/AIFC: read/write `NAME`, `AUTH`, `(c) `, and `ANNO` text chunks
- MP3: read/write ID3v1
- OGG: read Vorbis/Opus comments
- FLAC: read Vorbis comments

Sidecar metadata support:

- Unsupported embedded write formats use `<file>.codecmedia.properties`.
- Embedded metadata is treated as canonical where supported.
- Sidecar values are merged as fallback values for non-core keys.

### Audio Extraction

Current audio extraction is a copy workflow:

- Input must be detected as `MediaType.AUDIO`.
- Output is written as `<baseName>_audio.<sourceExtension>` inside the output directory.
- Requested target format must match the source format.
- Format conversion during extraction is not implemented.

### Conversion

Implemented conversion routes:

- Same-format copy for matching source and target extensions
- Image-to-image conversion for `png`, `jpg`, `jpeg`, `webp`, `bmp`, `tif`, `tiff`, `heic`, `heif`, and `avif`
- `wav -> pcm` by extracting the WAV `data` chunk from PCM WAV files
- `pcm -> wav` by wrapping raw PCM bytes in a WAV container
- `mp3 -> pcm` through a selected decoder backend
- `mp3 -> wav` through a selected decoder backend
- Java Sound audio output to `wav`, `aif`, `aiff`, `aifc`, or `au` when the runtime supports the source and target combination
- `mp4 -> m4a` and `mov -> m4a` remux when an M4A-compatible audio track is present

Runtime-dependent routes:

- WebP image decode/encode requires ImageIO WebP support.
- TIFF image decode/encode may require ImageIO TIFF support, depending on the JDK/runtime.
- HEIF, HEIC, and AVIF image decode/encode require compatible ImageIO support.
- Java Sound audio conversion depends on audio service providers available in the runtime.

### Playback

Playback supports:

- Dry-run mode for all known media types
- Java sampled playback for WAV/AIFF-family audio when Java Sound can open the file
- Desktop-open fallback through `java.awt.Desktop` when enabled and supported by the platform

## Limitations

- Probe output is technical media information, not a complete metadata catalog.
- ID3v2, album art/APIC extraction, and advanced tag families are not fully supported.
- General compressed audio transcoding, such as `mp3 -> ogg`, is not implemented.
- Generic video-to-audio conversion is not implemented, except the implemented MP4/MOV to M4A remux route.
- Video-to-video conversion is not implemented.
- Audio-to-image album-art extraction is not implemented.
- `extractAudio` does not transcode and does not select arbitrary streams.
- TIFF probing reads the first IFD/image only.
- WebP probing reports an assumed bit depth for VP8, VP8L, and VP8X when deeper profile metadata is not parsed.
- HEIF, HEIC, AVIF, WebP, and TIFF image conversion depends on runtime ImageIO support.
- Strict validation is limited to files up to 32 MiB.

## Development

Clone and verify the project locally:

```bash
git clone https://github.com/CodecMediaLib/codecmedia-java.git
cd codecmedia-java
mvn -Dgpg.skip=true clean verify
```

Useful commands:

```bash
mvn -Dgpg.skip=true test
mvn -Dgpg.skip=true -DskipTests package
mvn -Dgpg.skip=true clean verify
```

Project layout:

- `src/main/java/me/tamkungz/codecmedia` - public API and implementation
- `src/main/java/me/tamkungz/codecmedia/internal` - parsers, converters, and engine internals
- `src/test/java` - unit and facade tests
- `src/test/resources` - test fixtures
- `.github/workflows` - CI and deeper test workflows

## Testing

Run the full test suite:

```bash
mvn -Dgpg.skip=true test
```

Run the same verify phase used by CI without signing artifacts:

```bash
mvn -Dgpg.skip=true clean verify
```

Run focused tests:

```bash
mvn -Dgpg.skip=true -Dtest=CodecMediaFacadeTest test
mvn -Dgpg.skip=true -Dtest=CodecMediaPlayTest test
mvn -Dgpg.skip=true -Dtest=Mp3PcmWavConverterTest test
```

CI currently verifies Java 17 and Java 21 with:

```bash
mvn -B -Dgpg.skip=true clean verify
```

## Roadmap

Implemented features and planned work are separated below to avoid implying unsupported behavior.

### Implemented

- Probing for the supported audio, image, and video/container formats listed above
- Strict parser validation for supported formats
- Embedded metadata read/write for WAV, AIFF-family files, and MP3 ID3v1
- Embedded metadata read for OGG and FLAC comments
- Sidecar metadata fallback
- Limited conversion routes listed in [Conversion](#conversion)
- Dry-run, Java sampled, and desktop-open playback workflows

### Planned

- Generic video-to-audio conversion route
- Audio-to-image album-art extraction route
- Video-to-video conversion route
- Broader audio transcoding support beyond Java Sound, WAV/PCM, and MP3 decode routes

### TODO

- TODO: Document the exact metadata key mapping for each embedded metadata format.
- TODO: Document the supported ImageIO plugin combinations used for WebP, TIFF, HEIF, HEIC, and AVIF conversion.
- TODO: Document release and publishing steps for maintainers.
- TODO: Decide whether `StubCodecMediaEngine` should be renamed now that it contains implemented behavior.

## Contributing

Contributions should keep documentation aligned with implemented behavior.

Before opening a pull request:

- Run `mvn -Dgpg.skip=true test`.
- Add or update tests for parser, conversion, metadata, or facade behavior changes.
- Update this README and `CHANGELOG.md` when user-visible behavior changes.
- Mark incomplete or runtime-dependent behavior clearly.
- Avoid documenting planned features as supported features.

Issues and pull requests are tracked at:

- https://github.com/CodecMediaLib/codecmedia-java/issues

## License

CodecMedia is licensed under the Apache License 2.0. See [LICENSE](LICENSE) for details.
