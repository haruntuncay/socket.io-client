package engineio_client;

import exceptions.EngineIOException;
import org.junit.Test;

import static org.junit.Assert.*;

public class HandshakeTest {

    @Test
    public void testParseHandshake() {
        HandshakeData hs = HandshakeData.parseHandshake(
                "{\"sid\":\"sessionIdInString\",\"pingInterval\":20000,\"pingTimeout\":5000,\"upgrades\":[\"websocket\"]}");

        assertEquals(hs.getSessionId(), "sessionIdInString");
        assertEquals(hs.getPingInterval(), 20000);
        assertEquals(hs.getPingTimeout(), 5000);
        assertArrayEquals(hs.getUpgrades(), new String[]{"websocket"});

        // Any missing field must cause an exception.
        assertThrows(EngineIOException.class, () -> HandshakeData.parseHandshake("{\"pingInterval\":20000,\"pingTimeout\":5000,\"upgrades\":[\"websocket\"]}"));
        assertThrows(EngineIOException.class, () -> HandshakeData.parseHandshake("{\"sid\":\"sessionIdInString\",\"pingTimeout\":5000,\"upgrades\":[\"websocket\"]}"));
        assertThrows(EngineIOException.class, () -> HandshakeData.parseHandshake("{\"sid\":\"sessionIdInString\",\"pingInterval\":20000,\"upgrades\":[\"websocket\"]}"));
        assertThrows(EngineIOException.class, () -> HandshakeData.parseHandshake("{\"sid\":\"sessionIdInString\",\"pingInterval\":20000,\"pingTimeout\":5000}"));
    }
}
