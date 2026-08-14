package me.tamkungz.codecmedia.internal.audio.wav;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import me.tamkungz.codecmedia.CodecMediaException;
import me.tamkungz.codecmedia.internal.audio.BitrateMode;

public final class WavParser {

    private static final int WAVE_FORMAT_PCM = 0x0001;
    private static final int WAVE_FORMAT_IEEE_FLOAT = 0x0003;
    private static final int WAVE_FORMAT_EXTENSIBLE = 0xFFFE;
    private static final Map<String, String> INFO_TO_METADATA = Map.of(
            "INAM", "title",
            "IART", "artist",
            "IPRD", "album",
            "ICMT", "comment",
            "ICRD", "date",
            "IGNR", "genre"
    );
    private static final String[] METADATA_WRITE_ORDER = {"title", "artist", "album", "comment", "date", "genre"};

    private WavParser() {
    }

    public static WavProbeInfo parse(byte[] bytes) throws CodecMediaException {
        if (!isLikelyWav(bytes)) {
            throw new CodecMediaException("Not a WAV/RIFF file");
        }

        boolean isRf64 = bytes[0] == 'R' && bytes[1] == 'F' && bytes[2] == '6' && bytes[3] == '4';
        int offset = 12;
        Integer audioFormat = null;
        Integer channels = null;
        Integer sampleRate = null;
        Long avgByteRate = null;
        Integer bitsPerSample = null;
        Long dataSize = null;
        Long ds64DataSize = null;

        while (offset + 8 <= bytes.length) {
            String chunkId = new String(bytes, offset, 4, StandardCharsets.US_ASCII);
            long chunkSize = readLeUInt32(bytes, offset + 4);

            int chunkDataStart = offset + 8;
            long chunkDataEnd = chunkDataStart + chunkSize;
            if (chunkDataEnd < chunkDataStart || chunkDataEnd > bytes.length) {
                if (!(isRf64 && "data".equals(chunkId) && chunkSize == 0xFFFFFFFFL && ds64DataSize != null)) {
                    throw new CodecMediaException("WAV chunk exceeds file bounds: " + chunkId);
                }
            }

            if ("ds64".equals(chunkId)) {
                if (chunkSize < 16) {
                    throw new CodecMediaException("Invalid RF64 ds64 chunk");
                }
                long parsedDataSize = readLeLong(bytes, chunkDataStart + 8);
                if (parsedDataSize < 0) {
                    throw new CodecMediaException("RF64 data size is too large");
                }
                ds64DataSize = parsedDataSize;
            } else if ("fmt ".equals(chunkId)) {
                if (chunkSize < 16) {
                    throw new CodecMediaException("WAV fmt chunk is too small");
                }
                audioFormat = readLeShort(bytes, chunkDataStart);
                channels = readLeShort(bytes, chunkDataStart + 2);
                sampleRate = readLeInt(bytes, chunkDataStart + 4);
                avgByteRate = readLeUInt32(bytes, chunkDataStart + 8);
                bitsPerSample = readLeShort(bytes, chunkDataStart + 14);

                validateSupportedAudioFormat(audioFormat, bytes, chunkDataStart, chunkSize);
            } else if ("data".equals(chunkId)) {
                if (chunkSize == 0xFFFFFFFFL && isRf64) {
                    if (ds64DataSize == null) {
                        throw new CodecMediaException("RF64 data chunk uses 0xFFFFFFFF size but ds64 is missing");
                    }
                    dataSize = ds64DataSize;
                } else {
                    dataSize = chunkSize;
                }
            }

            long effectiveChunkSize = chunkSize;
            if (isRf64 && "data".equals(chunkId) && chunkSize == 0xFFFFFFFFL) {
                long available = bytes.length - chunkDataStart;
                long expected = ds64DataSize != null ? ds64DataSize : available;
                if (expected < 0 || expected > available) {
                    throw new CodecMediaException("RF64 data chunk exceeds file bounds");
                }
                effectiveChunkSize = expected;
            }

            long padded = (effectiveChunkSize % 2 == 0) ? effectiveChunkSize : effectiveChunkSize + 1;
            long nextOffset = chunkDataStart + padded;
            if (nextOffset < chunkDataStart || nextOffset > bytes.length || nextOffset > Integer.MAX_VALUE) {
                throw new CodecMediaException("WAV chunk exceeds file bounds: " + chunkId);
            }
            offset = (int) nextOffset;
        }

        if (audioFormat == null || channels == null || sampleRate == null || bitsPerSample == null || dataSize == null) {
            throw new CodecMediaException("WAV is missing required fmt/data chunks");
        }
        if (channels <= 0 || sampleRate <= 0 || bitsPerSample <= 0) {
            throw new CodecMediaException("Invalid WAV format values");
        }

        long computedByteRate = (long) sampleRate * channels * bitsPerSample / 8L;
        long byteRate = (avgByteRate != null && avgByteRate > 0) ? avgByteRate : computedByteRate;
        if (byteRate <= 0) {
            throw new CodecMediaException("Invalid WAV byte rate");
        }

        long durationMillis = (dataSize * 1000L) / byteRate;
        int bitrateKbps = (int) ((byteRate * 8L) / 1000L);
        return new WavProbeInfo(durationMillis, bitrateKbps, sampleRate, channels, BitrateMode.CBR);
    }

    public static boolean isLikelyWav(byte[] bytes) {
        return bytes != null && bytes.length >= 12
                && ((bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F')
                || (bytes[0] == 'R' && bytes[1] == 'F' && bytes[2] == '6' && bytes[3] == '4'))
                && bytes[8] == 'W'
                && bytes[9] == 'A'
                && bytes[10] == 'V'
                && bytes[11] == 'E';
    }

    public static Map<String, String> readInfoMetadata(byte[] bytes) throws CodecMediaException {
        if (!isLikelyWav(bytes)) {
            throw new CodecMediaException("Not a WAV/RIFF file");
        }

        boolean isRf64 = bytes[0] == 'R' && bytes[1] == 'F' && bytes[2] == '6' && bytes[3] == '4';
        Long ds64DataSize = null;
        Map<String, String> out = new LinkedHashMap<>();
        int offset = 12;
        while (offset + 8 <= bytes.length) {
            String chunkId = readAscii(bytes, offset, 4);
            long rawChunkSize = readLeUInt32(bytes, offset + 4);
            int chunkDataStart = offset + 8;

            if ("ds64".equals(chunkId)) {
                if (!isRf64 || rawChunkSize < 16 || (long) chunkDataStart + 16 > bytes.length) {
                    throw new CodecMediaException("Invalid RF64 ds64 chunk");
                }
                long parsedDataSize = readLeLong(bytes, chunkDataStart + 8);
                if (parsedDataSize < 0) {
                    throw new CodecMediaException("RF64 data size is too large");
                }
                ds64DataSize = parsedDataSize;
            }

            long chunkSize = rawChunkSize;
            if (isRf64 && "data".equals(chunkId) && rawChunkSize == 0xFFFFFFFFL) {
                if (ds64DataSize == null) {
                    throw new CodecMediaException("RF64 data chunk uses 0xFFFFFFFF size but ds64 is missing");
                }
                chunkSize = ds64DataSize;
            }

            long chunkDataEnd = chunkDataStart + chunkSize;
            if (chunkDataEnd < chunkDataStart || chunkDataEnd > bytes.length) {
                throw new CodecMediaException("WAV chunk exceeds file bounds: " + chunkId);
            }

            if ("LIST".equals(chunkId) && chunkSize >= 4) {
                String listType = readAscii(bytes, chunkDataStart, 4);
                if ("INFO".equals(listType)) {
                    readInfoListEntries(bytes, chunkDataStart + 4, (int) chunkDataEnd, out);
                }
            }

            long padded = (chunkSize % 2 == 0) ? chunkSize : chunkSize + 1;
            long nextOffset = chunkDataStart + padded;
            if (nextOffset < chunkDataStart || nextOffset > bytes.length || nextOffset > Integer.MAX_VALUE) {
                throw new CodecMediaException("WAV chunk exceeds file bounds: " + chunkId);
            }
            offset = (int) nextOffset;
        }

        return out;
    }

    public static byte[] writeInfoMetadata(byte[] bytes, Map<String, String> metadataEntries) throws CodecMediaException {
        if (!isLikelyWav(bytes)) {
            throw new CodecMediaException("Not a WAV/RIFF file");
        }
        if (bytes[0] == 'R' && bytes[1] == 'F' && bytes[2] == '6' && bytes[3] == '4') {
            throw new CodecMediaException("RF64 metadata writing is not supported");
        }

        List<byte[]> keptChunks = new ArrayList<>();
        int offset = 12;
        while (offset + 8 <= bytes.length) {
            String chunkId = readAscii(bytes, offset, 4);
            long chunkSize = readLeUInt32(bytes, offset + 4);
            int chunkDataStart = offset + 8;
            long chunkDataEnd = chunkDataStart + chunkSize;
            if (chunkDataEnd < chunkDataStart || chunkDataEnd > bytes.length) {
                throw new CodecMediaException("WAV chunk exceeds file bounds: " + chunkId);
            }

            boolean isInfoList = false;
            if ("LIST".equals(chunkId) && chunkSize >= 4) {
                String listType = readAscii(bytes, chunkDataStart, 4);
                isInfoList = "INFO".equals(listType);
            }

            long padded = (chunkSize % 2 == 0) ? chunkSize : chunkSize + 1;
            long nextOffset = chunkDataStart + padded;
            if (nextOffset < chunkDataStart || nextOffset > bytes.length || nextOffset > Integer.MAX_VALUE) {
                throw new CodecMediaException("WAV chunk exceeds file bounds: " + chunkId);
            }

            if (!isInfoList) {
                int length = (int) (nextOffset - offset);
                byte[] rawChunk = new byte[length];
                System.arraycopy(bytes, offset, rawChunk, 0, length);
                keptChunks.add(rawChunk);
            }
            offset = (int) nextOffset;
        }

        byte[] infoChunk = buildInfoListChunk(metadataEntries);
        long total = 12L;
        for (byte[] chunk : keptChunks) {
            total += chunk.length;
        }
        if (infoChunk != null) {
            total += infoChunk.length;
        }
        if (total > Integer.MAX_VALUE) {
            throw new CodecMediaException("WAV file is too large after metadata write");
        }

        byte[] out = new byte[(int) total];
        out[0] = 'R';
        out[1] = 'I';
        out[2] = 'F';
        out[3] = 'F';
        writeLeInt(out, 4, (int) (total - 8));
        out[8] = 'W';
        out[9] = 'A';
        out[10] = 'V';
        out[11] = 'E';

        int outOffset = 12;
        for (byte[] chunk : keptChunks) {
            System.arraycopy(chunk, 0, out, outOffset, chunk.length);
            outOffset += chunk.length;
        }
        if (infoChunk != null) {
            System.arraycopy(infoChunk, 0, out, outOffset, infoChunk.length);
        }
        return out;
    }

    private static void validateSupportedAudioFormat(int audioFormat, byte[] bytes, int fmtOffset, long fmtChunkSize)
            throws CodecMediaException {
        if (audioFormat == WAVE_FORMAT_PCM || audioFormat == WAVE_FORMAT_IEEE_FLOAT) {
            return;
        }
        if (audioFormat != WAVE_FORMAT_EXTENSIBLE) {
            throw new CodecMediaException("Unsupported WAV audio format: 0x" + Integer.toHexString(audioFormat));
        }

        if (fmtChunkSize < 40) {
            throw new CodecMediaException("Invalid WAV extensible fmt chunk");
        }
        int cbSize = readLeShort(bytes, fmtOffset + 16);
        if (cbSize < 22) {
            throw new CodecMediaException("Invalid WAV extensible fmt extension size");
        }

        int subFormatOffset = fmtOffset + 24;
        int subType = readLeShort(bytes, subFormatOffset);
        // KSDATAFORMAT_SUBTYPE_PCM / KSDATAFORMAT_SUBTYPE_IEEE_FLOAT use
        // {0000000x-0000-0010-8000-00AA00389B71}. The GUID is stored with
        // little-endian Data1/Data2/Data3 fields, so validate all 16 bytes at
        // their actual offsets instead of the previously shifted tail check.
        boolean validGuid = bytes[subFormatOffset + 2] == 0
                && bytes[subFormatOffset + 3] == 0
                && bytes[subFormatOffset + 4] == 0
                && bytes[subFormatOffset + 5] == 0
                && (bytes[subFormatOffset + 6] & 0xFF) == 0x10
                && bytes[subFormatOffset + 7] == 0
                && (bytes[subFormatOffset + 8] & 0xFF) == 0x80
                && bytes[subFormatOffset + 9] == 0
                && bytes[subFormatOffset + 10] == 0
                && (bytes[subFormatOffset + 11] & 0xFF) == 0xAA
                && bytes[subFormatOffset + 12] == 0
                && (bytes[subFormatOffset + 13] & 0xFF) == 0x38
                && (bytes[subFormatOffset + 14] & 0xFF) == 0x9B
                && bytes[subFormatOffset + 15] == 0x71;
        if (!validGuid || (subType != WAVE_FORMAT_PCM && subType != WAVE_FORMAT_IEEE_FLOAT)) {
            throw new CodecMediaException("Unsupported WAV extensible sub-format: 0x" + Integer.toHexString(subType));
        }
    }

    private static int readLeShort(byte[] bytes, int offset) throws CodecMediaException {
        if (offset + 2 > bytes.length) {
            throw new CodecMediaException("Unexpected end of WAV data");
        }
        return (bytes[offset] & 0xFF) | ((bytes[offset + 1] & 0xFF) << 8);
    }

    private static int readLeInt(byte[] bytes, int offset) throws CodecMediaException {
        if (offset + 4 > bytes.length) {
            throw new CodecMediaException("Unexpected end of WAV data");
        }
        return (bytes[offset] & 0xFF)
                | ((bytes[offset + 1] & 0xFF) << 8)
                | ((bytes[offset + 2] & 0xFF) << 16)
                | ((bytes[offset + 3] & 0xFF) << 24);
    }

    private static long readLeUInt32(byte[] bytes, int offset) throws CodecMediaException {
        return readLeInt(bytes, offset) & 0xFFFFFFFFL;
    }

    private static void readInfoListEntries(byte[] bytes, int offset, int limit, Map<String, String> out)
            throws CodecMediaException {
        int pos = offset;
        while (pos + 8 <= limit) {
            String id = readAscii(bytes, pos, 4);
            long size = readLeUInt32(bytes, pos + 4);
            int dataStart = pos + 8;
            long dataEndLong = dataStart + size;
            if (dataEndLong < dataStart || dataEndLong > limit) {
                throw new CodecMediaException("WAV INFO chunk exceeds LIST bounds: " + id);
            }

            String key = INFO_TO_METADATA.get(id);
            if (key != null) {
                int dataEnd = (int) dataEndLong;
                int effectiveEnd = dataEnd;
                while (effectiveEnd > dataStart && bytes[effectiveEnd - 1] == 0) {
                    effectiveEnd--;
                }
                String value = new String(bytes, dataStart, effectiveEnd - dataStart, StandardCharsets.UTF_8).trim();
                if (!value.isEmpty()) {
                    out.put(key, value);
                }
            }

            long padded = (size % 2 == 0) ? size : size + 1;
            long next = dataStart + padded;
            if (next < dataStart || next > limit || next > Integer.MAX_VALUE) {
                throw new CodecMediaException("WAV INFO chunk exceeds LIST bounds: " + id);
            }
            pos = (int) next;
        }
    }

    private static byte[] buildInfoListChunk(Map<String, String> metadataEntries) throws CodecMediaException {
        if (metadataEntries == null || metadataEntries.isEmpty()) {
            return null;
        }

        ByteArrayOutputStream listPayload = new ByteArrayOutputStream();
        writeAscii(listPayload, "INFO");
        for (String metadataKey : METADATA_WRITE_ORDER) {
            String value = metadataEntries.get(metadataKey);
            if (value == null) {
                continue;
            }
            String infoId = metadataToInfoId(metadataKey);
            if (infoId == null) {
                continue;
            }

            byte[] valueBytes = value.getBytes(StandardCharsets.UTF_8);
            int dataSize = valueBytes.length + 1;
            writeAscii(listPayload, infoId);
            writeLeInt(listPayload, dataSize);
            listPayload.writeBytes(valueBytes);
            listPayload.write(0);
            if ((dataSize & 1) != 0) {
                listPayload.write(0);
            }
        }

        byte[] payload = listPayload.toByteArray();
        if (payload.length <= 4) {
            return null;
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeAscii(out, "LIST");
        writeLeInt(out, payload.length);
        out.writeBytes(payload);
        if ((payload.length & 1) != 0) {
            out.write(0);
        }
        return out.toByteArray();
    }

    private static String metadataToInfoId(String metadataKey) {
        for (Map.Entry<String, String> entry : INFO_TO_METADATA.entrySet()) {
            if (entry.getValue().equals(metadataKey)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private static String readAscii(byte[] bytes, int offset, int length) throws CodecMediaException {
        if (offset < 0 || offset + length > bytes.length) {
            throw new CodecMediaException("Unexpected end of WAV data");
        }
        return new String(bytes, offset, length, StandardCharsets.US_ASCII);
    }

    private static void writeAscii(ByteArrayOutputStream out, String value) {
        out.writeBytes(value.getBytes(StandardCharsets.US_ASCII));
    }

    private static void writeLeInt(byte[] out, int offset, int value) {
        out[offset] = (byte) (value & 0xFF);
        out[offset + 1] = (byte) ((value >>> 8) & 0xFF);
        out[offset + 2] = (byte) ((value >>> 16) & 0xFF);
        out[offset + 3] = (byte) ((value >>> 24) & 0xFF);
    }

    private static void writeLeInt(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 24) & 0xFF);
    }

    private static long readLeLong(byte[] bytes, int offset) throws CodecMediaException {
        if (offset + 8 > bytes.length) {
            throw new CodecMediaException("Unexpected end of WAV data");
        }
        return (bytes[offset] & 0xFFL)
                | ((bytes[offset + 1] & 0xFFL) << 8)
                | ((bytes[offset + 2] & 0xFFL) << 16)
                | ((bytes[offset + 3] & 0xFFL) << 24)
                | ((bytes[offset + 4] & 0xFFL) << 32)
                | ((bytes[offset + 5] & 0xFFL) << 40)
                | ((bytes[offset + 6] & 0xFFL) << 48)
                | ((bytes[offset + 7] & 0xFFL) << 56);
    }
}

