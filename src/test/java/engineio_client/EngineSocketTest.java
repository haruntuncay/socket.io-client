package engineio_client;

import engineio_client.transports.PollingTransport;
import engineio_client.transports.WebSocketTransport;
import connection.ConnectingTest;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.function.BiConsumer;

import static org.junit.Assert.*;

public class EngineSocketTest extends ConnectingTest {

    private static final Config pollingConfig;
    private static final Config webSocketConfig;
    private static final List<Config> configList;

    static {
        pollingConfig = new Config();
        pollingConfig.transports = new String[]{PollingTransport.NAME};

        webSocketConfig = new Config();
        webSocketConfig.transports = new String[]{WebSocketTransport.NAME};

        configList = new LinkedList<Config>() {{
            add(pollingConfig);
            add(webSocketConfig);
            add(new Config());
        }};
    }

    @BeforeClass
    public static void beforeClass() {
        startServer();
    }

    @AfterClass
    public static void afterClass() {
        stopServer();
    }

    private void testHelper(String testName, Config config, String url, BiConsumer<EngineSocket, CountDownLatch> engineConsumer) {
        try {
            CountDownLatch latch = new CountDownLatch(1);
            EngineSocket engine = new EngineSocket(new URL(url), config);
            engineConsumer.accept(engine, latch);
            engine.open();
            await(testName, latch);
        } catch(MalformedURLException e) {
            fail("Test '" + testName + "' failed with exception: " + e);
        }
    }

    @Test
    public void testOpenForNonExistingHost() {
        configList.forEach(config -> {
            testHelper("testOpenForNonExistingHost", config, "http://localhost2/",
                    (engine, latch) -> engine.on(EngineSocket.ERROR, args -> {
                        assertTrue(args[1] instanceof UnknownHostException);
                        latch.countDown();
                    }));
        });
    }

    @Test
    public void testOpenForClosedServer() {
        configList.forEach(config -> {
            testHelper("testOpenForClosedServer", config, "http://localhost:4321/",
                    (engine, latch) -> engine.on(EngineSocket.ABRUPT_CLOSE, args -> {
                        assertTrue(args[1] instanceof ConnectException);
                        latch.countDown();
                    }));
        });
    }

    @Test
    public void testOpenSuccess() {
        configList.forEach(config -> {
            testHelper("testOpenSuccess", config, engineIOUrl,
                    (engine, latch) -> {
                        engine.on(EngineSocket.OPEN, args -> {
                            engine.close();
                            latch.countDown();
                        });
                    });
        });
    }

    @Test
    public void testClientInitiatedClose() {
        configList.forEach(config -> {
            testHelper("testClientInitiatedClose", config, engineIOUrl,
                    (engine, latch) -> {
                        engine.on(EngineSocket.OPEN, args -> engine.close());
                        engine.on(EngineSocket.CLOSE, args -> latch.countDown());
                    });
        });
    }

    @Test
    public void testServerInitiatedClose() {
        configList.forEach(config -> {
            testHelper("testServerInitiatedClose", config, engineIOUrl,
                    (engine, latch) -> {
                        engine.on(EngineSocket.OPEN, args -> engine.send("hello"));
                        engine.on(EngineSocket.OPEN, args -> engine.send("world"));
                        engine.on(EngineSocket.OPEN, args -> engine.send("close"));
                        engine.on(EngineSocket.UPGRADE, args -> System.out.println("Upgraded"));
                        engine.on(EngineSocket.CLOSE, args -> latch.countDown());
                    });
        });
    }

    private void sendAndReceiveHelper(String testName, List<?> messages) {
        configList.forEach(config -> {
            try {
                CountDownLatch latch = new CountDownLatch(messages.size());

                EngineSocket engine = new EngineSocket(new URL(engineIOUrl), config);

                engine.on(EngineSocket.MESSAGE, args -> {
                    if(args[0] instanceof byte[])
                        assertArrayEquals((byte[]) messages.remove(0), (byte[]) args[0]);
                    else
                        assertEquals(messages.remove(0), args[0]);

                    latch.countDown();
                });

                engine.on(EngineSocket.OPEN, args -> {
                    messages.forEach(msg -> {
                        if(msg instanceof byte[])
                            engine.send((byte[]) msg);
                        else
                            engine.send((String) msg);
                    });
                });

                engine.open();
                await(testName, latch);
                engine.close();
            } catch (MalformedURLException e) {
                fail("Malformed URL: " + engineIOUrl);
            }
        });
    }

    @Test
    public void testSendAndReceiveString() {
        List<String> data = new LinkedList<>();
        data.add("hello");
        data.add("world");
        data.add("running testSendAndReceiveString");

        sendAndReceiveHelper("testSendAndReceiveString", data);
    }

    @Test
    public void testSendAndReceiveBinary() {
        List<byte[]> data = new LinkedList<>();
        data.add(new byte[]{1, 2, 3});
        data.add(new byte[]{1});

        sendAndReceiveHelper("testSendAndReceiveBinary", data);
    }

    @Test
    public void testSendAndReceiveMixed() {
        List<Object> data = new LinkedList<>();
        data.add(new byte[]{1, 2, 3});
        data.add("hello world");
        data.add(new byte[]{3, 2, 1});
        data.add("world hello");

        sendAndReceiveHelper("testSendAndReceiveMixed", data);
    }

    @Test
    public void testUpgradeSuccess() {
        testHelper("testUpgradeSuccess", new Config(), engineIOUrl, (engine, latch) -> {
            engine.on(EngineSocket.UPGRADE, args -> latch.countDown());
        });
    }

    @Test
    public void testUpgradeFail() {
        Config conf = new Config();
        testHelper("testUpgradeFail", conf, engineIOUrl, (engine, latch) -> {
            engine.on(EngineSocket.UPGRADE_ATTEMPT, args -> conf.queryMap.put("sid", "non-existing-sid"));
            engine.on(EngineSocket.UPGRADE_FAIL, args -> latch.countDown());
        });
    }
}
