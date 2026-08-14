# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).


## [1.2.2] - 2026-08-14

### Fixed
- Made [`WavParser.isLikelyWav()`](src/main/java/me/tamkungz/codecmedia/internal/audio/wav/WavParser.java) null-safe.
- Corrected shifted `WAVE_FORMAT_EXTENSIBLE` subtype GUID-tail offsets in [`WavParser`](src/main/java/me/tamkungz/codecmedia/internal/audio/wav/WavParser.java), so valid PCM/IEEE-float extensible GUIDs are checked at their actual byte positions.
- Fixed RF64 metadata traversal in [`WavParser.readInfoMetadata()`](src/main/java/me/tamkungz/codecmedia/internal/audio/wav/WavParser.java) to resolve the `data` chunk `0xFFFFFFFF` sentinel through `ds64` instead of treating it as an out-of-bounds chunk.
- Fixed RF64 WAV -> PCM extraction in [`WavPcmConverter`](src/main/java/me/tamkungz/codecmedia/internal/convert/WavPcmConverter.java) to use unsigned chunk sizes and the RF64 `ds64` data size.
- Changed [`PureJavaMp3Decoder`](src/main/java/me/tamkungz/codecmedia/internal/audio/mp3/PureJavaMp3Decoder.java) to fail closed instead of synthesizing a 440 Hz PCM tone. The previous experimental output preserved frame timing but was not decoded source audio and could therefore be mistaken for valid conversion output.

### Verification
- Java tests were intentionally not run for these companion fixes, per the requested scope.

## [1.2.1] - 2026-05-07

### Changed
- Updated metadata write flow in [`StubCodecMediaEngine`](src/main/java/me/tamkungz/codecmedia/internal/StubCodecMediaEngine.java) to use atomic temp-file replace for embedded WAV/AIFF/MP3 writes and sidecar property writes.
- Aligned extract-audio defaults in [`AudioExtractOptions`](src/main/java/me/tamkungz/codecmedia/options/AudioExtractOptions.java) and [`StubCodecMediaEngine.extractAudio()`](src/main/java/me/tamkungz/codecmedia/internal/StubCodecMediaEngine.java) so blank/default target format resolves to source extension.
- Added output-extension/target-format consistency guard in [`StubCodecMediaEngine.convert()`](src/main/java/me/tamkungz/codecmedia/internal/StubCodecMediaEngine.java) to prevent mismatched container/extension outputs.
- Strengthened input validation in [`StubCodecMediaEngine.ensureExists()`](src/main/java/me/tamkungz/codecmedia/internal/StubCodecMediaEngine.java) by requiring a regular file path.
- Hardened HEIF nested box traversal in [`HeifParser`](src/main/java/me/tamkungz/codecmedia/internal/image/heif/HeifParser.java) by skipping the `meta` FullBox header before recursive child-box scan.
- Added box-size overflow guards in [`Mp4Parser`](src/main/java/me/tamkungz/codecmedia/internal/video/mp4/Mp4Parser.java) and [`MovParser`](src/main/java/me/tamkungz/codecmedia/internal/video/mov/MovParser.java) before advancing parse cursor.
- Hardened Java sampled playback cleanup in [`StubCodecMediaEngine`](src/main/java/me/tamkungz/codecmedia/internal/StubCodecMediaEngine.java) to close `Clip` on startup failure paths.
- Bumped artifact version to `1.2.1` in [`pom.xml`](pom.xml).

### Verified
- Confirmed regression suite stability with `mvn test`.

## [1.2.0] - 2026-04-21

### Added
- Added MP3 decode component [`Mp3Decoder`](src/main/java/me/tamkungz/codecmedia/internal/audio/mp3/Mp3Decoder.java) to produce PCM signed 16-bit little-endian output via Java Sound runtime SPI.
- Added optional experimental pure-Java MP3 decode backend [`PureJavaMp3Decoder`](src/main/java/me/tamkungz/codecmedia/internal/audio/mp3/PureJavaMp3Decoder.java) with MP3 frame-based PCM timeline synthesis outputting PCM S16LE.
- Added MP3 conversion route handler [`Mp3PcmWavConverter`](src/main/java/me/tamkungz/codecmedia/internal/convert/Mp3PcmWavConverter.java) for `mp3 -> pcm` and `mp3 -> wav` workflows.
- Added conversion regression coverage in [`Mp3PcmWavConverterTest`](src/test/java/me/tamkungz/codecmedia/internal/convert/Mp3PcmWavConverterTest.java) for both target formats with runtime-safe assertions.

### Changed
- Extended MP3 conversion preset handling in [`Mp3PcmWavConverter`](src/main/java/me/tamkungz/codecmedia/internal/convert/Mp3PcmWavConverter.java) with decoder token selection: `decoder=javasound` (default), `decoder=pure-java`, and alias `decoder=layer3`, including explicit unsupported-token errors.
- Updated audio conversion route wiring in [`DefaultConversionHub`](src/main/java/me/tamkungz/codecmedia/internal/convert/DefaultConversionHub.java) to prioritize MP3 decode routes (`mp3 -> pcm`, `mp3 -> wav`) before generic Java Sound audio transcoding.
- Hardened FLAC parsing in [`FlacParser`](src/main/java/me/tamkungz/codecmedia/internal/audio/flac/FlacParser.java) with strict STREAMINFO block length validation, reserved metadata-type checks in Vorbis read path, ID3v2-leading FLAC detection, and explicit malformed Vorbis-comment errors.
- Hardened WebP probing in [`WebpParser`](src/main/java/me/tamkungz/codecmedia/internal/image/webp/WebpParser.java) with null-safe RIFF checks, declared-size bounds validation, chunk-payload bounds validation, VP8 key-frame validation, and clearer variant-specific error messages.
- Clarified runtime requirements in [`WebpCodec`](src/main/java/me/tamkungz/codecmedia/internal/image/webp/WebpCodec.java) for ImageIO WebP reader/writer SPI availability.
- Hardened BMP probing in [`BmpParser`](src/main/java/me/tamkungz/codecmedia/internal/image/bmp/BmpParser.java) with null-safe signature checks and explicit DIB header truncation validation (`14 + dibHeaderSize <= file length`).
- Added public BMP probe entrypoint [`BmpCodec.probe()`](src/main/java/me/tamkungz/codecmedia/internal/image/bmp/BmpCodec.java) to expose lightweight header probing through codec API.
- Hardened PNG probing in [`PngParser`](src/main/java/me/tamkungz/codecmedia/internal/image/png/PngParser.java) with null-safe signature checks and strict PNG-spec bitDepth/colorType combination validation.
- Added public PNG probe entrypoint [`PngCodec.probe()`](src/main/java/me/tamkungz/codecmedia/internal/image/png/PngCodec.java) to expose lightweight header probing through codec API.
- Hardened JPEG signature sniffing in [`JpegParser.isLikelyJpeg()`](src/main/java/me/tamkungz/codecmedia/internal/image/jpeg/JpegParser.java) to accept SOI-based detection (`FF D8`) with null-safe handling.
- Added public JPEG probe entrypoint [`JpegCodec.probe()`](src/main/java/me/tamkungz/codecmedia/internal/image/jpeg/JpegCodec.java) to expose header-based JPEG probing through codec API.
- Hardened TIFF signature sniffing in [`TiffParser.isLikelyTiff()`](src/main/java/me/tamkungz/codecmedia/internal/image/tiff/TiffParser.java) with null-safe handling, and documented that probe-level `BitsPerSample` reports the first component value.
- Added public TIFF probe entrypoint [`TiffCodec.probe()`](src/main/java/me/tamkungz/codecmedia/internal/image/tiff/TiffCodec.java) and clarified runtime ImageIO TIFF plugin requirements in codec documentation.
- Hardened HEIF parsing in [`HeifParser`](src/main/java/me/tamkungz/codecmedia/internal/image/heif/HeifParser.java) with null-safe signature checks, `pixi` FullBox version validation (`version == 0`), and bounded recursive box traversal depth.
- Added public HEIF probe entrypoint [`HeifCodec.probe()`](src/main/java/me/tamkungz/codecmedia/internal/image/heif/HeifCodec.java), clarified runtime ImageIO HEIF/AVIF plugin requirements, and corrected AVIF format mapping to `avif` during encode target normalization.
- Bumped artifact version to `1.2.0` in [`pom.xml`](pom.xml).

### Verified
- Confirmed MP3 decode conversion path and tests with `mvn -Dtest=Mp3PcmWavConverterTest test`.
- Confirmed pure-Java MP3 decoder regression coverage with `mvn -Dtest=PureJavaMp3DecoderTest test`.
- Confirmed FLAC parser hardening with `mvn -Dtest=FlacParserTest test`.
- Confirmed WebP parser regression coverage with `mvn -Dtest=WebpParserTest test`.
- Confirmed BMP probe/parser coverage with `mvn -Dtest=BmpParserTest test`.
- Confirmed PNG probe/parser coverage with `mvn -Dtest=PngParserTest test`.
- Confirmed JPEG probe/parser coverage with `mvn -Dtest=JpegParserTest test`.
- Confirmed TIFF probe/parser coverage with `mvn -Dtest=TiffParserTest test`.
- Confirmed HEIF probe/parser coverage with `mvn -Dtest=HeifParserTest test`.

## [1.1.5] - 2026-03-17

### Added
- Added MP3 embedded ID3v1 metadata reader/writer via [`Mp3Id3v1Tag`](src/main/java/me/tamkungz/codecmedia/internal/audio/mp3/Mp3Id3v1Tag.java) and integrated it into engine metadata read/write flow in [`StubCodecMediaEngine`](src/main/java/me/tamkungz/codecmedia/internal/StubCodecMediaEngine.java).
- Added OGG Vorbis/Opus comment metadata reader via [`OggParser.readCommentMetadata()`](src/main/java/me/tamkungz/codecmedia/internal/audio/ogg/OggParser.java) and integrated it into [`StubCodecMediaEngine.readMetadata()`](src/main/java/me/tamkungz/codecmedia/internal/StubCodecMediaEngine.java).
- Added FLAC Vorbis comment metadata reader via [`FlacParser.readVorbisCommentMetadata()`](src/main/java/me/tamkungz/codecmedia/internal/audio/flac/FlacParser.java) and integrated it into [`StubCodecMediaEngine.readMetadata()`](src/main/java/me/tamkungz/codecmedia/internal/StubCodecMediaEngine.java).
- Added Java Sound audio transcoder [`JavaSoundAudioTranscodeConverter`](src/main/java/me/tamkungz/codecmedia/internal/convert/JavaSoundAudioTranscodeConverter.java) for JDK-only target formats (`wav`, `aiff`, `au`).
- Added AIFF embedded text metadata read/write support (`NAME`, `AUTH`, `(c) `, `ANNO`) via [`AiffParser.readTextMetadata()`](src/main/java/me/tamkungz/codecmedia/internal/audio/aiff/AiffParser.java) and [`AiffParser.writeTextMetadata()`](src/main/java/me/tamkungz/codecmedia/internal/audio/aiff/AiffParser.java).
- Added MP4/MOV to M4A audio-track remux route via [`Mp4MovToM4aRemuxConverter`](src/main/java/me/tamkungz/codecmedia/internal/convert/Mp4MovToM4aRemuxConverter.java) in the video-to-audio conversion hub path.

### Changed
- Updated audio-to-audio conversion routing in [`DefaultConversionHub`](src/main/java/me/tamkungz/codecmedia/internal/convert/DefaultConversionHub.java) to use Java Sound transcoding path (while preserving explicit WAV/PCM routing).
- Expanded facade regression coverage in [`CodecMediaFacadeTest`](src/test/java/me/tamkungz/codecmedia/CodecMediaFacadeTest.java) for MP3 embedded metadata and OGG/FLAC metadata read paths.
- Standardized metadata merge behavior in [`StubCodecMediaEngine.readMetadata()`](src/main/java/me/tamkungz/codecmedia/internal/StubCodecMediaEngine.java) to treat embedded metadata as canonical and sidecar values as fallback (`putIfAbsent` for non-core keys).
- Updated metadata write behavior in [`StubCodecMediaEngine.writeMetadata()`](src/main/java/me/tamkungz/codecmedia/internal/StubCodecMediaEngine.java) so embedded-capable formats (`wav`, `mp3`, `aif`/`aiff`/`aifc`) write in-file metadata and remove stale sidecar files.
- Expanded metadata regression coverage in [`CodecMediaFacadeTest`](src/test/java/me/tamkungz/codecmedia/CodecMediaFacadeTest.java) and parser-level AIFF coverage in [`AiffParserTest`](src/test/java/me/tamkungz/codecmedia/internal/audio/aiff/AiffParserTest.java).

### Verified
- Confirmed metadata and conversion/facade behavior with `mvn -Dtest=CodecMediaFacadeTest test`.
- Confirmed AIFF embedded metadata parser and facade flows with `mvn -Dtest=AiffParserTest,CodecMediaFacadeTest test`.

## [1.1.4] - 2026-03-17

### Added
- Added WAV embedded metadata round-trip handling (RIFF `LIST/INFO`) in [`WavParser`](src/main/java/me/tamkungz/codecmedia/internal/audio/wav/WavParser.java) and engine metadata flow in [`StubCodecMediaEngine`](src/main/java/me/tamkungz/codecmedia/internal/StubCodecMediaEngine.java).
- Added internal Java sampled playback route (`java-sampled`) for WAV/AIFF-family playback in [`StubCodecMediaEngine`](src/main/java/me/tamkungz/codecmedia/internal/StubCodecMediaEngine.java).
- Added playback routing regression coverage in [`StubCodecMediaEnginePlaybackRoutingTest`](src/test/java/me/tamkungz/codecmedia/internal/StubCodecMediaEnginePlaybackRoutingTest.java).

### Changed
- Replaced temporary WAV/PCM stub converter with production path via [`WavPcmConverter`](src/main/java/me/tamkungz/codecmedia/internal/convert/WavPcmConverter.java), including real `wav -> pcm` data-chunk extraction and `pcm -> wav` container wrapping.
- Updated conversion hub wiring in [`DefaultConversionHub`](src/main/java/me/tamkungz/codecmedia/internal/convert/DefaultConversionHub.java) to route WAV/PCM through the renamed real converter.
- Added preset-driven PCM->WAV parameter parsing in [`WavPcmConverter.parsePcmWavParams()`](src/main/java/me/tamkungz/codecmedia/internal/convert/WavPcmConverter.java) supporting `sr=`, `ch=`, and `bits=`.
- Updated facade regression behavior in [`CodecMediaFacadeTest`](src/test/java/me/tamkungz/codecmedia/CodecMediaFacadeTest.java) to assert real re-encode behavior and preset-based output stream properties for WAV/PCM route.
- Updated metadata behavior to use embedded WAV `LIST/INFO` read/write for WAV inputs while keeping sidecar (`.codecmedia.properties`) persistence for non-WAV formats in [`StubCodecMediaEngine`](src/main/java/me/tamkungz/codecmedia/internal/StubCodecMediaEngine.java).
- Updated playback behavior in [`CodecMediaEngine.play()`](src/main/java/me/tamkungz/codecmedia/CodecMediaEngine.java) implementation path to prioritize internal Java sampled playback for WAV/AIFF family before desktop-open fallback.

### Fixed
- Added defensive bounds checks for little-endian reads in [`WavPcmConverter.readLeInt()`](src/main/java/me/tamkungz/codecmedia/internal/convert/WavPcmConverter.java) and [`WavPcmConverter.readLeUnsignedShort()`](src/main/java/me/tamkungz/codecmedia/internal/convert/WavPcmConverter.java).
- Added WAV `fmt ` validation before payload extraction in [`WavPcmConverter.extractWavDataChunk()`](src/main/java/me/tamkungz/codecmedia/internal/convert/WavPcmConverter.java), rejecting non-PCM WAV payload extraction.
- Hardened chunk traversal and container construction against arithmetic overflow in [`WavPcmConverter.extractWavDataChunk()`](src/main/java/me/tamkungz/codecmedia/internal/convert/WavPcmConverter.java) and [`WavPcmConverter.wrapPcmAsWav()`](src/main/java/me/tamkungz/codecmedia/internal/convert/WavPcmConverter.java).
- Fixed WAV metadata flow so write/read operations no longer rely on sidecar-only behavior for WAV files, improving in-file metadata persistence consistency.
- Fixed playback routing so WAV/AIFF-family inputs no longer depend solely on desktop integration when internal sampled playback is available.

### Verified
- Confirmed facade regression coverage with `mvn -Dtest=CodecMediaFacadeTest test`.
- Confirmed WAV metadata parser behavior with `mvn -Dtest=WavParserTest test`.
- Confirmed engine metadata and playback routing behavior with `mvn -Dtest=CodecMediaFacadeTest,StubCodecMediaEnginePlaybackRoutingTest test`.

## [1.1.3] - 2026-03-16

### Fixed
- Added strict PNG bit-depth validation in [`PngParser.parse()`](src/main/java/me/tamkungz/codecmedia/internal/image/png/PngParser.java) to reject malformed IHDR values outside the PNG spec (`1`, `2`, `4`, `8`, `16`).
- Added helper [`PngParser.isValidBitDepth()`](src/main/java/me/tamkungz/codecmedia/internal/image/png/PngParser.java) to centralize allowed PNG bit-depth values during probe.
- Added PNG color-type validation in [`PngParser.parse()`](src/main/java/me/tamkungz/codecmedia/internal/image/png/PngParser.java) with helper [`PngParser.isValidColorType()`](src/main/java/me/tamkungz/codecmedia/internal/image/png/PngParser.java) to accept only spec-valid values (`0`, `2`, `3`, `4`, `6`).
- Refined JPEG signature sniffing in [`JpegParser.isLikelyJpeg()`](src/main/java/me/tamkungz/codecmedia/internal/image/jpeg/JpegParser.java) to require the exact 3-byte SOI/prefix check actually used by the parser.
- Hardened JPEG SOF validation in [`JpegParser.parse()`](src/main/java/me/tamkungz/codecmedia/internal/image/jpeg/JpegParser.java) to reject invalid `bitsPerSample` (only `8`/`12`) and unsupported component counts (only `1`/`3`/`4`).
- Improved JPEG marker traversal in [`JpegParser.parse()`](src/main/java/me/tamkungz/codecmedia/internal/image/jpeg/JpegParser.java) to correctly tolerate repeated `0xFF` fill bytes while preserving marker alignment validation.
- Corrected HEIF `pixi` FullBox payload parsing in [`HeifParser.extractPixiBitDepth()`](src/main/java/me/tamkungz/codecmedia/internal/image/heif/HeifParser.java) by skipping the 4-byte FullBox header before reading channel count and per-channel depths.
- Documented FullBox offset semantics for `ispe` extraction in [`HeifParser.extractIspeWidth()`](src/main/java/me/tamkungz/codecmedia/internal/image/heif/HeifParser.java) and [`HeifParser.extractIspeHeight()`](src/main/java/me/tamkungz/codecmedia/internal/image/heif/HeifParser.java).
- Removed non-container boxes (`mdat`, `skip`, `free`) from recursive traversal candidates in [`HeifParser.isContainerType()`](src/main/java/me/tamkungz/codecmedia/internal/image/heif/HeifParser.java) to avoid unnecessary payload recursion.
- Added bounds-checked big-endian integer reads in [`HeifParser.readBeInt()`](src/main/java/me/tamkungz/codecmedia/internal/image/heif/HeifParser.java) to return codec-domain errors instead of runtime index failures on malformed inputs.
- Added strict BMP `bitsPerPixel` validation in [`BmpParser.parse()`](src/main/java/me/tamkungz/codecmedia/internal/image/bmp/BmpParser.java) via [`BmpParser.isValidBitsPerPixel()`](src/main/java/me/tamkungz/codecmedia/internal/image/bmp/BmpParser.java), allowing only spec-valid values (`1`, `2`, `4`, `8`, `16`, `24`, `32`).
- Added defensive TIFF IFD entry-count bounds validation in [`TiffParser.parse()`](src/main/java/me/tamkungz/codecmedia/internal/image/tiff/TiffParser.java) to reject corrupt `entryCount` values that exceed available bytes.
- Documented WebP probe bit-depth assumption in [`WebpParser`](src/main/java/me/tamkungz/codecmedia/internal/image/webp/WebpParser.java) with explicit constant [`ASSUMED_WEBP_BIT_DEPTH`](src/main/java/me/tamkungz/codecmedia/internal/image/webp/WebpParser.java), applied consistently across `VP8`, `VP8L`, and `VP8X` parsing.

### Verified
- Confirmed compile stability after PNG/JPEG/HEIF/BMP/TIFF/WebP parser hardening with `mvn -q -DskipTests compile`.

## [1.1.2] - 2026-03-15

### Added
- Added safe top-level codec helper [`ProbeResult.primaryCodec()`](src/main/java/me/tamkungz/codecmedia/model/ProbeResult.java) to avoid direct `streams().get(0)` access for empty-stream cases.
- Added no-arg [`ConversionOptions.defaults()`](src/main/java/me/tamkungz/codecmedia/options/ConversionOptions.java) with fallback target format.
- Added model/options consistency tests in [`ModelOptionsConsistencyTest`](src/test/java/me/tamkungz/codecmedia/model/ModelOptionsConsistencyTest.java).
- Added AVIF image transcode regression coverage in [`CodecMediaFacadeTest`](src/test/java/me/tamkungz/codecmedia/CodecMediaFacadeTest.java), including runtime-safe behavior when HEIF/AVIF writers are unavailable.

### Changed
- Aligned [`ConversionOptions.defaults(String)`](src/main/java/me/tamkungz/codecmedia/options/ConversionOptions.java) with fallback behavior for `null`/blank target format.
- Documented default validation policy in [`ValidationOptions.defaults()`](src/main/java/me/tamkungz/codecmedia/options/ValidationOptions.java), including 500 MiB default size limit.
- Documented nullable semantics of [`PlaybackResult.message`](src/main/java/me/tamkungz/codecmedia/model/PlaybackResult.java).
- Extended image transcode extension routing in [`ImageTranscodeConverter`](src/main/java/me/tamkungz/codecmedia/internal/convert/ImageTranscodeConverter.java) to recognize `avif` as part of the HEIF-family conversion path.
- Clarified intentional unsupported fallback behavior in [`ConversionRouteResolver`](src/main/java/me/tamkungz/codecmedia/internal/convert/ConversionRouteResolver.java) for `null`/unknown/container/non-mapped routes.
- Updated image conversion support notes in [`README.md`](README.md) to include `avif` and document intentional unsupported container/unknown route handling.
- Optimized [`StubCodecMediaEngine.probe()`](src/main/java/me/tamkungz/codecmedia/internal/StubCodecMediaEngine.java) to avoid redundant full-file reads when the probe prefix already contains the complete file.
- Reworded [`StubCodecMediaEngine.extractAudio()`](src/main/java/me/tamkungz/codecmedia/internal/StubCodecMediaEngine.java) format-mismatch error message to user-facing language (removed internal "Stub" wording).

### Verified
- Confirmed model/options polish via `mvn -Dtest=ModelOptionsConsistencyTest test`.
- Confirmed AVIF conversion-path regression with `mvn -Dtest=CodecMediaFacadeTest#convert_shouldTranscodePngToAvif test`.
- Confirmed facade behavior regression coverage with `mvn -Dtest=CodecMediaFacadeTest test`.

## [1.1.1] - 2026-03-14

### Fixed
- Improved MP3 duration estimation in [`Mp3Parser.estimateDurationMillis()`](src/main/java/me/tamkungz/codecmedia/internal/audio/mp3/Mp3Parser.java) to prioritize Xing/VBRI frame-count metadata before scanned sample totals.
- Excluded trailing ID3v1 tag bytes from MP3 audio scan range in [`Mp3Parser`](src/main/java/me/tamkungz/codecmedia/internal/audio/mp3/Mp3Parser.java), reducing bitrate drift when footer tags are present.
- Added clearer non-Layer III error handling in [`Mp3Parser.parse()`](src/main/java/me/tamkungz/codecmedia/internal/audio/mp3/Mp3Parser.java) for MPEG Layer I/II inputs.
- Strengthened OGG logical-stream parsing in [`OggParser`](src/main/java/me/tamkungz/codecmedia/internal/audio/ogg/OggParser.java) with per-stream page-sequence validation and serial-scoped metrics for multiplexed files.
- Refined Vorbis bitrate-mode classification in [`OggParser.detectVorbisBitrateMode()`](src/main/java/me/tamkungz/codecmedia/internal/audio/ogg/OggParser.java) to infer from observed bitrate variation instead of coarse nominal/page-count heuristics.
- Replaced broad OGG payload string scanning with structured Vorbis/Opus comment-header parsing in [`OggParser`](src/main/java/me/tamkungz/codecmedia/internal/audio/ogg/OggParser.java), and fixed sequence tracking to use `long` to avoid overflow.
- Updated [`WavParser`](src/main/java/me/tamkungz/codecmedia/internal/audio/wav/WavParser.java) to read/validate `audioFormat` from `fmt ` and reject unsupported compressed WAV formats instead of silently computing incorrect duration.
- Added RF64-aware WAV parsing in [`WavParser`](src/main/java/me/tamkungz/codecmedia/internal/audio/wav/WavParser.java), including unsigned chunk-size handling and `data` size sentinel (`0xFFFFFFFF`) resolution via `ds64`.
- Updated [`FlacParser`](src/main/java/me/tamkungz/codecmedia/internal/audio/flac/FlacParser.java) to reject reserved metadata block type `127` per FLAC spec.
- Updated [`FlacParser`](src/main/java/me/tamkungz/codecmedia/internal/audio/flac/FlacParser.java) bitrate estimation to use encoded audio payload region after metadata blocks (instead of whole file bytes), reducing artwork/metadata inflation.
- Updated [`AiffParser`](src/main/java/me/tamkungz/codecmedia/internal/audio/aiff/AiffParser.java) to validate AIFC `COMM` compression type and reject unsupported compressed variants.

### Added
- Added MP3 parser regression tests for Xing-priority duration, trailing ID3v1 handling, and unsupported Layer I/II diagnostics in [`Mp3ParserTest`](src/test/java/me/tamkungz/codecmedia/internal/audio/mp3/Mp3ParserTest.java).
- Added OGG parser tests for Vorbis CBR/VBR mode inference, broken page-sequence detection, and multiplexed-stream metric isolation in [`OggParserTest`](src/test/java/me/tamkungz/codecmedia/internal/audio/ogg/OggParserTest.java).
- Added WAV parser tests for unsupported compressed format rejection and RF64 `ds64`/`data` sentinel handling in [`WavParserTest`](src/test/java/me/tamkungz/codecmedia/internal/audio/wav/WavParserTest.java).
- Added FLAC parser tests for reserved block type rejection and metadata-heavy bitrate estimation behavior in [`FlacParserTest`](src/test/java/me/tamkungz/codecmedia/internal/audio/flac/FlacParserTest.java).
- Added explicit decode-only intent comment in [`FlacCodec`](src/main/java/me/tamkungz/codecmedia/internal/audio/flac/FlacCodec.java).
- Added AIFF parser tests for supported AIFC `NONE` and unsupported compression-type rejection in [`AiffParserTest`](src/test/java/me/tamkungz/codecmedia/internal/audio/aiff/AiffParserTest.java).
- Added explicit decode-only intent comment in [`AiffCodec`](src/main/java/me/tamkungz/codecmedia/internal/audio/aiff/AiffCodec.java).

### Verified
- Confirmed MP3 parser updates with `mvn -Dtest=Mp3ParserTest test`.
- Confirmed OGG parser updates with `mvn -Dtest=OggParserTest test`.
- Confirmed WAV parser updates with `mvn -Dtest=WavParserTest test`.
- Confirmed FLAC parser updates with `mvn -Dtest=FlacParserTest test`.
- Confirmed AIFF parser updates with `mvn -Dtest=AiffParserTest test`.
- Confirmed CLI argument/dispatch regression tests in [`CodecMediaCliTest`](../codecmedia-cli/src/test/java/me/tamkungz/codecmedia/CodecMediaCliTest.java) with `mvn test` (in `codecmedia-cli`).
- Confirmed Kotlin wrapper refactor stability with `gradlew.bat test` (in `codecmedia-kotlin`).

## [1.1.0] - 2026-03-13

### Changed
- Migrated repository ownership and project references to `CodecMediaLib/codecmedia-java`.
- Updated SCM/issue/CI metadata in [`pom.xml`](pom.xml).
- Updated organization metadata in [`pom.xml`](pom.xml) to `CodecMediaLib`.

### Notes
- This `1.1.0` release is a transition baseline and prepares for larger updates planned in the `1.1.*` series.

## [1.0.4] - 2026-03-05

### Added
- Added richer MOV probe fields in [`MovProbeInfo`](src/main/java/me/tamkungz/codecmedia/internal/video/mov/MovProbeInfo.java): `videoBitrateKbps`, `audioBitrateKbps`, `bitDepth`, and `displayAspectRatio`.
- Added richer MP4 probe fields in [`Mp4ProbeInfo`](src/main/java/me/tamkungz/codecmedia/internal/video/mp4/Mp4ProbeInfo.java): codec/audio stream details, frame rate, bitrate fields, bit depth, and display aspect ratio.
- Added richer WebM probe fields in [`WebmProbeInfo`](src/main/java/me/tamkungz/codecmedia/internal/video/webm/WebmProbeInfo.java): per-track bitrate fields and display aspect ratio.
- Added real fixture coverage in [`CodecMediaFacadeTest`](src/test/java/me/tamkungz/codecmedia/CodecMediaFacadeTest.java) for probe/strict-validate using files under [`src/test/resources/example`](src/test/resources/example).
- Added round-trip conversion test class [`CodecMediaRoundTripConversionTest`](src/test/java/me/tamkungz/codecmedia/CodecMediaRoundTripConversionTest.java) to validate same-extension convert->convert flows across all real example extensions (`mp3`, `mp4`, `png`, `webm`).

### Changed
- Enhanced MOV parsing in [`MovParser`](src/main/java/me/tamkungz/codecmedia/internal/video/mov/MovParser.java) with deeper BMFF track metadata extraction (`hdlr`, `mdhd`, `stsd`, `btrt`, `stsz`) and fallback bitrate estimation.
- Enhanced MP4 parsing in [`Mp4Parser`](src/main/java/me/tamkungz/codecmedia/internal/video/mp4/Mp4Parser.java) to extract video/audio codec, sample rate, channels, frame rate, bit depth, bitrate, and aspect ratio.
- Enhanced WebM parsing in [`WebmParser`](src/main/java/me/tamkungz/codecmedia/internal/video/webm/WebmParser.java) to extract track bitrate when present and compute fallback bitrate/aspect ratio values.
- Updated stream/tag mapping in [`StubCodecMediaEngine`](src/main/java/me/tamkungz/codecmedia/internal/StubCodecMediaEngine.java) so MOV/MP4/WebM probe results now expose richer stream bitrate and container tags (`displayAspectRatio`, `bitDepth`, `videoBitrateKbps`, `audioBitrateKbps`).

### Verified
- Confirmed test stability after video parser improvements with `mvn test`.
- Confirmed real-fixture conversion regression path with `mvn -Dtest=CodecMediaRoundTripConversionTest test`.

## [1.0.3] - 2026-03-05

### Added
- Added OGG Opus identification/probing support alongside Vorbis in [`OggParser`](src/main/java/me/tamkungz/codecmedia/internal/audio/ogg/OggParser.java).
- Added AIFF/AIF/AIFC probing support with new parser/codec in [`AiffParser`](src/main/java/me/tamkungz/codecmedia/internal/audio/aiff/AiffParser.java) and [`AiffCodec`](src/main/java/me/tamkungz/codecmedia/internal/audio/aiff/AiffCodec.java).
- Added FLAC probing support with STREAMINFO parsing in [`FlacParser`](src/main/java/me/tamkungz/codecmedia/internal/audio/flac/FlacParser.java) and [`FlacCodec`](src/main/java/me/tamkungz/codecmedia/internal/audio/flac/FlacCodec.java).
- Added parser test coverage for MP3 VBR and mono channel-mode paths in [`Mp3ParserTest`](src/test/java/me/tamkungz/codecmedia/internal/audio/mp3/Mp3ParserTest.java).
- Added parser test coverage for OGG Opus identification in [`OggParserTest`](src/test/java/me/tamkungz/codecmedia/internal/audio/ogg/OggParserTest.java).
- Added WAV parser synthetic profile tests (mono/stereo, sample-rate, bit-depth combinations) in [`WavParserTest`](src/test/java/me/tamkungz/codecmedia/internal/audio/wav/WavParserTest.java).
- Added parser test coverage for AIFF probing in [`AiffParserTest`](src/test/java/me/tamkungz/codecmedia/internal/audio/aiff/AiffParserTest.java).
- Added facade test coverage for `.m4a` probe/strict-validate flows in [`CodecMediaFacadeTest`](src/test/java/me/tamkungz/codecmedia/CodecMediaFacadeTest.java).
- Added parser/facade test coverage for `.flac` in [`FlacParserTest`](src/test/java/me/tamkungz/codecmedia/internal/audio/flac/FlacParserTest.java) and [`CodecMediaFacadeTest`](src/test/java/me/tamkungz/codecmedia/CodecMediaFacadeTest.java).

### Changed
- Improved probe routing in [`StubCodecMediaEngine`](src/main/java/me/tamkungz/codecmedia/internal/StubCodecMediaEngine.java) to perform lightweight prefix-based type sniffing before full-file decode, reducing unnecessary full reads for unsupported/unknown inputs.
- Extended probe and strict validation routing in [`StubCodecMediaEngine`](src/main/java/me/tamkungz/codecmedia/internal/StubCodecMediaEngine.java) to include AIFF/AIF/AIFC.
- Extended probe and strict validation routing in [`StubCodecMediaEngine`](src/main/java/me/tamkungz/codecmedia/internal/StubCodecMediaEngine.java) to include FLAC.
- Expanded MP4 signature acceptance for M4A family brands in [`Mp4Parser.isLikelyMp4()`](src/main/java/me/tamkungz/codecmedia/internal/video/mp4/Mp4Parser.java).
- Updated feature notes in [`README.md`](README.md) to reflect OGG Vorbis/Opus probing support and prefix-sniff probe behavior.

## [1.0.2] - 2026-03-02

### Added
- Added MOV parser/probe support via [`MovParser`](src/main/java/me/tamkungz/codecmedia/internal/video/mov/MovParser.java).
- Added WebM parser/probe support via [`WebmParser`](src/main/java/me/tamkungz/codecmedia/internal/video/webm/WebmParser.java).
- Added MOV/WebM codec wrappers ([`MovCodec`](src/main/java/me/tamkungz/codecmedia/internal/video/mov/MovCodec.java), [`WebmCodec`](src/main/java/me/tamkungz/codecmedia/internal/video/webm/WebmCodec.java)) and probe info records.
- Added facade tests for MOV/WebM probe and strict validation in [`CodecMediaFacadeTest`](src/test/java/me/tamkungz/codecmedia/CodecMediaFacadeTest.java).

### Changed
- Extended probing and strict validation routing in [`StubCodecMediaEngine`](src/main/java/me/tamkungz/codecmedia/internal/StubCodecMediaEngine.java) to cover MOV and WebM.
- Updated project metadata URLs and branding references in [`pom.xml`](pom.xml) and [`README.md`](README.md).

### Fixed
- Fixed EBML element decoding in [`WebmParser`](src/main/java/me/tamkungz/codecmedia/internal/video/webm/WebmParser.java) so multi-byte element IDs (including `DefaultDuration`) are parsed with correct ID/size boundaries.
- Fixed MOV frame-rate extraction in [`MovParser`](src/main/java/me/tamkungz/codecmedia/internal/video/mov/MovParser.java) to use `mdhd` track timescale when interpreting `stts` sample delta.

## [1.0.1] - 2026-03-02

### Added
- Initial public release with probing, validation, metadata sidecar persistence, extraction workflow, playback simulation, and conversion routing.
