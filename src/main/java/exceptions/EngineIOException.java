package exceptions;

public class EngineIOException extends RuntimeException {

    public EngineIOException(String message) {
        super(message);
    }

    public EngineIOException(String message, Throwable cause) {
        super(message, cause);
    }
}
