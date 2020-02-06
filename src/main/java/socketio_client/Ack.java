package socketio_client;

/**
 * An object that represents an Acknowledgement.
 * If client wants an acknowledgement, he/she will create an implementation of this class and
 *      it will be called with the data that server passes.
 * If the server wants an acknowledgement, the {@link Socket Socket.IO-Client Socket} will create and pass it as an argument
 *      so that client can call it with any data he/she wants.
 */
public interface Ack {

    void call(Object... args);
}
