package socketio_client.parser;

import org.junit.Test;

import static org.junit.Assert.*;

public class TypeTest {

    @Test
    public void testGetTypeForValue() {
        assertEquals(Type.CONNECT, Type.getTypeForValue(0));
        assertEquals(Type.DISCONNECT, Type.getTypeForValue(1));
        assertEquals(Type.EVENT, Type.getTypeForValue(2));
        assertEquals(Type.ACK, Type.getTypeForValue(3));
        assertEquals(Type.ERROR, Type.getTypeForValue(4));
        assertEquals(Type.BINARY_EVENT, Type.getTypeForValue(5));
        assertEquals(Type.BINARY_ACK, Type.getTypeForValue(6));
    }
}
