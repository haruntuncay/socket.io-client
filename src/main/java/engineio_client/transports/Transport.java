package engineio_client.transports;

import engineio_client.Config;
import engineio_client.HandshakeData;
import common.Observable;
import engineio_client.parser.Packet;
import engineio_client.parser.Parser;
import engineio_client.parser.Type;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static common.Utils.getQueryStringFromMap;

/**
 * Manages the connection and provides communication between server and client by either doing Polling or using WebSockets.
 */
public abstract class Transport extends Observable {

    protected Config config;
    protected Parser parser;
    protected State state;
    /**
     * For PollingTransport, buffers any sent packets if there is currently a write operation in progress.
     * For WebSocketTransport, buffers any packets sent between the instantiation of a WebSocketTransport object and its open call.
     */
    protected Queue<Packet> sendBuffer;

    public Transport(Config config) {
        this.config = config;
        parser = new Parser();
        state = State.INITIAL;
        sendBuffer = new ConcurrentLinkedQueue<>();
    }

    public void send(String data) {
        send(new Packet(Type.MESSAGE, data));
    }

    public void send(byte[] data) {
        send(new Packet(Type.MESSAGE, data));
    }

    public boolean isOpen() {
        return state == State.OPEN;
    }

    public boolean isClosed() {
        return state == State.CLOSED;
    }

    protected void onOpenPacket(Packet packet) {
        state = State.OPEN;
        HandshakeData handshakeData = HandshakeData.parseHandshake((String) packet.getData());
        config.queryMap.put("sid", handshakeData.getSessionId());
        emitEvent(HANDSHAKE, handshakeData);
        emitEvent(OPEN, packet);
    }

    String buildURLString() {
        return buildURLString(false);
    }

    String buildURLString(boolean buildForWebSocket) {
        return  (buildForWebSocket ? "https".equals(config.scheme) ? "wss" : "ws"
                                   : config.scheme)
                + "://"
                + config.hostname + ":" + config.port
                + config.path + "?"
                + getQueryStringFromMap(config.queryMap)
                + "&transport=" + (buildForWebSocket ? WebSocketTransport.NAME : PollingTransport.NAME);
    }

    void closeAbruptly(String message) {
        closeAbruptly(message, null);
    }

    void handleError(String reason) {
        handleError(reason, null);
    }

    abstract void closeAbruptly(String message, Throwable throwable);

    abstract void handleError(String reason, Throwable throwable);

    public abstract void open();

    public abstract void close(boolean isCloseClientInitiated);

    public abstract void send(Packet packet);

    public abstract void flush();

    public static final String OPEN = "open";
    public static final String PACKET = "packet";
    public static final String ABRUPT_CLOSE = "abrupt_close";
    public static final String CLOSE = "close";
    public static final String ERROR = "error";
    public static final String HANDSHAKE = "handshake";

    public enum State {
        INITIAL, OPEN, CLOSED, ABRUPTLY_CLOSED;
    }
}
