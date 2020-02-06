package exceptions;

public class SocketIOParserException extends RuntimeException{

    public SocketIOParserException(String message) {
        super(message);
    }

    public SocketIOParserException(String message, Throwable cause) {
        super(message, cause);
    }
}
