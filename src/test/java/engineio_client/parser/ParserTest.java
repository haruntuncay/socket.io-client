package engineio_client.parser;

import org.junit.Test;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.*;

public class ParserTest {

    private Parser parser = new Parser();

    @Test
    public void testEncodePacket() {
        Packet strPacket = new Packet(Type.MESSAGE, "data");
        Packet binPacket = new Packet(Type.MESSAGE, new byte[]{1,2,3});
        Packet emptyPacket = new Packet(Type.MESSAGE);
        Packet nullPacket = new Packet(Type.MESSAGE, null);

        parser.encodePacket(strPacket, encodedData -> assertEquals("4data", encodedData));
        parser.encodePacket(binPacket, encodedData -> assertArrayEquals(new byte[]{4,1,2,3}, (byte[])encodedData));
        parser.encodePacket(emptyPacket, encodedData -> assertEquals("4", encodedData));
        parser.encodePacket(nullPacket, encodedData -> assertEquals("4", encodedData));
    }

    @Test
    public void testEncodePayload() {
        Packet strPacket = new Packet(Type.MESSAGE, "data");
        Packet binPacket = new Packet(Type.MESSAGE, new byte[]{1,2,3});
        Packet emptyPacket = new Packet(Type.MESSAGE);
        Packet nullPacket = new Packet(Type.MESSAGE, null);

        Packet[] packets = new Packet[]{strPacket, binPacket, emptyPacket, nullPacket};
        List<byte[]> individualPayloads = new LinkedList<>();

        Arrays.stream(packets)
                .forEach(packet -> parser.encodePayload(new Packet[]{packet}, payload -> individualPayloads.add((byte[]) payload)));

        int totalPayloadLength = individualPayloads.stream()
                                                    .mapToInt(payload -> payload.length)
                                                    .reduce(0, Integer::sum);

        ByteBuffer combinedPayloadBuffer = ByteBuffer.allocate(totalPayloadLength);
        individualPayloads.forEach(combinedPayloadBuffer::put);

        parser.encodePayload(packets, payload -> assertArrayEquals((byte[]) payload, combinedPayloadBuffer.array()));
        assertArrayEquals(combinedPayloadBuffer.array(),
                            new byte[]{0, 5, -1, 52, 100, 97, 116, 97, 1, 4, -1, 4, 1, 2, 3, 0, 1, -1, 52, 0, 1, -1, 52});
    }

    @Test
    public void testDecodePacket() {
        parser.decodePacket("4data", packet -> assertEquals(packet, new Packet(Type.MESSAGE, "data")));
        parser.decodePacket(new byte[]{4,1,2,3}, packet -> assertEquals(packet, new Packet(Type.MESSAGE, new byte[]{1,2,3})));
        parser.decodePacket("0", packet -> assertEquals(packet, new Packet(Type.OPEN, "")));
        parser.decodePacket(new byte[]{0}, packet -> assertEquals(packet, new Packet(Type.OPEN, new byte[0])));
    }

    @Test
    public void testDecodeStringPayload() {
        String payload = "6:4hello6:4world";
        Packet helloPacket = new Packet(Type.MESSAGE, "hello");
        Packet worldPacket = new Packet(Type.MESSAGE, "world");
        List<Packet> decodedPackets = new LinkedList<>();

        parser.decodePayload(payload, decodedPackets::add);
        assertEquals(decodedPackets.get(0), helloPacket);
        assertEquals(decodedPackets.get(1), worldPacket);

        byte[] binPayload = new byte[]{0, 5, -1, 52, 100, 97, 116, 97, 1, 4, -1, 4, 1, 2, 3, 0, 1, -1, 52, 0, 1, -1, 52};
        Packet strPacket = new Packet(Type.MESSAGE, "data");
        Packet binPacket = new Packet(Type.MESSAGE, new byte[]{1,2,3});
        Packet emptyPacket = new Packet(Type.MESSAGE);
        Packet nullPacket = new Packet(Type.MESSAGE, null);
        List<Packet> packets = Arrays.asList(strPacket, binPacket, emptyPacket, nullPacket);

        decodedPackets.clear();
        parser.decodePayload(binPayload, decodedPackets::add);
        assertEquals(packets, decodedPackets);
    }
}
