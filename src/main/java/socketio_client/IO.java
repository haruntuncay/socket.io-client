package socketio_client;

import engineio_client.transports.PollingTransport;
import engineio_client.transports.WebSocketTransport;
import exceptions.SocketIOException;
import okhttp3.Call;
import okhttp3.WebSocket;

import java.net.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static common.Utils.getConnectionPath;

/**
 * A utility class that is used to build SocketIO Client instances.
 * Available options:
 * <p> {@link IO#of(String)} a static factory method that must be called to get an IO instance.
 * <br> Example: {@code IO.of("http://localhost:3000");}
 * <p> {@link #noMultiplex} disable multiplexing. (creates a new connection for each socket)
 * <p> {@link #query(String, String)} add a query key-value pair.
 * <p> {@link #header(String, String)} add a header key-value pair.
 * <p> {@link #path(String)} sets the path of the connection.
 * <p> {@link #socket} creates and returns the configured socket instance.
 *
 * <p> These calls can be chained like so:
 * <p> {@code Socket socket = IO.of("http://localhost:3000").path("/chat").socket();}
 * <p> <b>Note:</b> The path part of the URI an the URI String is actually interpreted as the namespace and not the actual path.
 * <br> For example {@code IO.of("http://localhost/chat");} doesn't actually connects to "http://localhost/chat",
 *      but rather connects to the "http://localhost/socket.io" and uses "/chat" as the <b>namespace</b>.
 *      Therefore, if you want to connect to a different path than "/socket.io", use {@link #path(String)} to describe it.
 *
 * @see <a href="https://socket.io/docs/#Restricting-yourself-to-a-namespace">Namespace</a> documentation at socket.io website for details about namespaces.
 */
public class IO {

    static Map<String, Manager> managers = new ConcurrentHashMap<>();

    private URL url;
    private ClientConfig config;

    private IO(URL url, ClientConfig config) {
        this.url = url;
        this.config = config;
    }

    public static IO of(String url) {
        return of(url, new ClientConfig());
    }

    public static IO of(String url, ClientConfig config) {
        try {
            return of(new URL(url), config.clone());
        } catch (MalformedURLException e) {
            throw new SocketIOException("Can't parse {" + url + "} as URL.", e);
        } catch (CloneNotSupportedException e) {
            throw new SocketIOException("Cloning of config object is not supported.");
        }
    }

    public static IO of(URL url) {
        return new IO(url, new ClientConfig());
    }

    public static IO of(URL url, ClientConfig config) {
        try {
            return new IO(url, config.clone());
        } catch (CloneNotSupportedException e) {
            throw new SocketIOException("Cloning of config object is not supported.");
        }
    }

    /**
     * By default, sockets who connects to same hosts under different namespaces actually share a connection.
     * This can save network and memory resources.
     * <p> When multiplex option is true(it is by default), a new connection will only be made if
     *      the connection url is different than any of the existing ones
     *      or a connection to the same namespace already exists.
     * <p> By calling this method, you give up multiplexing, and will be using a different connection for each socket.
     *
     * @return IO (This) instance for method chaining.
     */
    public IO noMultiplex() {
        config.multiplex = false;
        return this;
    }

    /**
     * If the connection fails due to an error, reconnection attempts will be made in order to re-establish the connection.
     * Call this method to disable any reconnect attemps.
     *
     * @return IO (This) instance for method chaining.
     */
    public IO noReconnect() {
        config.reconnect = false;
        return this;
    }

    public IO pollingOnly() {
        config.transports = new String[]{PollingTransport.NAME};
        return this;
    }

    public IO webSocketOnly() {
        config.transports = new String[]{WebSocketTransport.NAME};
        return this;
    }

    /**
     * Replace the default okhttp3.Call.Factory with your own.
     * Usually used in order to provide a Call.Factory that can create secure connections with client credentials.
     * <p>
     * <b>NOTE:</b> If you call this method with a Call.Factory that is capable of establishing secure connections,
     *  make sure you have created your IO instance with "https" protocol. Otherwise, connections will be plain text.
     *
     * @see <a href="https://square.github.io/okhttp/https/">Okhttp HTTPS</a> for an explanation of how to provide a secure Call.Factory.
     *
     * @param callFactory
     * @return IO (This) instance for method chaining.
     */
    public IO callFactory(Call.Factory callFactory) {
        if(callFactory != null)
            config.callFactory = callFactory;
        return this;
    }

    /**
     * Replace the default okhttp3.WebSocket.Factory with your own.
     * Usually used in order to provide a WebSocket.Factory that can create secure connections with client credentials.
     * <p>
     * <b>NOTE:</b> If you call this method with a Call.Factory that is capable of establishing secure connections,
     *  make sure you have created your IO instance with "https" protocol. Otherwise, connections will be plain text.
     *
     * @see <a href="https://square.github.io/okhttp/https/">Okhttp HTTPS</a> for an explanation of how to provide a secure WebSocket.Factory.
     *
     * @param webSocketFactory
     * @return IO (This) instance for method chaining.
     */
    public IO webSocketFactory(WebSocket.Factory webSocketFactory) {
        if(webSocketFactory != null)
            config.webSocketFactory = webSocketFactory;
        return this;
    }

    /**
     * Enables users to add query options for requests.
     *
     * @param key Key of the query option.
     * @param value Value of the query option.
     * @return (This) instance for method chaining.
     */
    public IO query(String key, String value) {
        config.queryMap.putIfAbsent(key, value);
        return this;
    }

    /**
     * Enables users to add headers for the requests.
     *
     * @param key Key of the query option.
     * @param value Value of the query option.
     * @return (This) instance for method chaining.
     */
    public IO header(String key, String value) {
        config.headerMap.putIfAbsent(key, value);
        return this;
    }

    /**
     * The path given in the URI is actually interpreted as the namespace.
     * So, in order to change the path("/socket.io" by default), call this method with the desired path.
     *
     * @return (This) instance for method chaining.
     */
    public IO path(String path) {
        // Make sure path conforms to the form "/path_name/".
        path = path.startsWith("/") ? path : "/" + path;
        path = path.endsWith("/") ? path : path + "/";
        config.path = path;
        return this;
    }

    /**
     * Creates the socket instance with the given configuration.
     *
     * @return socket instance that was just configured and created.
     */
    public Socket socket() {
        // Get the actual connection path, which is hostname + port + config.path.
        // This is actually what managers are identified by. If another socket wants to connect to the same path with different namespace,
        //  we can just use the same manager and multiplex the sockets since Socket.IO packets pass namespace as a part of the data.
        String connectionPath = getConnectionPath(url, config.path);
        Manager manager = managers.get(connectionPath);
        String namespace = url.getPath().equals("") ? "/" : url.getPath();
        // If this namespace already exists, we have to create a new (connection)manager.
        boolean namespaceExists = manager != null && manager.sockets.get(namespace) != null;
        // If namespaceExists or multiplex is false, create new connection.
        boolean shouldCreateNewConnection = namespaceExists || !config.multiplex;
        // If a manager for this host doesn't exist or shouldCreateNewConnection, create a new (connection)manager.
        if(manager == null || shouldCreateNewConnection) {
            manager = new Manager(url, config);
            // Save the manager instance only if it isn't explicitly requested anew with (forceNew or !multiplex).
            if(!shouldCreateNewConnection)
                managers.putIfAbsent(connectionPath, manager);
        }

        return manager.createSocket(namespace);
    }

    public ClientConfig config() {
        return config;
    }

    /**
     * Called by the manager when it's closed. A closed manager can't be re-opened.
     * And since multiplexing is possible, if we don't remove it,
     *      another multiplexed socket may end up binded to a closed manager instance.
     *
     * @param connectionPath ConnectionPath that the manager was identified with.
     */
    static void removeManager(String connectionPath) {
        managers.remove(connectionPath);
    }

}
