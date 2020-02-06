package engineio_client.parser;

import org.junit.Test;

import static org.junit.Assert.*;

public class PacketTest {

    @Test
    public void testPacketType() {
        assertTrue(new Packet(Type.MESSAGE, new byte[1]).isBinary());
        assertFalse(new Packet(Type.MESSAGE, "").isBinary());
    }

    @Test
    public void testPacketSize() {
        assertEquals(new Packet(Type.MESSAGE).size(), 0);
        assertEquals(new Packet(Type.MESSAGE, null).size(), 0);
        assertEquals(new Packet(Type.MESSAGE, new byte[0]).size(), 0);
        assertEquals(new Packet(Type.MESSAGE, new byte[]{1,2,3}).size(), 3);
        assertEquals(new Packet(Type.MESSAGE, "abc").size(), 3);
        // Size of the String in UTF8 encoded bytes.
        assertEquals(new Packet(Type.MESSAGE, "abcçöü").size(), 9);
    }

    @Test
    public void testPacketToByteArray() {
        assertArrayEquals(new Packet(Type.MESSAGE).toByteArray(), new byte[0]);
        assertArrayEquals(new Packet(Type.MESSAGE, null).toByteArray(), new byte[0]);
        assertArrayEquals(new Packet(Type.MESSAGE, new byte[]{1,2,3}).toByteArray(), new byte[]{1,2,3});
        assertArrayEquals(new Packet(Type.MESSAGE, "abc").toByteArray(), new byte[]{0x61, 0x62, 0x63});
        assertArrayEquals(new Packet(Type.MESSAGE, "abcçöü").toByteArray(),
                new byte[]{0x61, 0x62, 0x63, (byte)0xc3, (byte)0xa7, (byte)0xc3, (byte)0xb6, (byte)0xc3, (byte)0xbc});
    }
}
