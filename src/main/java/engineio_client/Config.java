package engineio_client;

import engineio_client.parser.Parser;
import engineio_client.transports.PollingTransport;
import engineio_client.transports.WebSocketTransport;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.WebSocket;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Configures the Engine.IO client.
 * Available configurations are:
 * <p> {@code String scheme} Scheme, that indicates the protocol. (http/ws, http by default)
 * <p> {@code String path} Path that the connection should be made. ("/engine.io/" by default)
 * <p> {@code String hostname} Hostname for the connection to be made.
 * <p> {@code int port} Port number for the connection to be made.
 * <p> {@code String[] transports} Which transports should be used.
 *      <br>Options are {@link PollingTransport#NAME} and {@link WebSocketTransport#NAME}.
 *      <br>By default, connections starts with a {@link PollingTransport} and then gets upgraded to a {@link WebSocketTransport} if possible.
 *      <br>In order to use only one of them exclusively, alter this option like so,
 *          {@code config.transports = new String[]{PollingTransport.NAME}} or {@code config.transports = new String[]{WebSocketTransport.NAME}}.
 * <p> {@code Call.Factory callFactory} OkHttp Call.Factory to use when submitting poll requests.
 * <p> {@code WebSocket.Factory webSocketFactory} OkHttp WebSocket.Factory to use when making WebSocket requests.
 * <p> {@code Map<String, String> queryMap} Query options for the connection.
 * <p> {@code Map<String, String> headerMap} Headers for the connection.
 */
public class Config implements Cloneable {

    public String scheme;
    public String path;
    public String hostname;
    public int port;
    public String[] transports;
    public Call.Factory callFactory;
    public WebSocket.Factory webSocketFactory;
    public Map<String, String> queryMap;
    public Map<String, String> headerMap;

    public Config() {
        scheme = "http";
        path = "/engine.io/";
        transports = new String[]{PollingTransport.NAME, WebSocketTransport.NAME};
        callFactory = new OkHttpClient();
        webSocketFactory = (WebSocket.Factory) callFactory;
        headerMap = new HashMap<>();
        // There are 3 queries required by engine.io protocol.
        // 1- EIO (engine.io protocol version)
        // 2- transport (current transport in use, polling/websocket). This is set by the transports just before making a request.
        // 3- sid (session id of the engine.io-client instance. This is also set by the transports when handshake data is received.
        queryMap = new HashMap<String, String>(){{
            put("EIO", Parser.VERSION);
        }};
    }

    // Added in order to make this method visible in ConfigCloneTest test class.
    @Override
    protected Config clone() throws CloneNotSupportedException {
        Config copyConfig = (Config) super.clone();
        copyConfig.queryMap = new HashMap<>(queryMap);
        copyConfig.headerMap = new HashMap<>(headerMap);
        copyConfig.transports = Arrays.copyOf(transports, 2);
        copyConfig.callFactory = new OkHttpClient();
        copyConfig.webSocketFactory = (WebSocket.Factory) copyConfig.callFactory;
        return copyConfig;
    }
}
