package engineio_client.parser;

import exceptions.EngineIOParserException;

/**
 *
 */
public enum Type {

    OPEN(0), CLOSE(1), PING(2), PONG(3), MESSAGE(4), UPGRADE(5), NOOP(6);

    int value;

    Type(int value) {
        this.value = value;
    }

    static Type of(int val) {
        switch (val) {
            case 0: return OPEN;
            case 1: return CLOSE;
            case 2: return PING;
            case 3: return PONG;
            case 4: return MESSAGE;
            case 5: return UPGRADE;
            case 6: return NOOP;
            default:
                throw new EngineIOParserException("Invalid engine.io packet type (" + val + ").");
        }
    }

    // Return the type's value as a byte to use it during encoding process.
    byte toByteValue() {
        return (byte) value;
    }

    // Returns type's value as a String to use it during String encoding process.
    byte toStringValue() {
        return (byte) String.valueOf(value).charAt(0);
    }
}
