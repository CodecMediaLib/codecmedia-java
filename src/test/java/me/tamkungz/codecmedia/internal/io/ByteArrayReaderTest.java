package me.tamkungz.codecmedia.internal.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class ByteArrayReaderTest {

    @Test
    void shouldReadPrimitiveValuesInBothEndianOrders() {
        byte[] data = {
                0x01, 0x02, 0x03, 0x04,
                0x05, 0x06, 0x07, 0x08
        };
        ByteArrayReader r = new ByteArrayReader(data);

        assertEquals(0x01, r.readU8());
        assertEquals(0x0203, r.readU16BE());
        assertEquals(0x0504, r.readU16LE());
        assertEquals(0x06, r.readU8());
        assertEquals(0x0708, r.readU16BE());
    }
}

