package socketio_client.parser;

import java.util.Arrays;

/**
 * Socket.IO packet object.
 */
public class Packet {

    public static final String DEFAULT_NAMESPACE = "/";

    Type type;
    String namespace;
    int id = -1;
    int attachmentSize;
    Object data;

    public Packet(Type type, String namespace, int id, Object data) {
        this.type = type;
        this.namespace = namespace;
        this.id = id;
        this.data = data;
    }

    public Packet(Type type, Object data) {
        this(type, DEFAULT_NAMESPACE, -1, data);
    }

    public Packet(Type type, String namespace) {
        this(type, namespace, -1, null);
    }

    public Packet(Type type) {
        // Casting of null to Object is to distinguish between 2 constructors.
        // Packet(Type type, String namespace) and  Packet(Type type, Object data)
        this(type, (Object) null);
    }

    public boolean shouldBeAcknowledged() {
        return id > -1;
    }

    public Type getType() {
        return type;
    }

    public String getNamespace() {
        return namespace;
    }

    public int getId() {
        return id;
    }

    public Object getData() {
        return data;
    }

    @Override
    public String toString() {
        return "Packet{" +
                "type=" + type +
                ", namespace='" + namespace + '\'' +
                ", id=" + id +
                ", attachmentSize=" + attachmentSize +
                ", data=" + data +
                '}';
    }

    @Override
    public boolean equals(Object obj) {
        if(this == obj)
            return true;

        if(!(obj instanceof Packet))
            return false;

        Packet packet = (Packet) obj;
        if(type != packet.type)
            return false;

        if(data == null)
            return packet.getData() == null;

        if(data instanceof byte[])
            return Arrays.equals((byte[]) data, (byte[]) packet.getData());

        return data.equals(packet.getData());
    }
}
