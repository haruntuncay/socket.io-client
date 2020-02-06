package socketio_client;

public enum State {

    INITIAL, // Default state.
    OPENING, // Indicates a Socket/Manger instance that has just started a connection request.
    OPEN, // Indicates a Socket/Manager instance that has completed an OPEN operation properly.
    ABRUPTLY_CLOSED, // Indicates a Socket/Manager that is closed due to an unforeseen circumstance.
    CLOSED; // Indicates a Socket/Manager that is closed either by the Server or the Client itself.
}
