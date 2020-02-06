package exceptions;

public class SocketIOException extends RuntimeException{

    public SocketIOException(String message) {
        super(message);
    }

    public SocketIOException(String message, Throwable cause) {
        super(message, cause);
    }
}
