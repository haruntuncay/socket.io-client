package socketio_client;

import org.junit.Test;

import static org.junit.Assert.*;

public class IOTest {

    private String url = "http://localhost/";

    @Test
    public void testCloneClientConfigBehavior() {
        // When IO builder (the "of" method) is given a config, it must use a clone of it, so that different IO instances
        //  can share the same common config settings and can change any others without affecting each other.
        ClientConfig conf = new ClientConfig();
        conf.reconnect = false; // common setting.

        IO io = IO.of(url,  conf);
        IO io2 = IO.of(url, conf);

        io.noMultiplex();
        io2.path("/custom_path");

        assertEquals(io.config().reconnect, io2.config().reconnect);
        assertNotEquals(io.config().multiplex, io2.config().multiplex);
        assertNotEquals(io.config().path, io2.config().path);
    }

    @Test
    public void testMultiplex() {
        IO.managers.clear();
        String url = "http://localhost/";
        // Connections to same path but different namespaces shares a manager, if multiplex is on. (it's on by default)
        Manager manager = IO.of(url).socket().getManager();
        Manager manager2 = IO.of(url + "nsp").socket().getManager();
        assertEquals(1, IO.managers.size());
        assertEquals(manager, manager2);
    }

    @Test
    public void testNoMultiplex() {
        IO.managers.clear();
        String url = "http://localhost/";
        // noMultiplex method causes IO to create a new manager instance and omit saving it.
        Manager manager = IO.of(url).noMultiplex().socket().getManager();
        Manager manager2 = IO.of(url + "nsp").noMultiplex().socket().getManager();
        assertEquals(0, IO.managers.size());
        assertNotEquals(manager, manager2);
    }

    @Test
    public void testPath() {
        // ClientConfig.path must conform to the form "/path_name/";
        IO io = IO.of(url).path("path");
        assertEquals("/path/", io.config().path);
        io.path("/other_path");
        assertEquals("/other_path/", io.config().path);
        io.path("last_path/");
        assertEquals("/last_path/", io.config().path);
        io.path("/correct/");
        assertEquals("/correct/", io.config().path);
    }

    @Test
    public void testCreateSocket() {
        Socket socket = IO.of(url).socket();
        // This socket should be multiplexed.
        assertEquals(1, IO.managers.size());

        Socket socket2 = IO.of(url + "namespace").socket();
        assertEquals(1, IO.managers.size());
        assertEquals(socket.getManager(), socket2.getManager());
        assertEquals(2, socket.getManager().sockets.size());

        // This socket should cause a new manager to be created
        //  because the connection path is different than any other existing ones.
        Socket socket3 = IO.of(url).path("/different_path").socket();
        assertNotEquals(socket3.getManager(), socket2.getManager());
        assertEquals(2, IO.managers.size());

        // This IO doesn't use multiplex, so even if it was possible to do so, IO will just create a separate manager
        //  and won't keep track of it.
        Socket socket4 = IO.of(url + "multiplexable_namespace").noMultiplex().socket();
        assertEquals(2, IO.managers.size());
        assertFalse(IO.managers.values().contains(socket4.getManager()));
    }
}
