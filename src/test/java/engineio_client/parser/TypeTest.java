package engineio_client.parser;

import exceptions.EngineIOParserException;
import org.junit.Test;

import static org.junit.Assert.*;

public class TypeTest {

    @Test
    public void testOf() {
        assertEquals(Type.of(0), Type.OPEN);
        assertEquals(Type.of(1), Type.CLOSE);
        assertEquals(Type.of(2), Type.PING);
        assertEquals(Type.of(3), Type.PONG);
        assertEquals(Type.of(4), Type.MESSAGE);
        assertEquals(Type.of(5), Type.UPGRADE);
        assertEquals(Type.of(6), Type.NOOP);
        assertThrows(EngineIOParserException.class, () -> Type.of(-1));
    }

    @Test
    public void toStringValue() {
        assertEquals(0x30, Type.OPEN.toStringValue());
        assertEquals(0x31, Type.CLOSE.toStringValue());
        assertEquals(0x32, Type.PING.toStringValue());
        assertEquals(0x33, Type.PONG.toStringValue());
        assertEquals(0x34, Type.MESSAGE.toStringValue());
        assertEquals(0x35, Type.UPGRADE.toStringValue());
        assertEquals(0x36, Type.NOOP.toStringValue());
    }
}
