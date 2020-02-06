package socketio_client.parser;

import exceptions.SocketIOParserException;

public enum Type {

    CONNECT(0), DISCONNECT(1), EVENT(2), ACK(3),
    ERROR(4), BINARY_EVENT(5), BINARY_ACK(6);

    private int value;
    Type(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }

    public static Type getTypeForValue(int value) {
        switch (value) {
            case 0: return CONNECT;
            case 1: return DISCONNECT;
            case 2: return EVENT;
            case 3: return ACK;
            case 4: return ERROR;
            case 5: return BINARY_EVENT;
            case 6: return BINARY_ACK;
            default:
                throw new SocketIOParserException("Invalid socket.io packet type (" + value + ").");
        }
    }

    public static boolean isValid(int value) {
        return getTypeForValue(value) != null;
    }
}
