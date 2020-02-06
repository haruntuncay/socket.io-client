package socketio_client;

import connection.ConnectingTest;
import okhttp3.OkHttpClient;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.*;

public class SSLConnectionTest extends ConnectingTest {

    @BeforeClass
    public static void beforeClass() {
        startServer(true);
    }

    @AfterClass
    public static void afterClass() {
        stopServer();
    }

    @Test
    public void testSSLSendAndReceive() {
        OkHttpClient sslEnabledHttpClient = getSSLEnabledHttpClient();
        IO io = IO.of(secureUrl)
                .callFactory(sslEnabledHttpClient)
                .webSocketFactory(sslEnabledHttpClient);

        // Upgrading transport.
        Socket upgradingSocket = io.socket();
        doSendReceive("testSendAndReceiveOnUpgradingTransport", Collections.singletonList(upgradingSocket));
        // Remove the sid from the IO query map so it doesn't include a closed "sessionId (sid)" in requests.
        io.config().queryMap.remove("sid");

        // Polling only socket.
        Socket pollSocket = io.pollingOnly().socket();
        doSendReceive("testSSLSendAndReceiveOnPollingTransport", Collections.singletonList(pollSocket));
        // Remove the sid from the IO query map so it doesn't include a closed "sessionId (sid)" in requests.
        io.config().queryMap.remove("sid");

        // WebSocket only socket.
        Socket webSocket = io.webSocketOnly().socket();
        doSendReceive("testSendAndReceiveOnWebSocketTransport", Collections.singletonList(webSocket));
    }

    private OkHttpClient getSSLEnabledHttpClient() {
        try {
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            Certificate cert = certificateFactory.generateCertificate(new FileInputStream("src\\test\\resources\\cert.pem"));

            // Put the certificates a key store.
            char[] password = "password".toCharArray(); // Any password will work.

            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, password);
            keyStore.setCertificateEntry("cert", cert);

            // Use it to build an X509 trust manager.
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("SunX509");
            keyManagerFactory.init(keyStore, password);

            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance("SunX509");
            trustManagerFactory.init(keyStore);

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), null);
            SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            X509TrustManager trustManager = (X509TrustManager) trustManagerFactory.getTrustManagers()[0];

            return new OkHttpClient.Builder()
                    .hostnameVerifier((hostname, session) -> "localhost".equals(hostname))
                    .sslSocketFactory(sslSocketFactory, trustManager)
                    .build();
        } catch (CertificateException | KeyStoreException | NoSuchAlgorithmException
                | UnrecoverableKeyException | KeyManagementException | IOException e) {

            fail("Creation of SSLEnabled OkHttpClient failed." + e);
            return null;
        }
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
            socket.close();
        });
    }
}
