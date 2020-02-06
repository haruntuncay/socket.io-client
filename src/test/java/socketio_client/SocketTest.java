package socketio_client;

import connection.ConnectingTest;
import org.junit.*;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.function.BiConsumer;

import static org.junit.Assert.*;

public class SocketTest extends ConnectingTest {

    @BeforeClass
    public static void beforeClass() {
        startServer();
    }

    @AfterClass
    public static void afterClass() {
        stopServer();
    }

    private void testHelper(String testName, IO io, BiConsumer<Socket, CountDownLatch> consumer) {
        CountDownLatch latch = new CountDownLatch(1);
        Socket socket = io.socket();
        consumer.accept(socket, latch);
        socket.open();
        await(testName, latch);
    }

    @Test
    public void testOpen() {
        BiConsumer<Socket, CountDownLatch> openTestConsumer = (socket, latch) -> {
            socket.on(Socket.CONNECT, args -> socket.close());
            socket.on(Socket.DISCONNECT, args -> latch.countDown());
        };

        testHelper("normal", IO.of(socketIoUrl), openTestConsumer);
        testHelper("normalPollingOnly", IO.of(socketIoUrl).pollingOnly(), openTestConsumer);
        testHelper("normalWebSocketOnly", IO.of(socketIoUrl).webSocketOnly(), openTestConsumer);
    }

    @Test
    public void testReconnectOnce() {
        testHelper("reconnect", IO.of("http://localhost:1234"), (socket, latch) -> {
            socket.on(Socket.RECONNECT_ATTEMPT, args -> latch.countDown());
        });
    }

    @Test
    public void testReconnectMaxAttempts() {
        ClientConfig conf = new ClientConfig();
        conf.maxReconnectAttempts = 1;
        testHelper("reconnect", IO.of("http://localhost:1234", conf), (socket, latch) -> {
            socket.on(Socket.RECONNECT_FAIL, args -> latch.countDown());
        });
    }

    @Test
    public void testError() {
        testHelper("error", IO.of("http://localhost2"), (socket, latch) -> {
            socket.on(Socket.ERROR, args -> latch.countDown());
        });
    }

    private void doSendReceive(String testName, List<Socket> sockets) {
        sockets.forEach(socket -> {
            List<Object> messages = new LinkedList<>(Arrays.asList("hello", new byte[]{1}, "world"));
            CountDownLatch latch = new CountDownLatch(messages.size());
            socket.on(event, args -> {
                Object data = messages.remove(0);
                if(data instanceof byte[])
                    assertArrayEquals((byte[]) data, (byte[]) args[0]);
                else
                    assertEquals(data, args[0]);
                latch.countDown();
            });
            socket.open();
            messages.forEach(msg -> socket.emit("test", msg));
            await(testName, latch);
        });
    }

    private void sendAndReceiveHelper(List<IO> ioList) {
        List<Socket> sockets = new LinkedList<>();

        // Upgrading transports.
        ioList.forEach(io -> sockets.add(io.socket()));
        doSendReceive("testSendAndReceiveOnUpgradingTransport", sockets);
        sockets.clear();
        // Remove the sid from the IO query map so it doesn't include a closed "sessionId (sid)" in requests.
        ioList.forEach(io -> io.config().queryMap.remove("sid"));

        // Polling only sockets.
        ioList.forEach(io -> sockets.add(io.pollingOnly().socket()));
        doSendReceive("testSendAndReceiveOnPollingTransport", sockets);
        sockets.clear();
        // Remove the sid from the IO query map so it doesn't include a closed "sessionId (sid)" in requests.
        ioList.forEach(io -> io.config().queryMap.remove("sid"));

        // WebSocket only sockets.
        ioList.forEach(io -> sockets.add(io.webSocketOnly().socket()));
        doSendReceive("testSendAndReceiveOnWebSocketTransport", sockets);
        sockets.clear();
    }

    @Test
    public void testSendAndReceive() {
        List<IO> ioList = new LinkedList<>();
        ioList.add(IO.of(socketIoUrl));
        ioList.add(IO.of(socketIoUrl + nsp));

        sendAndReceiveHelper(ioList);
    }

    @Test
    public void testUpgradeSuccess() {
        testHelper("testUpgradeSuccess", IO.of(socketIoUrl), (socket, latch) -> {
            socket.on(Socket.UPGRADE, args -> latch.countDown());
        });
    }

    @Test
    public void testReceiveAck() {
        testHelper("testReceiveAck", IO.of(socketIoUrl).pollingOnly(), (socket, latch) -> {
            String msg = "hello";
            socket.emit("ack", "hello", (Ack) args -> {
                assertEquals(msg + "ack", args[0]);
                latch.countDown();
            });
        });
    }

    @Test
    public void testSendAck() {
        testHelper("testAck", IO.of(socketIoUrl).pollingOnly(), (socket, latch) -> {
            String msg = "hello";
            socket.on("requestAck", args -> {
                int len = args.length;
                assertEquals(2, len);
                assertTrue(args[len - 1] instanceof Ack);

                Ack ack = (Ack) args[len - 1];
                String ackMessage = args[0] + "ack";
                ack.call(ackMessage);
            });
            socket.on("serverAckReceive", args -> {
                assertEquals(msg + "ackack", args[0]);
                latch.countDown();
            });
            socket.emit("ack", msg, (Ack) argv -> {});
        });
    }

    @Test
    public void testClientInitiatedClose() {
        testHelper("testClientInitiatedClose", IO.of(socketIoUrl), (socket, latch) -> {
           socket.on(Socket.CONNECT, args -> socket.close());
           socket.on(Socket.DISCONNECT, args -> latch.countDown());
        });
    }

    @Test
    public void testServerInitiatedClose() {
        testHelper("testServerInitiatedClose", IO.of(socketIoUrl), (socket, latch) -> {
            socket.on(Socket.CONNECT, args -> socket.emit("test", "close"));
            socket.on(Socket.DISCONNECT, args -> latch.countDown());
        });
    }
}

