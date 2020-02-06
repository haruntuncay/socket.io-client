package socketio_client;

import common.Utils;
import engineio_client.EngineSocket;
import common.Observable;
import socketio_client.parser.Packet;
import socketio_client.parser.Parser;
import socketio_client.parser.ParserImpl;

import java.net.URL;
import java.util.Map;
import java.util.concurrent.*;

import static common.Worker.*;

/**
 * Manages a group of Sockets and maintains an EngineSocket to delegate reads and writes.
 * Exposes multiple events in order to keep sockets notified. Namely:
 * <p> {@link #OPEN}, emitted when the underlying EngineSocket instance is considered open.
 * <p> {@link #CLOSE}, emitted when the underlying EngineSocket instance is closed orderly, either by the client or by the server.
 * <p> {@link #ABRUPT_CLOSE}, emitted when the connection is closed due to an error or an unexpected event.
 * <p> {@link #PING}, emitted when the ping packet is written.
 * <p> {@link #PONG}, emitted when the pong packet is received.
 * <p> {@link #PACKET}, emitted when a socket.io packet is receives and decoded.
 * <p> {@link #RECONNECT_FAIL}, emitted when the reconnect attempts reaches its max value.
 * <p> {@link #RECONNECT_ATTEMPT}, emitted when an attempt to reconnect is made.
 */
public class Manager extends Observable {

    // Namespace -> Socket map
    Map<String, Socket> sockets;
    private State state;
    private ClientConfig config;
    private EngineSocket engine;
    private Parser.Encoder encoder;
    private Parser.Decoder decoder;
    private ReconnectManager reconnectManager;
    private String connectionPath;

    Manager(URL url, ClientConfig config) {
        if(config == null)
            config = new ClientConfig();

        sockets = new ConcurrentHashMap<>();
        engine = new EngineSocket(url, config);
        this.config = config;
        encoder = new ParserImpl.EncoderImpl();
        decoder = new ParserImpl.DecoderImpl();
        reconnectManager = new ReconnectManager(config);
        state = State.INITIAL;
        connectionPath = Utils.getConnectionPath(url, config.path);
    }

    /**
     * Given a namespace, creates a socket instance and stores it in a map.
     *
     * @param namespace Namespace that socket will be connected to.
     * @return Socket instance that was just created.
     */
    Socket createSocket(String namespace) {
        if(namespace == null || namespace.equals(""))
            namespace = "/";
        else if(!namespace.startsWith("/"))
            namespace = "/" + namespace;

        return sockets.computeIfAbsent(namespace, namespaceKey -> new Socket(namespaceKey, this));
    }

    /**
     * Removes a given socket from the sockets map.
     * If there no more sockets left in the map, closes the engine, and then itself.
     *
     * @param socket Socket to remove.
     */
    void disconnectSocket(Socket socket) {
        sockets.remove(socket.namespace);

        // If there aren't any sockets left to manage, close the underlying engine.
        if(sockets.isEmpty()) {
            IO.removeManager(connectionPath);
            submit(engine::close);
        }
    }

    /**
     * Open the underlying conenction. See {@link EngineSocket} for details.
     */
    void open() {
        if(state != State.INITIAL && state != State.ABRUPTLY_CLOSED)
            return;

        state  = State.OPENING;
        engine.once(EngineSocket.OPEN, args -> onOpen());
        engine.once(EngineSocket.ABRUPT_CLOSE, this::onAbruptClose);
        engine.once(EngineSocket.ERROR, this::onError);
        engine.once(EngineSocket.CLOSE, this::onClose);
        engine.open();
    }

    private void onOpen() {
        state = State.OPEN;
        engine.on(EngineSocket.MESSAGE, this::onData);
        engine.once(EngineSocket.ERROR, this::onError);
        engine.on(EngineSocket.PING, argv -> emitToAllSockets(PING, argv));
        engine.on(EngineSocket.PONG, argv -> emitToAllSockets(PONG, argv));
        engine.on(EngineSocket.UPGRADE, argv -> emitToAllSockets(UPGRADE, argv));
        engine.on(EngineSocket.UPGRADE_ATTEMPT, argv -> emitToAllSockets(UPGRADE_ATTEMPT, argv));
        engine.on(EngineSocket.UPGRADE_FAIL, argv -> emitToAllSockets(UPGRADE_FAIL, argv));

        // Assign sockets their associated session id. (The engine's id.)
        sockets.values().forEach(socket -> socket.socketId = engine.getSessionId());
        reconnectManager.reset();
        emitEvent(OPEN);
    }

    private void unregisterFromEngineEvents() {
        engine.removeAllListenersForEvent(EngineSocket.MESSAGE);
        engine.removeAllListenersForEvent(EngineSocket.CLOSE);
        engine.removeAllListenersForEvent(EngineSocket.PING);
        engine.removeAllListenersForEvent(EngineSocket.PONG);
        engine.removeAllListenersForEvent(EngineSocket.ERROR);
        engine.removeAllListenersForEvent(EngineSocket.UPGRADE);
        engine.removeAllListenersForEvent(EngineSocket.UPGRADE_ATTEMPT);
        engine.removeAllListenersForEvent(EngineSocket.UPGRADE_FAIL);
    }

    private void onAbruptClose(Object... args) {
        state = State.ABRUPTLY_CLOSED;
        unregisterFromEngineEvents();
        emitToAllSockets(ABRUPT_CLOSE, args);
        if(config.reconnect)
            tryReconnect();
    }

    private void onError(Object... args) {
        commonCleanUp(ERROR, args);
    }

    private void onClose(Object... args) {
        commonCleanUp(CLOSE, args);
    }

    private void commonCleanUp(String event, Object... args) {
        state = State.CLOSED;
        unregisterFromEngineEvents();
        emitToAllSockets(event, args);
        removeAllListeners();
        sockets.clear();
    }

    private void onData(Object... args) {
        submit(() -> {
            Object data = args[0];
            if(data instanceof String)
                decoder.add((String) data, (packet) -> emitEvent(PACKET, packet));
            else if(data instanceof byte[])
                decoder.add((byte[]) data, (packet) -> emitEvent(PACKET, packet));
        });
    }


    void sendPacket(Packet packet) {
        submit(() -> {
            encoder.encode(packet, (encodedObjects) -> {
                for (Object obj : encodedObjects) {
                    if (obj instanceof byte[])
                        engine.send((byte[]) obj);
                    else
                        engine.send((String) obj);
                }
            });
        });
    }

    private void tryReconnect() {
        if(!reconnectManager.shouldReconnect()) {
            reconnectManager.reset();
            emitEvent(RECONNECT_FAIL, "Maximum number of attempts has been reached!");
            return;
        }

        int delay = reconnectManager.calculateDelay();
        schedule(() -> {
                        emitEvent(RECONNECT_ATTEMPT, reconnectManager.reconnectsAttempted, delay);
                        open();
                      }, delay);
    }

    private void emitToAllSockets(String event, Object... args) {
        for(Socket socket : sockets.values())
            socket.emitEvent(event, args);
    }

    public boolean isOpen() {
        return state == State.OPEN;
    }

    public boolean isOpening() {
        return state == State.OPENING;
    }

    public static final String OPEN = EngineSocket.OPEN;
    public static final String ERROR = EngineSocket.ERROR;
    public static final String CLOSE = EngineSocket.CLOSE;
    public static final String ABRUPT_CLOSE = EngineSocket.ABRUPT_CLOSE;
    public static final String PING = EngineSocket.PING;
    public static final String PONG = EngineSocket.PONG;
    public static final String PACKET = "packet";
    public static final String UPGRADE = EngineSocket.UPGRADE;
    public static final String UPGRADE_ATTEMPT = EngineSocket.UPGRADE_ATTEMPT;
    public static final String UPGRADE_FAIL = EngineSocket.UPGRADE_FAIL;
    public static final String RECONNECT_FAIL = "reconnect_failed";
    public static final String RECONNECT_ATTEMPT = "reconnect_attempt";


    private static class ReconnectManager {

        private int maxReconnectAttempts;
        private int reconnectDelay;
        private int maxReconnectDelay;
        private double randomizationFactor;
        private int reconnectsAttempted;

        private ReconnectManager(ClientConfig options) {
            maxReconnectAttempts = options.maxReconnectAttempts;
            reconnectDelay = (options.reconnectDelay < 100 ? 100 : options.reconnectDelay);
            maxReconnectDelay = options.maxReconnectDelay;
            randomizationFactor = (options.randomizationFactor < 0.0 || options.randomizationFactor > 1.0 ? .5 : options.randomizationFactor);
            reconnectsAttempted = 0;
        }

        void reset() {
            reconnectsAttempted = 0;
        }

        int calculateDelay() {
            int randFactor = (int) (reconnectDelay * randomizationFactor);
            int delay = reconnectDelay * (int) Math.pow(2, reconnectsAttempted);
            ++reconnectsAttempted;
            delay += (Math.random() > .5 ? randFactor : -randFactor);
            return delay > maxReconnectDelay ? maxReconnectDelay : delay;
        }

        boolean shouldReconnect() {
            return reconnectsAttempted < maxReconnectAttempts;
        }
    }

}
