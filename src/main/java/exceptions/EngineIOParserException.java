package exceptions;

public class EngineIOParserException extends RuntimeException {

    public EngineIOParserException(String message) {
        super(message);
    }

    public EngineIOParserException(String message, Throwable cause) {
        super(message, cause);
    }
}
