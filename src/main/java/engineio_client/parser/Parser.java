package engineio_client.parser;

import exceptions.EngineIOParserException;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Parser implementation for engine.io packets.
 * There are two ways to represent an engine.io packet. Binary representation and String representation.
 * And there are two ways to encode them, as a payload(array of packets) for PollingTransport
 *  or as individual packets for WebSocketTransport.
 *
 * <p> PollingTransport:
 * <p> When the PollingTransport is in use, all data that is sent and received, is encoded as a payload.
 * When encoding, the entire payload is encoded into a byte[], using the following formula.
 * <p> - If packet in payload is String, put {@code 0x0}, if binary, put {@code 0x1}
 * <br>- Put data length, by representing each digit in length as a single byte.
 *          For example, if length is 5, put 0x5, if length is 15, put 0x1 0x5. (weird, I know, but that's how it is)
 * <br>- Put the separator byte(0xFF) that marks the data start.
 * <br>- Put the packet type's value. If the packet is binary, put the value as is, it the packet is String, put the String value.
 *          For example, if the packet is binary and the Type value is 4, put 0x4.
 *          If the packet is String and the Type value is 4, put 0x32, since 4 is actually 52 in UTF_8. (again, weird but that's how it is)
 * <br>- Put the data as byte[] if packet is binary, if the packet is String, put UTF_8 bytes.
 *
 * <p> Received payload could either be String encoded if every packet in the payload is String. Otherwise, it will be binary encoded.
 * <p> String encoding is simpler than binary encoding, where it only goes like this:
 * <p> - Length + STRING_DATA_SEPARATOR(:) + data
 *
 * <br>
 * <p> WebSocketTransport:
 * <p> Encoding and decoding packets when WebSocketTransport is in use is much simpler,
 *      since WebSocketTransport doesn't require payloads or buffering, only a single packet needs to be encoded/decoded at a given time.
 * <p> The format is: TypeValue + data.
 * <p> Of course, if the data is String, this value is written as String, if the packet is binary, this value is written as byte[].
 * <p> For example: Packet of type MESSAGE(4) with data "Hello" would be encoded as -> 4Hello.
 * <br> And Packet of type MESSAGE(4) with data [1,2,3] would be encoded as -> 0x4 0x1 0x2 0x3.
 *
 * @see <a href="https://github.com/socketio/engine.io-protocol">EngineIO protocol</a> for detailed information.
 */
public class Parser {

    //Engine.IO protocol version.
    public static final String VERSION = "3";
    // When the packets are encoded as byte[], indicates that the data is actually a String.
    private static final byte STRING_DATA_MARKER = 0;
    // When the packets are encoded as byte[], indicates that the data is actually a byte[].
    private static final byte BINARY_DATA_MARKER = 1;
    // When the payload is encoded
    private static final byte BINARY_DATA_SEPARATOR = (byte)0xFF;
    private static final char STRING_DATA_SEPARATOR = ':';

    public void encodePacket(Packet packet, EncodeCallback callback) {
        Object data;
        if(packet.isBinary()) {
            data = new byte[packet.size() + 1];
            ((byte[]) data)[0] = (byte) packet.type.value;
            System.arraycopy((byte[])packet.data, 0, data, 1, packet.size());
        } else {
            data = packet.type.value + (packet.data == null ? "" : (String) packet.data);
        }

        callback.call(data);
    }

    public void encodePayload(Packet[] packets, EncodeCallback callback) {
        int payloadSize = 0;
        for(Packet packet : packets)
            payloadSize += getEncodeSizeForPacket(packet);

        ByteBuffer payloadBuffer = ByteBuffer.allocate(payloadSize);
        for(Packet packet : packets)
            encodePayloadHelper(packet, (encodedData) -> payloadBuffer.put((byte[]) encodedData));

        callback.call(payloadBuffer.array());
    }

    private void encodePayloadHelper(Packet packet, EncodeCallback callback) {
        int encodeBufferSize = getEncodeSizeForPacket(packet);
        int dataLength = packet.size() + 1; // +1 for the packet type.

        ByteBuffer encodeBuffer = ByteBuffer.allocate(encodeBufferSize);
        encodeBuffer.put(packet.isBinary() ? BINARY_DATA_MARKER : STRING_DATA_MARKER)
                .put(digitsToByteArray(dataLength))
                .put(BINARY_DATA_SEPARATOR)
                .put(packet.isBinary() ? packet.type.toByteValue() : packet.type.toStringValue())
                .put(packet.toByteArray());

        callback.call(encodeBuffer.array());
    }

    private int getEncodeSizeForPacket(Packet packet) {
        int packetSize = packet.size() + 1; // + 1 is for storing packet's type.
        String packetSizeStr = String.valueOf(packetSize);
        // 1 byte for differentiating between String(0x00) or Binary(0x01) Data
        // 1 byte for marking start of data (0xFF);
        return 2 + packetSizeStr.length() + packetSize;
    }

    private byte[] digitsToByteArray(int num) {
        int digitCount = 0;
        int numCpy = num;
        while(numCpy > 0) {
            digitCount++;
            numCpy /= 10;
        }
        byte[] digitArray = new byte[digitCount];
        for(int i = digitCount - 1; i >= 0; i--) {
            digitArray[i] = (byte) (num % 10);
            num /= 10;
        }
        return digitArray;
    }

    public void decodePacket(String encodedPacketData, DecodeCallback callback) {
        Type type =  Type.of(Character.digit(encodedPacketData.charAt(0), 10));
        Packet packet = new Packet(type, encodedPacketData.substring(1));
        callback.call(packet);
    }

    public void decodePacket(byte[] encodedPacketData, DecodeCallback callback) {
        Type type = Type.of(encodedPacketData[0]);
        // todo(low priority), maybe consider an immutable wrapper object, so that we can avoid copying (almost) entire byte[], or possibly off-heap.
        // Since encodedPacketData starts with a byte that corresponds to Type, we can't just give it to Packet's constructor.
        // We have to remove that first byte, so that Packet.data doesn't start with a byte that is meaningless to the user.
        byte[] packetData = Arrays.copyOfRange(encodedPacketData, 1, encodedPacketData.length);
        Packet packet = new Packet(type, packetData);
        callback.call(packet);
    }

    public void decodePayload(byte[] encodedPayloadData, DecodeCallback callback) {
        int start = 0;
        do{
            start = decodeByteArrayPayloadHelper(encodedPayloadData, callback, start);
        } while(start < encodedPayloadData.length);
    }

    private int decodeByteArrayPayloadHelper(byte[] encodedData, DecodeCallback callback, int startIndex) {
        byte dataType = encodedData[startIndex];
        if(dataType != BINARY_DATA_MARKER && dataType != STRING_DATA_MARKER)
            throw new EngineIOParserException("Received encoded data starts with an invalid byte(" + dataType + ")."
                                                + "\r\nAccepted ones are " + BINARY_DATA_MARKER + " or " + STRING_DATA_MARKER);

        // Since SEPARATOR(0xFF) byte separates between size and data area of a packet, read until it is seen.
        StringBuilder sizeStrBuilder = new StringBuilder();
        int i = startIndex + 1;
        while(i < encodedData.length && encodedData[i] != BINARY_DATA_SEPARATOR) {
            sizeStrBuilder.append(encodedData[i]);
            i++;
        }
        int packetSize = Integer.parseInt(sizeStrBuilder.toString()) - 1;
        // "i" currently holds the index of SEPARATOR(0xFF), get packet type by reading the next byte.
        byte typeByte = encodedData[++i];

        if(dataType == STRING_DATA_MARKER)
            typeByte = Byte.valueOf("" + (char)typeByte);

        Type type = Type.of(typeByte);
        Object data = null;
        // Get "i" to point to the start of data.
        i++;
        if(packetSize > 0) {
            // Create data, respectively to the "dataType" parameter. (String or byte[]).
            data = dataType == STRING_DATA_MARKER ? new String(encodedData, i, packetSize)
                                                  : Arrays.copyOfRange(encodedData, i, i + packetSize);
        }
        Packet packet = new Packet(type, data);
        callback.call(packet);
        return i + packetSize;
    }

    public void decodePayload(String encodedPayloadData, DecodeCallback callback) {
        int start = 0;
        do {
            start = decodeStringPayloadHelper(encodedPayloadData, callback, start);
        } while(start < encodedPayloadData.length());
    }

    private int decodeStringPayloadHelper(String encodedData, DecodeCallback callback, int startIndex) {
        int i = startIndex;
        StringBuilder sizeSb = new StringBuilder();
        while(i < encodedData.length() && encodedData.charAt(i) != STRING_DATA_SEPARATOR) {
            sizeSb.append(encodedData.charAt(i));
            i++;
        }
        int packetSize = Integer.parseInt(sizeSb.toString()) - 1; // - 1 is because packet type is also included in data.

        // "i" is currently pointing to STRING_DATA_SERAPATOR, increment it, so that it can point to packet type.
        i++;
        int packetTypeValue = Character.getNumericValue(encodedData.charAt(i));
        Type type = Type.of(packetTypeValue);

        // "i" was pointing to packetType, increment it to point to the start of data.
        i++;
        String data = packetSize == 0 ? null : encodedData.substring(i, i + packetSize);
        Packet packet = new Packet(type, data);
        callback.call(packet);

        return i + packetSize;
    }

    public static interface EncodeCallback {

        void call(Object encodedData);
    }

    public static interface DecodeCallback {

        void call(Packet packet);
    }
}
