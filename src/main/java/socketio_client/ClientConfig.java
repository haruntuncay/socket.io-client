package socketio_client;

import engineio_client.Config;

/**
 * Configures the Socket.IO client.
 * Available configurations are:
 * <p> {@code int maxReconnectAttempts} Maximum number of times to try to reconnect. (Integer.MAX_VALUE by default)
 * <p> {@code int reconnectDelay} Base wait time before a recurring reconnect request, in milliseconds.  (500 ms by default)
 * <p> {@code int maxReconnectDelay} An upper bound for reconnect delay. (10,000 ms by default)
 * <p> {@code double randomizationFactor} Factor of randomization used when calculating the delay
 * <p> {@code boolean reconnect} Whether the socket should try to reconnect or not after <b>{@link Manager#ABRUPT_CLOSE}</b> occurs.
 * <p> {@code boolean multiplex} Whether Socket.IO connections to different namespaces should be multiplexed or not. (true by default)
 */
public class ClientConfig extends Config implements Cloneable {

    int maxReconnectAttempts;
    int reconnectDelay;
    int maxReconnectDelay;
    double randomizationFactor;
    boolean reconnect;
    boolean multiplex;

    public ClientConfig() {
        super();
        path = "/socket.io/";
        maxReconnectAttempts = Integer.MAX_VALUE;
        reconnectDelay = 500;
        maxReconnectDelay = 10000;
        randomizationFactor = .5;
        reconnect = true;
        multiplex = true;
    }

    @Override
    protected ClientConfig clone() throws CloneNotSupportedException {
        return (ClientConfig) super.clone();
    }
}
