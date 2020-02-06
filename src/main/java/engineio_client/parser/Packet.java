package engineio_client.parser;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Represents an Engine.IO packet.
 * Can be one of six types:
 * <p> OPEN(0), CLOSE(1), PING(2), PONG(3), MESSAGE(4), UPGRADE(5), NOOP(6).
 * <p>
 * @see <a href="https://github.com/socketio/engine.io-protocol">Engine.IO Protocol</a> for detailed information.
 */
public class Packet {

    public static final Packet PING = new Packet(Type.PING);
    public static final Packet NOOP = new Packet(Type.NOOP);
    public static final Packet CLOSE = new Packet(Type.CLOSE);
    private static final byte[] EMPTY_BYTE_ARRAY = new byte[]{};

    Type type;
    Object data;

    public Packet(Type type) {
        this(type, null);
    }

    public Packet(Type type, Object data) {
        this.type = type;
        this.data = data;
    }

    public Type getType() {
        return type;
    }

    public Object getData() {
        return data;
    }

    public boolean isBinary() {
        return data instanceof byte[];
    }

    /**
     * Get the size of data in bytes. This equals to byte[].length if the data is of type byte[],
     *  or String.getBytes("UTF-8").length if the data is of type String, 0 otherwise.
     *
     * @return Size of the data in bytes.
     */
    int size() {
        if(data instanceof byte[])
            return ((byte[]) data).length;

        if(data instanceof String)
            return ((String) data).getBytes(StandardCharsets.UTF_8).length;

        return 0;
    }

    /**
     * Returns a byte[] representation of the data.
     * If the data is instance of byte[], return itself, if it is an instance of a String, return UTF_8 bytes.
     * Otherwise, return a zero length byte array.
     *
     * @return byte[] Byte representation of data.
     */
    byte[] toByteArray() {
        if(data instanceof byte[])
            return (byte[]) data;

        if(data instanceof String)
            return ((String) data).getBytes(StandardCharsets.UTF_8);

        return EMPTY_BYTE_ARRAY;
    }

    @Override
    public String toString() {
        return "Packet{" +
                "type=" + type +
                ", data=" + ((data instanceof byte[]) ? Arrays.toString((byte[]) data) : data) +
                '}';
    }

    @Override
    public boolean equals(Object obj) {
        if(this == obj)
            return true;

        if(!(obj instanceof Packet))
            return false;

        Packet packet = (Packet) obj;
        if(type != packet.getType())
            return false;

        if(data == null)
            return packet.getData() == null;

        if(data instanceof byte[])
            return Arrays.equals((byte[]) data, (byte[]) packet.getData());

        return data.equals(packet.getData());
    }
}
