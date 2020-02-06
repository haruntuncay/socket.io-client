package engineio_client;

import org.junit.Test;

import static org.junit.Assert.*;

public class ConfigCloneTest {

    @Test
    public void testConfigClose() throws CloneNotSupportedException {
        Config conf = new Config();
        Config cpyConf = conf.clone();

        // Override non-primitives for conf.
        conf.transports[0] = "random";
        conf.queryMap.put("which", "original");
        conf.headerMap.put("which", "original");

        assertNotEquals(conf.transports[0], cpyConf.transports[0]);
        assertNotEquals(conf.queryMap, cpyConf.queryMap);
        assertNotEquals(conf.headerMap, cpyConf.headerMap);
        assertNotEquals(conf.callFactory, cpyConf.callFactory);
        assertNotEquals(conf.webSocketFactory, cpyConf.webSocketFactory);
    }
}
