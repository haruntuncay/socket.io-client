package connection;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.fail;

public class ConnectingTest {

    private static Process serverProcess;
    private static final int socketIOPort = 5000;
    private static final int engineIOPort = 5001;
    protected static final String nsp = "/nsp";
    protected final String socketIoUrl = "http://localhost:" + socketIOPort;
    protected final String engineIOUrl = "http://localhost:" + engineIOPort;
    protected final String secureUrl = "https://localhost:" + socketIOPort;
    protected final String event = "test";
    protected final int timeoutMs = 10000;

    protected static void startServer() {
        startServer(false);
    }

    protected static void startServer(boolean isSecureConnection) {
        if (serverProcess != null && serverProcess.isAlive())
            stopServer();

        List<String> envList = new LinkedList<>();
        envList.add("socketioPort=" + socketIOPort);
        envList.add("socketioNsp=" + nsp);
        envList.add("engineioPort=" + engineIOPort);
        if (isSecureConnection)
            envList.add("socketioSecure=1");

        try {
            serverProcess = Runtime.getRuntime().exec("node src\\test\\resources\\testserver.js", envList.toArray(new String[0]));
        } catch (IOException e) {
            fail("Exception during server open process: " + e);
        }
    }

    protected static void stopServer() {
        if (serverProcess == null)
            return;
        serverProcess.destroy();
        try {
            serverProcess.waitFor();
            serverProcess = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    protected void await(String testName, CountDownLatch latch) {
        try {
            boolean latchZeroed = latch.await(timeoutMs, TimeUnit.MILLISECONDS);
            if (!latchZeroed)
                fail("Open test '" + testName + "'timed out.");
        } catch (InterruptedException e) {
            fail("Test " + testName + " interrupted.");
        }
    }
}
