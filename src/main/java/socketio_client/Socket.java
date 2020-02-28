package socketio_client;

import common.Observable;
import exceptions.SocketIOException;
import org.json.JSONArray;
import socketio_client.parser.Packet;
import socketio_client.parser.Type;
import java.util.*;

import static common.Utils.containsBinaryData;
import static common.Utils.jsonArrayToObjectArray;

/**
 * The Socket.IO Client instance.
 * Can be used to send and receive messages.
 * Provides several events that clients can register for. Namely:
 * <p> {@link #CONNECT} emitted when the socket is considered connected.
 *      For a socket to be considered connected, a CONNECT packet has to be received.
 * <p> {@link #ERROR} emitted when an unforeseen circumstance happens.
 * It can be anything from an exception to irrecoverable connection error.
 * If any, information about the error will be shared with the client as the parameter of callback.
 * <p> {@link #ERROR_PACKET} emitted when the server sends an error packet.
 * This is different and **Socket.ERROR** as this one just indicates that a socket.io packet with **ERROR** as the type is received.
 * <p> {@link #DISCONNECT} emitted when the socket instance is closed/disconnected.
 * This event designated a successful disconnection rather than an erroneous closing or loss of connection.
 * The connection can be closed/disconnected either by the client by calling {@link #close()} on the instance or by the server.
 * <p> {@link #PING} emitted when a PING packet is written.
 * <p> {@link #PONG} emitted when a PONG packet is received.
 * <p> {@link #ABRUPT_CLOSE} emitted when the connection is closed due to an error.
 * <p> {@link #CLOSE} emitted when the the underlying manager is closed.
 * <p> {@link #RECONNECT_ATTEMPT} emitted when a reconnect attempt is made.
 * Reconnect attempts will only be made if the connection is closed abruptly.
 * This means, when you or the server closes the connection willingly, no reconnect attempt will be made.
 * <p> {@link #RECONNECT_FAIL} emitted when maximum number of reconnect attempts is made and reconnection still failed.
 * <p> {@link #UPGRADE} emitted when an upgradable transport successfully moves to websocket transport from polling transport.
 * <p> {@link #UPGRADE_ATTEMPT} emitted when an upgrade attempt is about to be made.
 * <p> {@link #UPGRADE_FAIL} emitted when the upgrade attempt is failed.
 */
public class Socket extends Observable {

    String socketId;
    String namespace;
    private Manager manager;
    private int lastAckId;
    private List<Packet> outgoingPacketBuffer;
    private Map<Integer, Ack> ackMap;
    private State state;
    private List<CallbackHandle> callbackHandles;

    Socket(String namespace, Manager manager) {
        this.namespace = namespace;
        this.manager = manager;
        outgoingPacketBuffer = new LinkedList<>();
        ackMap = new HashMap<>();
        state = State.INITIAL;
        callbackHandles = new LinkedList<>();
    }

    public void connect() {
        open();
    }

    public void open() {
        if(state == State.CLOSED)
            throw new SocketIOException("Can't re-open a closed/disconnected socket. Create a new instance instead.");

        if(state == State.OPENING || state == State.OPEN)
            return;

        state = State.OPENING;
        // Register to manager's events so that Socket instance can be notified when OPEN, PACKET and CLOSE events occur.
        registerForManagerEvents();
        // If manager is already open, (since multiplexing is possible, it could be opened by another Socket instance)
        // the "onOpen" callback would never be called for this Socket instance.
        // Therefore that case is handled manually.
        if(manager.isOpening())
            ;
        else if(manager.isOpen()) {
            onOpen();
            if(Packet.DEFAULT_NAMESPACE.equals(namespace))
                handleConnectPacket();
        }
        else
            manager.open();
    }

    public void close() {
        disconnect();
    }

    private void disconnect(Object... args) {
        if(state == State.CLOSED)
            return;

        manager.sendPacket(new Packet(Type.DISCONNECT, namespace));
        manager.disconnectSocket(this);
        doCommonClosingCleanUp(DISCONNECT, args);
    }

    private void registerForManagerEvents() {
        callbackHandles.add(manager.on(Manager.OPEN, this::onOpen));
        callbackHandles.add(manager.on(Manager.PACKET, this::onPacket));
        callbackHandles.add(manager.on(Manager.CLOSE, this::onClose));
        callbackHandles.add(manager.on(Manager.ERROR, this::onError));
        callbackHandles.add(manager.on(Manager.ABRUPT_CLOSE, args -> emitEvent(ABRUPT_CLOSE, args)));
        callbackHandles.add(manager.on(Manager.RECONNECT_ATTEMPT, args -> emitEvent(RECONNECT_ATTEMPT, args)));
        callbackHandles.add(manager.on(Manager.RECONNECT_FAIL, args -> emitEvent(RECONNECT_FAIL, args)));
        callbackHandles.add(manager.on(Manager.PING, args -> emitEvent(PING, args)));
        callbackHandles.add(manager.on(Manager.PONG, args -> emitEvent(PONG, args)));
        callbackHandles.add(manager.on(Manager.UPGRADE, args -> emitEvent(UPGRADE, args)));
        callbackHandles.add(manager.on(Manager.UPGRADE_ATTEMPT, args -> emitEvent(UPGRADE_ATTEMPT, args)));
        callbackHandles.add(manager.on(Manager.UPGRADE_FAIL, args -> emitEvent(UPGRADE_FAIL, args)));
    }

    private void onOpen(Object... args) {
        // Send a connect packet only if the namespace != DEFAULT_NAMESPACE("/").
        // Reason: When a network connection to socket.io server is made, a connection request to "/" namespace is assumed.
        // So there is no reason to send a Connect Packet with namespace "/".
        if(!Packet.DEFAULT_NAMESPACE.equals(namespace))
            handleOutgoingPacket(new Packet(Type.CONNECT, namespace));
    }

    private void onClose(Object... args) {
        doCommonClosingCleanUp(CLOSE, args);
    }

    private void onError(Object... args) {
        doCommonClosingCleanUp(ERROR, args);
    }

    private void doCommonClosingCleanUp(String event, Object... args) {
        state = State.CLOSED;
        emitEvent(event, args);
        removeAllListeners();
        // Clean up any resources/buffers/references.
        manager = null;
        outgoingPacketBuffer.clear();
        outgoingPacketBuffer = null;
        ackMap.clear();
        ackMap = null;
        callbackHandles.forEach(CallbackHandle::remove);
        callbackHandles.clear();
        callbackHandles = null;
    }

    public Socket send(Object... args) {
        return emit("message", args);
    }

    public Socket emit(String event, Object... args) {
        if(state == State.CLOSED || args == null || args.length == 0)
            return this;

        int argsLength = args.length;
        Ack ack = args[argsLength - 1] instanceof Ack ? (Ack) args[--argsLength] : null;

        Object[] objs = new Object[argsLength];
        System.arraycopy(args, 0, objs, 0, argsLength);

        return emit(event, objs, ack);
    }

    public Socket emit(String event, Object[] args, Ack ack) {
        if(state == State.CLOSED || args == null || args.length == 0)
            return this;

        JSONArray jsonArray = new JSONArray();
        jsonArray.put(event);

        for(Object arg : args)
            jsonArray.put(arg);

        Type type = containsBinaryData(args) ? Type.BINARY_EVENT : Type.EVENT;
        Packet packet = new Packet(type,
                namespace,
                ack != null ? lastAckId : -1,
                jsonArray);

        // Ack object with id=lastAckId will be called when an acknowledgement packet is received with respective id.
        if(ack != null)
            ackMap.put(lastAckId++, ack);

        if(state == State.OPEN)
            handleOutgoingPacket(packet);
        else
            outgoingPacketBuffer.add(packet);

        return this;
    }

    private void handleOutgoingPacket(Packet packet) {
        manager.sendPacket(packet);
    }

    private void onPacket(Object... args) {
        Packet packet = (Packet) args[0];
        if(!namespace.equals(packet.getNamespace()))
            return;

        Type type = packet.getType();
        switch (type) {
            case CONNECT:
                handleConnectPacket();
                break;
            case DISCONNECT:
                handleDisconnectPacket(packet);
                break;
            case ERROR:
                handleErrorPacket(packet);
                break;
            case EVENT:
            case BINARY_EVENT:
                handleEventPacket(packet);
                break;
            case ACK:
            case BINARY_ACK:
                handleAckPacket(packet);
                break;
        }
    }

    private void handleConnectPacket() {
        state = State.OPEN;
        handleBufferedPackets();
        emitEvent(CONNECT);
    }

    private void handleBufferedPackets() {
        outgoingPacketBuffer.forEach(this::handleOutgoingPacket);
        outgoingPacketBuffer.clear();
    }

    private void handleDisconnectPacket(Packet packet) {
        disconnect(packet);
    }

    private void handleErrorPacket(Packet packet) {
        emitEvent(ERROR_PACKET, packet.getData());
    }

    private void handleEventPacket(Packet packet) {
        List<Object> data = ((JSONArray) packet.getData()).toList();
        String eventName = (String) data.remove(0);

        // If packet.id is > -1, an acknowledgement is requested by server.
        // Pass the client an Ack object so that users can call it with any arguments they want.
        if(packet.shouldBeAcknowledged())
            data.add(createAckForId(packet.getId()));

        Object[] args = data.toArray();
        emitEvent(eventName, args);
    }

    private void handleAckPacket(Packet packet) {
        Ack ack = ackMap.get(packet.getId());
        if(ack == null)
            return;
        ack.call(jsonArrayToObjectArray((JSONArray) packet.getData()));
    }

    private Ack createAckForId(int packetId) {
        return args -> {
            Type type = containsBinaryData(args) ? Type.BINARY_ACK : Type.ACK;
            Packet packet = new Packet(type,
                                        namespace,
                                        packetId,
                                        new JSONArray(args));
            handleOutgoingPacket(packet);
        };
    }

    public String getSocketId() {
        return socketId;
    }

    public Manager getManager() {
        return manager;
    }

    public static final String CONNECT = "connect";
    public static final String ERROR = "error";
    public static final String ERROR_PACKET = "error_packet";
    public static final String DISCONNECT = "disconnect";
    public static final String PING = Manager.PING;
    public static final String PONG = Manager.PONG;
    public static final String ABRUPT_CLOSE = Manager.ABRUPT_CLOSE;
    public static final String CLOSE = Manager.CLOSE;
    public static final String RECONNECT_ATTEMPT = Manager.RECONNECT_ATTEMPT;
    public static final String RECONNECT_FAIL = Manager.RECONNECT_FAIL;
    public static final String UPGRADE = Manager.UPGRADE;
    public static final String UPGRADE_ATTEMPT = Manager.UPGRADE_ATTEMPT;
    public static final String UPGRADE_FAIL = Manager.UPGRADE_FAIL;
}
