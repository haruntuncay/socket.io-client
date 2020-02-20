package engineio_client.transports;

import engineio_client.Config;
import engineio_client.parser.Packet;
import engineio_client.parser.Type;
import okhttp3.*;
import okio.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketException;

import static common.Worker.submit;

public class WebSocketTransport extends Transport {

    private final Logger logger = LoggerFactory.getLogger(WebSocketTransport.class);

    public static final String NAME = "websocket";
    /*
        An event that is special to WebSocketTransport.
        Emitted when the WebSocket connected opens rathers than when the open packet is received.
        This event is used when probing for a WebSocket connection.
     */
    public static final String WEBSOCKET_CONN_OPEN = "websocket_conn_open";

    private WebSocket.Factory webSocketFactory;
    private WebSocket webSocket;

    public WebSocketTransport(Config configuration) {
        super(configuration);
        webSocketFactory = configuration.webSocketFactory;
    }

    @Override
    public void open() {
        if(isOpen())
            return;

        Request.Builder requestBuilder = new Request.Builder()
                                                    .url(buildURLString(true));
        if(config.headerMap != null)
            config.headerMap.forEach(requestBuilder::addHeader);

        Request request = requestBuilder.build();
        webSocket = webSocketFactory.newWebSocket(request, new WebSocketListener());
    }

    @Override
    public void close(boolean isCloseClientInitiated) {
        if(isCloseClientInitiated)
            send(Packet.CLOSE);
        onClose(isCloseClientInitiated);
    }

    @Override
    void closeAbruptly(String message, Throwable throwable) {
        state = State.ABRUPTLY_CLOSED;
        if(throwable != null)
            emitEvent(Transport.ABRUPT_CLOSE, message, throwable);
        else
            emitEvent(Transport.ABRUPT_CLOSE, message);
        removeAllListeners();
    }

    private void onClose(boolean isCloseClientInitiated) {
        commonCleanUp(Transport.CLOSE);
    }

    @Override
    void handleError(String reason, Throwable throwable) {
        commonCleanUp(Transport.ERROR, reason, throwable);
    }

    private void commonCleanUp(String event, Object... args) {
        state = State.CLOSED;
        emitEvent(event, args);
        removeAllListeners();
        submit(() -> webSocket.close(1000, null));
    }

    @Override
    public void send(Packet packet) {
        if(state == State.INITIAL) {
            sendBuffer.offer(packet);
            return;
        }

        submit(() -> {
            parser.encodePacket(packet, encodedData -> {
                if(encodedData instanceof byte[])
                    webSocket.send(ByteString.of((byte[]) encodedData));
                else
                    webSocket.send((String) encodedData);
            });
        });
    }

    private void onPacket(Packet packet) {
        if(packet.getType() == Type.OPEN) {
            onOpenPacket(packet);
            flush();
        } else
            emitEvent(Transport.PACKET, packet);
    }

    @Override
    public void flush() {
        if(state == State.OPEN) {
            sendBuffer.forEach(this::send);
            sendBuffer.clear();
        }
    }

    private class WebSocketListener extends okhttp3.WebSocketListener {

        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            super.onOpen(webSocket, response);
            state = State.OPEN;
            emitEvent(WEBSOCKET_CONN_OPEN);
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            submit(() -> parser.decodePacket(text, WebSocketTransport.this::onPacket));
        }

        @Override
        public void onMessage(WebSocket webSocket, ByteString bytes) {
            submit(() -> parser.decodePacket(bytes.toByteArray(), WebSocketTransport.this::onPacket));
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            if(t instanceof SocketException) {
                logger.error("WebSocket connect failure, abruptly closed.", t);
                WebSocketTransport.this.closeAbruptly("WebSocket connection failure", t);
            }
            else {
                logger.error("WebSocket error. Response: {}", response, t);
                handleError("An error occurred." + (response != null ? response : ""), t);
            }
        }

        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
            close(false);
        }
    }
}
