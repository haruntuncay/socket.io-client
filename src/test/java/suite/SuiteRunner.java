package suite;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)

@Suite.SuiteClasses({
    common.ObservableTest.class,
    common.UtilsTest.class,

    engineio_client.parser.PacketTest.class,
    engineio_client.parser.ParserTest.class,
    engineio_client.parser.TypeTest.class,
    engineio_client.ConfigCloneTest.class,
    engineio_client.EngineSocketTest.class,
    engineio_client.HandshakeTest.class,

    socketio_client.IOTest.class,
    socketio_client.parser.ParserTest.class,
    socketio_client.parser.TypeTest.class,
    socketio_client.SocketTest.class,
    socketio_client.SSLConnectionTest.class,
})

public class SuiteRunner {
}
