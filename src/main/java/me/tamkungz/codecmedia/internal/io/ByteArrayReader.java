package me.tamkungz.codecmedia.internal.io;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class ByteArrayReader {

    private final byte[] data;
    private int position;

    public ByteArrayReader(byte[] data) {
        this.data = data == null ? new byte[0] : data;
        this.position = 0;
    }

    public int length() {
        return data.length;
    }

    public int position() {
        return position;
    }

    public int remaining() {
        return data.length - position;
    }

    public void position(int newPosition) {
        if (newPosition < 0 || newPosition > data.length) {
            throw new IllegalArgumentException("Position out of bounds: " + newPosition);
        }
        this.position = newPosition;
    }

    public void skip(int bytes) {
        position(position + bytes);
    }

    public int readU8() {
        ensureRemaining(1);
        return data[position++] & 0xFF;
    }

    public int readU16BE() {
        ensureRemaining(2);
        int value = ((data[position] & 0xFF) << 8) | (data[position + 1] & 0xFF);
        position += 2;
        return value;
    }

    public int readU16LE() {
        ensureRemaining(2);
        int value = (data[position] & 0xFF) | ((data[position + 1] & 0xFF) << 8);
        position += 2;
        return value;
    }

    public long readU32BE() {
        ensureRemaining(4);
        long value = ((long) (data[position] & 0xFF) << 24)
                | ((long) (data[position + 1] & 0xFF) << 16)
                | ((long) (data[position + 2] & 0xFF) << 8)
                | (data[position + 3] & 0xFFL);
        position += 4;
        return value;
    }

    public long readU32LE() {
        ensureRemaining(4);
        long value = (data[position] & 0xFFL)
                | ((long) (data[position + 1] & 0xFF) << 8)
                | ((long) (data[position + 2] & 0xFF) << 16)
                | ((long) (data[position + 3] & 0xFF) << 24);
        position += 4;
        return value;
    }

    public long readU64LE() {
        ensureRemaining(8);
        long value = 0;
        for (int i = 0; i < 8; i++) {
            value |= ((long) data[position + i] & 0xFFL) << (8 * i);
        }
        position += 8;
        return value;
    }

    public byte[] readBytes(int length) {
        ensureRemaining(length);
        byte[] out = Arrays.copyOfRange(data, position, position + length);
        position += length;
        return out;
    }

    public String readAscii(int length) {
        return new String(readBytes(length), StandardCharsets.US_ASCII);
    }

    public int peekU8(int offsetFromPosition) {
        int index = position + offsetFromPosition;
        if (index < 0 || index >= data.length) {
            throw new IllegalArgumentException("Peek out of bounds: " + index);
        }
        return data[index] & 0xFF;
    }

    public int getU8(int index) {
        if (index < 0 || index >= data.length) {
            throw new IllegalArgumentException("Index out of bounds: " + index);
        }
        return data[index] & 0xFF;
    }

    private void ensureRemaining(int needed) {
        if (needed < 0 || remaining() < needed) {
            throw new IllegalArgumentException(
                    "Not enough bytes: need " + needed + ", remaining " + remaining());
        }
    }
}

