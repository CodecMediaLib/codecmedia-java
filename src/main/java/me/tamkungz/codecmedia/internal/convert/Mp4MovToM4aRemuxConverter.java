package me.tamkungz.codecmedia.internal.convert;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import me.tamkungz.codecmedia.CodecMediaException;
import me.tamkungz.codecmedia.model.ConversionResult;

/**
 * Container-level audio remux path for MP4/MOV -> M4A without audio re-encode.
 * <p>
 * Implementation strategy is intentionally conservative and zero-dependency:
 * rewrite non-audio {@code trak} boxes in {@code moov} to {@code free} (same size),
 * preserving all chunk offsets and payload bytes.
 */
public final class Mp4MovToM4aRemuxConverter implements MediaConverter {

    @Override
    public ConversionResult convert(ConversionRequest request) throws CodecMediaException {
        String sourceExt = normalize(request.sourceExtension());
        String targetExt = normalize(request.targetExtension());
        if (!"m4a".equals(targetExt) || !("mp4".equals(sourceExt) || "mov".equals(sourceExt))) {
            throw new CodecMediaException("Only MP4/MOV -> M4A remux is supported by this converter");
        }

        Path output = request.output();
        try {
            Path parent = output.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            if (Files.exists(output) && !request.options().overwrite()) {
                throw new CodecMediaException("Output already exists and overwrite is disabled: " + output);
            }

            byte[] inputBytes = Files.readAllBytes(request.input());
            byte[] remuxed = remuxToM4aAudioOnly(inputBytes);
            Files.write(output, remuxed);
            return new ConversionResult(output, "m4a", false);
        } catch (IOException e) {
            throw new CodecMediaException("Failed to remux file: " + request.input(), e);
        }
    }

    private static byte[] remuxToM4aAudioOnly(byte[] bytes) throws CodecMediaException {
        Box moov = findTopLevelBox(bytes, "moov");
        if (moov == null) {
            throw new CodecMediaException("Cannot remux to m4a: missing moov box");
        }

        List<Box> moovChildren = parseChildBoxes(bytes, moov.payloadOffset(), moov.endOffset());
        List<Box> trakBoxes = moovChildren.stream().filter(b -> "trak".equals(b.type())).toList();
        if (trakBoxes.isEmpty()) {
            throw new CodecMediaException("Cannot remux to m4a: source has no track boxes");
        }

        List<Box> audioTracks = new ArrayList<>();
        List<Box> nonAudioTracks = new ArrayList<>();
        for (Box trak : trakBoxes) {
            String handler = findTrackHandlerType(bytes, trak);
            if ("soun".equals(handler)) {
                audioTracks.add(trak);
            } else {
                nonAudioTracks.add(trak);
            }
        }

        if (audioTracks.isEmpty()) {
            throw new CodecMediaException("Cannot remux to m4a: no audio track found in source container");
        }

        for (Box audioTrack : audioTracks) {
            String codecFourCc = findAudioSampleEntryFourCc(bytes, audioTrack);
            if (!isM4aCompatibleAudioFourCc(codecFourCc)) {
                throw new CodecMediaException(
                        "Cannot remux to m4a: source audio track codec is not m4a-compatible (found: "
                                + (codecFourCc == null ? "unknown" : codecFourCc)
                                + ")"
                );
            }
        }

        byte[] out = bytes.clone();
        for (Box nonAudio : nonAudioTracks) {
            // keep box size/payload unchanged; mark as free so players treat it as ignorable padding.
            writeAscii(out, nonAudio.offset() + 4, "free");
        }
        return out;
    }

    private static boolean isM4aCompatibleAudioFourCc(String codecFourCc) {
        if (codecFourCc == null) {
            return false;
        }
        return "mp4a".equals(codecFourCc) || "alac".equals(codecFourCc);
    }

    private static String findTrackHandlerType(byte[] bytes, Box trak) throws CodecMediaException {
        List<Box> trakChildren = parseChildBoxes(bytes, trak.payloadOffset(), trak.endOffset());
        for (Box child : trakChildren) {
            if (!"mdia".equals(child.type())) {
                continue;
            }
            List<Box> mdiaChildren = parseChildBoxes(bytes, child.payloadOffset(), child.endOffset());
            for (Box mdiaChild : mdiaChildren) {
                if (!"hdlr".equals(mdiaChild.type())) {
                    continue;
                }
                // full box: version+flags (4), pre_defined (4), handler_type (4)
                int handlerOffset = mdiaChild.payloadOffset() + 8;
                if (handlerOffset + 4 <= mdiaChild.endOffset()) {
                    return readAscii(bytes, handlerOffset, 4);
                }
            }
        }
        return null;
    }

    private static String findAudioSampleEntryFourCc(byte[] bytes, Box trak) throws CodecMediaException {
        List<Box> trakChildren = parseChildBoxes(bytes, trak.payloadOffset(), trak.endOffset());
        for (Box child : trakChildren) {
            if (!"mdia".equals(child.type())) {
                continue;
            }
            List<Box> mdiaChildren = parseChildBoxes(bytes, child.payloadOffset(), child.endOffset());
            for (Box mdiaChild : mdiaChildren) {
                if (!"minf".equals(mdiaChild.type())) {
                    continue;
                }
                List<Box> minfChildren = parseChildBoxes(bytes, mdiaChild.payloadOffset(), mdiaChild.endOffset());
                for (Box minfChild : minfChildren) {
                    if (!"stbl".equals(minfChild.type())) {
                        continue;
                    }
                    List<Box> stblChildren = parseChildBoxes(bytes, minfChild.payloadOffset(), minfChild.endOffset());
                    for (Box stblChild : stblChildren) {
                        if (!"stsd".equals(stblChild.type())) {
                            continue;
                        }
                        int payload = stblChild.payloadOffset();
                        if (payload + 16 > stblChild.endOffset()) {
                            return null;
                        }
                        long entryCount = readUInt32(bytes, payload + 4);
                        if (entryCount <= 0) {
                            return null;
                        }
                        int firstEntryOffset = payload + 8;
                        if (firstEntryOffset + 8 > stblChild.endOffset()) {
                            return null;
                        }
                        return readAscii(bytes, firstEntryOffset + 4, 4);
                    }
                }
            }
        }
        return null;
    }

    private static Box findTopLevelBox(byte[] bytes, String type) throws CodecMediaException {
        List<Box> boxes = parseChildBoxes(bytes, 0, bytes.length);
        for (Box box : boxes) {
            if (type.equals(box.type())) {
                return box;
            }
        }
        return null;
    }

    private static List<Box> parseChildBoxes(byte[] bytes, int start, int endExclusive) throws CodecMediaException {
        if (start < 0 || endExclusive < start || endExclusive > bytes.length) {
            throw new CodecMediaException("Invalid BMFF parse range");
        }
        List<Box> out = new ArrayList<>();
        int cursor = start;
        while (cursor + 8 <= endExclusive) {
            long size32 = readUInt32(bytes, cursor);
            String type = readAscii(bytes, cursor + 4, 4);
            int headerSize = 8;
            long boxSize = size32;
            if (size32 == 1L) {
                if (cursor + 16 > endExclusive) {
                    throw new CodecMediaException("Invalid extended BMFF box header for type: " + type);
                }
                boxSize = readUInt64(bytes, cursor + 8);
                headerSize = 16;
            } else if (size32 == 0L) {
                boxSize = endExclusive - cursor;
            }
            if (boxSize < headerSize) {
                throw new CodecMediaException("Invalid BMFF box size for type: " + type);
            }
            long next = cursor + boxSize;
            if (next > endExclusive || next > Integer.MAX_VALUE) {
                throw new CodecMediaException("BMFF box exceeds bounds for type: " + type);
            }

            int boxEnd = (int) next;
            out.add(new Box(cursor, boxEnd, headerSize, type));
            cursor = boxEnd;
        }
        if (cursor != endExclusive) {
            // trailing bytes are tolerated only when small padding was explicitly encoded as free/skip boxes.
            // here we require clean boundaries for deterministic remux safety.
            throw new CodecMediaException("Invalid BMFF box layout: unaligned payload near byte " + cursor);
        }
        return out;
    }

    private static String normalize(String ext) {
        if (ext == null) {
            return "";
        }
        String out = ext.trim().toLowerCase(Locale.ROOT);
        return out.startsWith(".") ? out.substring(1) : out;
    }

    private static long readUInt32(byte[] bytes, int offset) throws CodecMediaException {
        if (offset < 0 || offset + 4 > bytes.length) {
            throw new CodecMediaException("Unexpected end of BMFF data while reading uint32");
        }
        return ((long) (bytes[offset] & 0xFF) << 24)
                | ((long) (bytes[offset + 1] & 0xFF) << 16)
                | ((long) (bytes[offset + 2] & 0xFF) << 8)
                | ((long) (bytes[offset + 3] & 0xFF));
    }

    private static long readUInt64(byte[] bytes, int offset) throws CodecMediaException {
        if (offset < 0 || offset + 8 > bytes.length) {
            throw new CodecMediaException("Unexpected end of BMFF data while reading uint64");
        }
        return ((long) (bytes[offset] & 0xFF) << 56)
                | ((long) (bytes[offset + 1] & 0xFF) << 48)
                | ((long) (bytes[offset + 2] & 0xFF) << 40)
                | ((long) (bytes[offset + 3] & 0xFF) << 32)
                | ((long) (bytes[offset + 4] & 0xFF) << 24)
                | ((long) (bytes[offset + 5] & 0xFF) << 16)
                | ((long) (bytes[offset + 6] & 0xFF) << 8)
                | ((long) (bytes[offset + 7] & 0xFF));
    }

    private static String readAscii(byte[] bytes, int offset, int length) {
        if (offset < 0 || offset + length > bytes.length) {
            return "";
        }
        return new String(bytes, offset, length, StandardCharsets.US_ASCII);
    }

    private static void writeAscii(byte[] bytes, int offset, String value) {
        byte[] encoded = value.getBytes(StandardCharsets.US_ASCII);
        for (int i = 0; i < encoded.length; i++) {
            bytes[offset + i] = encoded[i];
        }
    }

    private record Box(int offset, int endOffset, int headerSize, String type) {
        int payloadOffset() {
            return offset + headerSize;
        }
    }
}

