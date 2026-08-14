package me.tamkungz.codecmedia.internal.audio.aiff;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import me.tamkungz.codecmedia.CodecMediaException;
import me.tamkungz.codecmedia.internal.audio.BitrateMode;

public final class AiffParser {

    private static final String AIFC_COMPRESSION_NONE = "NONE";
    private static final String AIFC_COMPRESSION_SOWT = "sowt";
    private static final Map<String, String> TEXT_CHUNK_TO_METADATA = Map.of(
            "NAME", "title",
            "AUTH", "artist",
            "(c) ", "copyright",
            "ANNO", "comment"
    );
    private static final String[] METADATA_WRITE_ORDER = {"title", "artist", "copyright", "comment"};

    private AiffParser() {
    }

    public static AiffProbeInfo parse(byte[] bytes) throws CodecMediaException {
        if (!isLikelyAiff(bytes)) {
            throw new CodecMediaException("Not an AIFF file");
        }

        boolean aifc = bytes[11] == 'C';
        int offset = 12;
        Integer channels = null;
        Integer bitsPerSample = null;
        Integer sampleRate = null;
        Long frameCount = null;

        while (offset + 8 <= bytes.length) {
            String chunkId = readAscii(bytes, offset, 4);
            int chunkSize = readBeInt(bytes, offset + 4);
            if (chunkSize < 0) {
                throw new CodecMediaException("Invalid AIFF chunk size: " + chunkSize);
            }

            int chunkDataStart = offset + 8;
            if (chunkDataStart + chunkSize > bytes.length) {
                throw new CodecMediaException("AIFF chunk exceeds file bounds: " + chunkId);
            }

            if ("COMM".equals(chunkId)) {
                if (chunkSize < 18) {
                    throw new CodecMediaException("AIFF COMM chunk too small");
                }
                channels = readBeShort(bytes, chunkDataStart);
                frameCount = readBeUInt32(bytes, chunkDataStart + 2);
                bitsPerSample = readBeShort(bytes, chunkDataStart + 6);
                sampleRate = decodeExtended80ToIntHz(bytes, chunkDataStart + 8);

                if (aifc) {
                    if (chunkSize < 22) {
                        throw new CodecMediaException("AIFC COMM chunk missing compression type");
                    }
                    String compressionType = readAscii(bytes, chunkDataStart + 18, 4);
                    validateAifcCompressionType(compressionType);
                }
            }

            int padded = (chunkSize % 2 == 0) ? chunkSize : chunkSize + 1;
            offset = chunkDataStart + padded;
        }

        if (channels == null || bitsPerSample == null || sampleRate == null || frameCount == null) {
            throw new CodecMediaException("AIFF missing required COMM chunk fields");
        }
        if (channels <= 0 || bitsPerSample <= 0 || sampleRate <= 0 || frameCount < 0) {
            throw new CodecMediaException("Invalid AIFF format values");
        }

        long durationMillis = (frameCount * 1000L) / sampleRate;
        long byteRate = (long) sampleRate * channels * bitsPerSample / 8L;
        int bitrateKbps = (int) ((byteRate * 8L) / 1000L);

        return new AiffProbeInfo(durationMillis, bitrateKbps, sampleRate, channels, BitrateMode.CBR);
    }

    private static void validateAifcCompressionType(String compressionType) throws CodecMediaException {
        if (AIFC_COMPRESSION_NONE.equals(compressionType) || AIFC_COMPRESSION_SOWT.equals(compressionType)) {
            return;
        }
        throw new CodecMediaException("Unsupported AIFC compression type: " + compressionType);
    }

    public static boolean isLikelyAiff(byte[] bytes) {
        return bytes != null
                && bytes.length >= 12
                && bytes[0] == 'F'
                && bytes[1] == 'O'
                && bytes[2] == 'R'
                && bytes[3] == 'M'
                && bytes[8] == 'A'
                && bytes[9] == 'I'
                && bytes[10] == 'F'
                && (bytes[11] == 'F' || bytes[11] == 'C');
    }

    public static Map<String, String> readTextMetadata(byte[] bytes) throws CodecMediaException {
        if (!isLikelyAiff(bytes)) {
            throw new CodecMediaException("Not an AIFF file");
        }

        Map<String, String> out = new LinkedHashMap<>();
        int offset = 12;
        while (offset + 8 <= bytes.length) {
            String chunkId = readAscii(bytes, offset, 4);
            int chunkSize = readBeInt(bytes, offset + 4);
            if (chunkSize < 0) {
                throw new CodecMediaException("Invalid AIFF chunk size: " + chunkSize);
            }

            int chunkDataStart = offset + 8;
            long chunkDataEndLong = (long) chunkDataStart + chunkSize;
            if (chunkDataEndLong < chunkDataStart || chunkDataEndLong > bytes.length) {
                throw new CodecMediaException("AIFF chunk exceeds file bounds: " + chunkId);
            }

            String key = TEXT_CHUNK_TO_METADATA.get(chunkId);
            if (key != null) {
                int chunkDataEnd = (int) chunkDataEndLong;
                while (chunkDataEnd > chunkDataStart
                        && (bytes[chunkDataEnd - 1] == 0 || bytes[chunkDataEnd - 1] == ' ' || bytes[chunkDataEnd - 1] == '\n' || bytes[chunkDataEnd - 1] == '\r')) {
                    chunkDataEnd--;
                }
                String value = new String(bytes, chunkDataStart, chunkDataEnd - chunkDataStart, StandardCharsets.UTF_8).trim();
                if (!value.isEmpty()) {
                    out.put(key, value);
                }
            }

            int padded = (chunkSize & 1) == 0 ? chunkSize : chunkSize + 1;
            long nextOffsetLong = (long) chunkDataStart + padded;
            if (nextOffsetLong < chunkDataStart || nextOffsetLong > bytes.length || nextOffsetLong > Integer.MAX_VALUE) {
                throw new CodecMediaException("AIFF chunk exceeds file bounds: " + chunkId);
            }
            offset = (int) nextOffsetLong;
        }
        return out;
    }

    public static byte[] writeTextMetadata(byte[] bytes, Map<String, String> metadataEntries) throws CodecMediaException {
        if (!isLikelyAiff(bytes)) {
            throw new CodecMediaException("Not an AIFF file");
        }

        List<byte[]> keptChunks = new ArrayList<>();
        int offset = 12;
        while (offset + 8 <= bytes.length) {
            String chunkId = readAscii(bytes, offset, 4);
            int chunkSize = readBeInt(bytes, offset + 4);
            if (chunkSize < 0) {
                throw new CodecMediaException("Invalid AIFF chunk size: " + chunkSize);
            }

            int chunkDataStart = offset + 8;
            long chunkDataEndLong = (long) chunkDataStart + chunkSize;
            if (chunkDataEndLong < chunkDataStart || chunkDataEndLong > bytes.length) {
                throw new CodecMediaException("AIFF chunk exceeds file bounds: " + chunkId);
            }

            int padded = (chunkSize & 1) == 0 ? chunkSize : chunkSize + 1;
            long nextOffsetLong = (long) chunkDataStart + padded;
            if (nextOffsetLong < chunkDataStart || nextOffsetLong > bytes.length || nextOffsetLong > Integer.MAX_VALUE) {
                throw new CodecMediaException("AIFF chunk exceeds file bounds: " + chunkId);
            }

            if (!isManagedTextChunk(chunkId)) {
                int len = (int) (nextOffsetLong - offset);
                byte[] rawChunk = new byte[len];
                System.arraycopy(bytes, offset, rawChunk, 0, len);
                keptChunks.add(rawChunk);
            }
            offset = (int) nextOffsetLong;
        }

        List<byte[]> textChunks = buildTextChunks(metadataEntries);

        long total = 12L;
        for (byte[] chunk : keptChunks) {
            total += chunk.length;
        }
        for (byte[] chunk : textChunks) {
            total += chunk.length;
        }
        if (total > Integer.MAX_VALUE) {
            throw new CodecMediaException("AIFF file is too large after metadata write");
        }

        byte[] out = new byte[(int) total];
        out[0] = 'F';
        out[1] = 'O';
        out[2] = 'R';
        out[3] = 'M';
        writeBeInt(out, 4, (int) (total - 8));
        out[8] = 'A';
        out[9] = 'I';
        out[10] = 'F';
        out[11] = bytes[11];

        int outOffset = 12;
        for (byte[] chunk : keptChunks) {
            System.arraycopy(chunk, 0, out, outOffset, chunk.length);
            outOffset += chunk.length;
        }
        for (byte[] chunk : textChunks) {
            System.arraycopy(chunk, 0, out, outOffset, chunk.length);
            outOffset += chunk.length;
        }
        return out;
    }

    private static int decodeExtended80ToIntHz(byte[] bytes, int offset) throws CodecMediaException {
        if (offset + 10 > bytes.length) {
            throw new CodecMediaException("Unexpected end of AIFF data");
        }

        int exp = ((bytes[offset] & 0x7F) << 8) | (bytes[offset + 1] & 0xFF);
        long mantissa = 0;
        for (int i = 0; i < 8; i++) {
            mantissa = (mantissa << 8) | (bytes[offset + 2 + i] & 0xFFL);
        }

        if (exp == 0 || mantissa == 0) {
            return 0;
        }

        int shift = exp - 16383 - 63;
        long value;
        if (shift >= 0) {
            value = mantissa << Math.min(shift, 30);
        } else {
            value = mantissa >>> Math.min(-shift, 63);
        }

        if (value <= 0 || value > Integer.MAX_VALUE) {
            throw new CodecMediaException("Unsupported AIFF sample rate encoding");
        }
        return (int) value;
    }

    private static String readAscii(byte[] bytes, int offset, int len) throws CodecMediaException {
        if (offset + len > bytes.length) {
            throw new CodecMediaException("Unexpected end of AIFF data");
        }
        return new String(bytes, offset, len, StandardCharsets.US_ASCII);
    }

    private static boolean isManagedTextChunk(String chunkId) {
        return TEXT_CHUNK_TO_METADATA.containsKey(chunkId);
    }

    private static List<byte[]> buildTextChunks(Map<String, String> metadataEntries) throws CodecMediaException {
        List<byte[]> chunks = new ArrayList<>();
        if (metadataEntries == null || metadataEntries.isEmpty()) {
            return chunks;
        }

        for (String metadataKey : METADATA_WRITE_ORDER) {
            String value = metadataEntries.get(metadataKey);
            if (value == null || value.isBlank()) {
                continue;
            }

            String chunkId = metadataToChunkId(metadataKey);
            if (chunkId == null) {
                continue;
            }

            byte[] data = value.getBytes(StandardCharsets.UTF_8);
            if (data.length > Integer.MAX_VALUE - 8) {
                throw new CodecMediaException("AIFF metadata entry too large: " + metadataKey);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.writeBytes(chunkId.getBytes(StandardCharsets.US_ASCII));
            writeBeInt(out, data.length);
            out.writeBytes(data);
            if ((data.length & 1) != 0) {
                out.write(0);
            }
            chunks.add(out.toByteArray());
        }

        return chunks;
    }

    private static String metadataToChunkId(String metadataKey) {
        for (Map.Entry<String, String> entry : TEXT_CHUNK_TO_METADATA.entrySet()) {
            if (entry.getValue().equals(metadataKey)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private static int readBeShort(byte[] bytes, int offset) throws CodecMediaException {
        if (offset + 2 > bytes.length) {
            throw new CodecMediaException("Unexpected end of AIFF data");
        }
        return ((bytes[offset] & 0xFF) << 8) | (bytes[offset + 1] & 0xFF);
    }

    private static int readBeInt(byte[] bytes, int offset) throws CodecMediaException {
        if (offset + 4 > bytes.length) {
            throw new CodecMediaException("Unexpected end of AIFF data");
        }
        return ((bytes[offset] & 0xFF) << 24)
                | ((bytes[offset + 1] & 0xFF) << 16)
                | ((bytes[offset + 2] & 0xFF) << 8)
                | (bytes[offset + 3] & 0xFF);
    }

    private static long readBeUInt32(byte[] bytes, int offset) throws CodecMediaException {
        return readBeInt(bytes, offset) & 0xFFFFFFFFL;
    }

    private static void writeBeInt(byte[] out, int offset, int value) {
        out[offset] = (byte) ((value >>> 24) & 0xFF);
        out[offset + 1] = (byte) ((value >>> 16) & 0xFF);
        out[offset + 2] = (byte) ((value >>> 8) & 0xFF);
        out[offset + 3] = (byte) (value & 0xFF);
    }

    private static void writeBeInt(ByteArrayOutputStream out, int value) {
        out.write((value >>> 24) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }
}

