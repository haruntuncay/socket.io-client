package socketio_client.parser;

import exceptions.SocketIOParserException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.Test;

import static common.Utils.jsonArrayToObjectArray;
import static org.junit.Assert.*;

public class ParserTest {

    private Parser.Encoder encoder = new ParserImpl.EncoderImpl();
    private Parser.Decoder decoder = new ParserImpl.DecoderImpl();

    @Test
    public void testEncodeStringPacket() {
        JSONArray array = new JSONArray();
        array.put("eventName")
                .put("hello")
                .put("world");
        Packet packet = new Packet(Type.EVENT, array);
        encoder.encode(packet, encodedData -> assertEquals(encodedData[0], "2[\"eventName\",\"hello\",\"world\"]"));

        Packet packet2 = new Packet(Type.ACK, "/nsp", 1, null);
        encoder.encode(packet2, encodedData -> assertEquals(encodedData[0], "3/nsp,1"));

        Packet packet3 = new Packet(Type.EVENT, "/nsp", 1, new JSONArray().put("eventName"));
        encoder.encode(packet3, encodedData -> assertEquals(encodedData[0], "2/nsp,1[\"eventName\"]"));
    }

    @Test
    public void testEncodeBinaryData() {
        JSONArray array = new JSONArray();
        array.put("eventName").put(new byte[]{1,2,3}).put("str");

        Packet packet = new Packet(Type.BINARY_EVENT, array);
        encoder.encode(packet, encodedPackets -> {
            assertEquals(encodedPackets[0], "51-[\"eventName\",{\"_placeholder\":true,\"num\":0},\"str\"]");
            assertArrayEquals((byte[]) encodedPackets[1], new byte[]{1,2,3});
        });

        packet = new Packet(Type.BINARY_EVENT, "/nsp", 10, new JSONArray().put("eventName").put(new byte[]{1,2,3}));
        encoder.encode(packet, encodedPackets -> {
            assertEquals(encodedPackets[0], "51-/nsp,10[\"eventName\",{\"_placeholder\":true,\"num\":0}]");
            assertArrayEquals((byte[]) encodedPackets[1], new byte[]{1,2,3});
        });

        JSONArray nestedBinArray = new JSONArray();
        /*
            [
                "eventName",
                {
                    "data": byte{1},
                    "hello": "world"
                },
                byte{2},
                [
                    byte{3}
                ]
            ]
         */
        nestedBinArray.put("eventName")
                .put(new JSONObject()
                        .put("data", new byte[]{1})
                        .put("hello", "world"))
                .put(new byte[]{2})
                .put(new JSONArray().put(new byte[]{3}));

        Packet packet3 = new Packet(Type.BINARY_EVENT, "/nsp", 1, nestedBinArray);
        encoder.encode(packet3, encodedPackets -> {
            assertEquals(encodedPackets[0],
                    "53-/nsp,1[\"eventName\",{\"data\":{\"_placeholder\":true,\"num\":0},\"hello\":\"world\"},{\"_placeholder\":true,\"num\":1},[{\"_placeholder\":true,\"num\":2}]]");

            assertArrayEquals((byte[])encodedPackets[1], new byte[]{1});
            assertArrayEquals((byte[])encodedPackets[2], new byte[]{2});
            assertArrayEquals((byte[])encodedPackets[3], new byte[]{3});
        });
    }

    @Test
    public void testDecodeStringPacket() {
        decoder.add("2/nsp,1[\"eventName\",\"hello\",\"world\"]",
                packet -> {
                    assertEquals(Type.EVENT, packet.type);
                    assertEquals("/nsp", packet.namespace);
                    assertEquals(1, packet.id);
                    assertArrayEquals(jsonArrayToObjectArray((JSONArray) packet.getData()), new Object[]{"eventName", "hello", "world"});
                });

        decoder.add("31",
                packet -> {
                    assertEquals(Type.ACK, packet.type);
                    assertEquals("/", packet.namespace);
                    assertEquals(1, packet.id);
                    assertNull(packet.getData());
                });
    }

    @Test
    /*
         Test of issue#1 https://github.com/haruntuncay/socket.io-client/issues/1.
         Socket.IO packets separate different parts of a message with separators (like "/", "-", ",")
         and when they appear in data part, caused a bug that elicited this issue.
     */
    public void testDecodeStringWhenSeparatorsAppearsInData() {
        decoder.add("22-[\"event-name/\", \"va,lue\"]", packet -> {
            JSONArray data = (JSONArray) packet.data;
            assertEquals(2, packet.attachmentSize);
            assertEquals("event-name/", data.get(0));
            assertEquals("va,lue", data.get(1));
            assertEquals("/", packet.namespace);
        });

        decoder.add("22-/nsp,[\"event,name\", \"va/lue\"]", packet -> {
            JSONArray data = (JSONArray) packet.data;
            assertEquals("event,name", data.get(0));
            assertEquals("va/lue", data.get(1));
            assertEquals("/nsp", packet.namespace);
        });

        decoder.add("2/nsp,", packet -> {
            assertEquals("/nsp", packet.namespace);
        });
    }

    @Test
    public void testDecodeBinaryPacket() {
        // Binary packets with attachments trigger the callback only when they are fully re-constructed.
        // This one still waits for 1 byte[], since it's not fully constructed, the callback will not be called.
        decoder.add("51-[\"eventName\",{\"_placeholder\":true,\"num\":0}]", packet -> fail("This will not be called"));
        // Adding a binary packet while another one already waits for re-construction causes SocketIOException to be thrown.
        assertThrows(SocketIOParserException.class, () -> decoder.add("51-[\"eventName\",{\"_placeholder\":true,\"num\":0}]", packet -> {}));

        // Add the required binary data so that packet re-construction can be complete.
        decoder.add(new byte[]{1,2,3}, packet -> {
            assertEquals(Type.BINARY_EVENT, packet.type);
            assertEquals(1, packet.attachmentSize);
            assertEquals("/", packet.namespace);
            assertArrayEquals(jsonArrayToObjectArray((JSONArray) packet.getData()), new Object[]{"eventName", new byte[]{1,2,3}});
        });
    }
}
