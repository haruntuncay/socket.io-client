package engineio_client;

import common.Observable;
import engineio_client.parser.Packet;
import engineio_client.parser.Type;
import engineio_client.transports.PollingTransport;
import engineio_client.transports.WebSocketTransport;
import engineio_client.transports.Transport;
import exceptions.EngineIOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.*;
import java.util.concurrent.Future;

import static common.Worker.*;

/**
 * Represents an engine.io connection.
 * <p> This class uses {@link Transport} instances to create and maintain engine.io connections.
 *      It registers callbacks for the events that are emitted by the {@link Transport} instances. Namely:
 * <p> {@link Transport#OPEN}, emitted when a transport is opened. This is <b>different</b> than a mere successful connection.
 * <br> For a transport to be considered open, an <b>open packet</b> has to be received with handshake data.
 * <br> A transport emits OPEN event only when it receives a packet of {@link Type#OPEN type open}.
 * <p> {@link Transport#PACKET}, emitted after the transport receives some data and parses it into a form of {@link Packet}.
 * <p> {@link Transport#ABRUPT_CLOSE}, emitted when the connection is closed due to an unforeseen circumstance. May be caused by an exception, also.
 * <p> {@link Transport#CLOSE}, emitted when the transport is successfully closed, either by the client itself or by the server.
 * <p> {@link Transport#HANDSHAKE}, emitted when the handshake data is received (from an open packet) and parsed.
 *
 * <p> An EngineSocket instance registers to these events to manage itself and to provide useful APIs to clients.
 * Write operations, just as poll/read operations do, leverages the underlying transport instance by delegating these requests.
 *
 * <p> EngineSocket instances themselves emits some events for the clients to register for. Namely:
 * <p> {@link #MESSAGE}, emitted after receiving a packet form the underlying transport,
 *      with packets data passed as the first argument to the callback.
 *      The received packet is the <b>engine.io packet</b> and its <b>data</b> is the <b>encoded socket.io packet</b>, if the socket.io is being used.
 * <p> {@link #OPEN}, emitted right after the underlying transport emits its OPEN event.
 * <p> {@link #ABRUPT_CLOSE}, emitted after {@link Transport#ABRUPT_CLOSE} to notify clients of abrupt connection loss.
 * <p> {@link #PING}, emitted after a PING packet is written to transport.
 *      Since both transport types use buffering, it doesn't necessarily mean the PING packet been sent.
 * <p> {@link #PONG}, emitted when the PONG packet is received.
 * <p> {@link #CLOSE}, emitted when an orderly close operation is complete, that is initiated either by the client or by the server.
 * <p> {@link #ERROR}, emitted in case of an unrecoverable error or when Socket.IO server refuses the client. (http status code 403)
 * <p> {@link #UPGRADE} emitted when an upgradable transport successfully moves to websocket transport from polling transport.
 * <p> {@link #UPGRADE_ATTEMPT} emitted when an upgrade attempt is about to be made.
 * <p> {@link #UPGRADE_FAIL} emitted when the upgrade attempt is failed.
 */
public class EngineSocket extends Observable {

    private final Logger logger = LoggerFactory.getLogger(EngineSocket.class);

    private Config config;
    private String sessionId;
    private Transport transport;
    /**
     * At what intervals a PING packet should be sent.
     * Decided by the server, sent in the open packet as a part of handshake data.
     */
    private int pingInterval;
    /**
     * How long to wait for a PONG packet to arrive after sending a PING packet.
     * If a PONG packet isn't received in pingInterval + pingTimeout milliseconds, consider the remote peer closed.
     */
    private int pingTimeout;
    /**
     * Future object, returned by scheduling a ping packet that will be send in pingInterval seconds.
     */
    private Future<?> pingIntervalFuture;
    /**
     * Future object, returned by scheduling a CLOSE operation that will be triggered
     *  when PONG packet is not received in pingInterval + pingTimeout milliseconds.
     */
    private Future<?> pingTimeoutFuture;

    public EngineSocket(URL url) {
        this(url, null);
    }

    public EngineSocket(URL url, Config config) {
        if(config == null)
            config = new Config();

        this.config = config;
        this.config.scheme = url.getProtocol();
        this.config.hostname = url.getHost();
        this.config.port = url.getPort() != -1 ? url.getPort()
                                                : "https".equals(url.getProtocol()) || "wss".equals(url.getProtocol()) ? 443 : 80;

        if(url.getQuery() != null)
            appendURIQueryToConfQuery(config, url.getQuery());
    }

    /**
     * Open the underlying transport/connection to the engine.io server.
     */
    public void open() {
        if(transport != null && transport.isOpen())
            return;

        // Create the transport instance and register for its events, as explained in the Class Documentation.
        createTransport();
        registerForTransportEvents();
        transport.open();
    }

    /**
     * Creates an instance of one of the transport objects that config.transports(Object[]) marks as available.
     * Takes the order into consideration.
     */
    private void createTransport() {
        if(config.transports == null || config.transports.length == 0) {
            logger.error("config.transports was null or empty, {}", (Object) config.transports);
            throw new EngineIOException("config.transports can't be null or empty");
        }

        if(PollingTransport.NAME.equals(config.transports[0]))
            transport = new PollingTransport(config);
        else if(WebSocketTransport.NAME.equals(config.transports[0]))
            transport = new WebSocketTransport(config);
        else {
            logger.error("Unknown transport {} was chosen.", transport);
            throw new EngineIOException("Unknown transport '" + config.transports[0] + "'.Choose either 'polling' or 'websocket'.");
        }
    }

    private void registerForTransportEvents() {
        transport.once(Transport.OPEN, args -> emitEvent(OPEN))
                .once(Transport.HANDSHAKE, args -> onHandshake((HandshakeData) args[0]))
                .once(Transport.ABRUPT_CLOSE, this::onAbruptClose)
                .once(Transport.ERROR, this::onError)
                .once(Transport.CLOSE, this::onClose)
                .on(Transport.PACKET, args -> onPacket((Packet) args[0]));
    }

    /**
     * Initiate a transport/connection close sequence.
     */
    public void close() {
        close(true);
    }

    private void close(boolean isCloseInitiatedByClient) {
        if(transport != null) {
            transport.close(isCloseInitiatedByClient);
            transport = null;
        }
    }

    private void onClose(Object... args) {
        commonCleanUp(CLOSE, args);
    }

    private void onAbruptClose(Object... args) {
        commonCleanUp(ABRUPT_CLOSE, args);
    }

    private void onError(Object... args) {
        commonCleanUp(ERROR, args);
    }

    /**
     * Clean up the EngineSocket instance and remove the "sid" query parameter, since it represents an open session id.
     * Removes all listeners afterwards, so that if the client wants to open the connection again, the callbacks won't be registered twice.
     *
     * @param event Event to emit.
     * @param args Any arguments that can shed light to what happened.
     */
    private void commonCleanUp(String event, Object... args) {
        transport = null;
        config.queryMap.remove("sid");
        if(pingIntervalFuture != null)
            pingIntervalFuture.cancel(false);
        if(pingTimeoutFuture != null)
            pingTimeoutFuture.cancel(false);
        if(event != null)
            emitEvent(event, args);
        removeAllListeners();
    }

    /**
     * Reads given handshake data to setup pingInterval, pingTimeout and sessionId.
     * Starts the ping sequence.
     * Most importantly, if both the server and client supports websocket communication,
     *  sends a probe to test whether websocket connection can be made or not, and if it succeeds, upgrades to websocket connection.
     *
     * @param handshakeData {@link HandshakeData} data.
     */
    private void onHandshake(HandshakeData handshakeData) {
        pingInterval = handshakeData.getPingInterval();
        pingTimeout = handshakeData.getPingTimeout();
        sessionId = handshakeData.getSessionId();
        doPing();
        // Check whether the server, as well as the client, supports the WebSocket connection or not.
        // We can check the handshake for the server, and config for the client.
        String[] upgrades = handshakeData.getUpgrades();
        boolean isWebSocketSupported = Arrays.asList(upgrades).contains(WebSocketTransport.NAME);
        boolean shouldProbeForWebSocket = Arrays.asList(config.transports).contains(WebSocketTransport.NAME);
        if(!isWebSocketSupported || !shouldProbeForWebSocket)
            return;
        //Server supports WebSockets. Send a probe over a webSocket connection.
        submit(this::probeForWebSocket);
    }

    /**
     * Probe process:
     *  - Create a WebSocket transport
     *  - Send a PING packet with "probe" as data.
     *  - Look for a PONG packet with "probe" as data.
     * If the PONG packet is received with same data, consider the test successful, failed otherwise.
     */
    private void probeForWebSocket() {
        PollingTransport pollingTransport = (PollingTransport) transport;
        emitEvent(UPGRADE_ATTEMPT);

        Transport webSocketTp = new WebSocketTransport(config);
        CallbackHandle openHandle = webSocketTp.once(WebSocketTransport.WEBSOCKET_CONN_OPEN,
                                                        args -> webSocketTp.send(new Packet(Type.PING, "probe")));
        Callback upgradeFailCallback = args -> {
            webSocketTp.removeAllListeners();
            pollingTransport.unPause();
            emitEvent(UPGRADE_FAIL, args);
        };

        CallbackHandle abruptCloseHandle = webSocketTp.once(WebSocketTransport.ABRUPT_CLOSE, upgradeFailCallback);
        CallbackHandle errorHandle = webSocketTp.once(WebSocketTransport.ERROR, upgradeFailCallback);

        webSocketTp.once(Transport.PACKET, args -> {
            Packet packet = (Packet) args[0];
            if(packet.getType() == Type.PONG && "probe".equals(packet.getData())) {
                pollingTransport.pause();
                // Send an UPGRADE packet to let the server know we are moving to a new transport (WebSocket transport).
                webSocketTp.send(new Packet(Type.UPGRADE));
                // Remove all the listeners from the old transport so once it's closed, it doesn'T cause confusion.
                pollingTransport.removeAllListeners();
                // This listener here is added in order to emit any packets that are received but not emitted.
                pollingTransport.on(Transport.PACKET, argv -> {
                    Packet pack = (Packet) argv[0];
                    if(pack.getType() == Type.MESSAGE)
                        emitEvent(MESSAGE, pack.getData());
                });
                // Clear one time listeners.
                openHandle.remove();
                abruptCloseHandle.remove();
                errorHandle.remove();
                // Move to new transport.
                transport = webSocketTp;
                // Register listeners for this new transport and flush any unsent packets from the old one.
                registerForTransportEvents();
                transport.flush();
                // Send any packets that was buffered on the old transport through this new one.
                Queue<Packet> bufferedPacketsLeft = pollingTransport.getBufferedPackets();
                bufferedPacketsLeft.forEach(transport::send);
                bufferedPacketsLeft.clear();

                emitEvent(UPGRADE);
            } else {
                // Broken upgrade sequence. Close webSocketTp.
                webSocketTp.removeAllListeners();
                webSocketTp.close(false);
                emitEvent(UPGRADE_FAIL, "Transport was open but didn't receive a PONG[probe] packet.Instead, received: " + packet);
            }
        });
        webSocketTp.open();
    }

    private void doPing() {
        pingIntervalFuture = schedule(() -> {
                                                transport.send(Packet.PING);
                                                emitEvent(PING);
                                            }, pingInterval);

        pingTimeoutFuture = schedule(() -> onError("Didn't receive pong packet in time."), pingInterval + pingTimeout);
    }

    public void send(String data) {
        transport.send(data);
    }

    public void send(byte[] data) {
        transport.send(data);
    }

    private void onPacket(Packet packet) {
        Type type = packet.getType();
        switch (type) {
            case OPEN:
            case NOOP:
                break;
            case MESSAGE:
                emitEvent(MESSAGE, packet.getData());
                break;
            case PING:
                break;
            case PONG:
                onPong();
                break;
            case CLOSE:
                close(false);
                break;
        }
    }

    private void onPong() {
        if(pingTimeoutFuture != null)
            pingTimeoutFuture.cancel(false);

        emitEvent(PONG);
        doPing();
    }

    /**
     * Add any queries that appears in the URI to Config.queryMap, because then the connection URL is built,
     *  it is query string is build by using this map.
     */
    private void appendURIQueryToConfQuery(Config conf, String uriQuery) {
        Arrays.stream(uriQuery.split("&"))
                .map(query -> query.split("="))
                .forEach(queryKeyValue -> {
                    if(queryKeyValue.length > 1)
                        conf.queryMap.put(queryKeyValue[0], queryKeyValue[1]);
                    else
                        conf.queryMap.put(queryKeyValue[0], "");
                });
    }

    public String getSessionId() {
        return sessionId;
    }

    public static final String MESSAGE = "message";
    public static final String OPEN = "open";
    public static final String ABRUPT_CLOSE = "abrupt_close";
    public static final String PING = "ping";
    public static final String PONG = "pong";
    public static final String CLOSE = "close";
    public static final String ERROR = "error";
    public static final String UPGRADE = "upgrade";
    public static final String UPGRADE_ATTEMPT = "upgrade_attempt";
    public static final String UPGRADE_FAIL = "upgrade_fail";

}
