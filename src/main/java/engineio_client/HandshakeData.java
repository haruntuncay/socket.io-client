package engineio_client;

import exceptions.EngineIOException;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Arrays;

/**
 * Represents the handshake options given by the server. Options are comprised of:
 * <p> String sessionId, id given to this engine.io server.
 * <p> String[] upgrades, which transports does the server supports over PollingTransport. (WebSocket)
 * <p> int pingInterval, at which intervals (milliseconds) does the server expects a ping packet from us.
 * <p> int pingTimeout, after what milliseconds do we consider the server disconnected if we don't receive a PONG packet after sending a PING.
 */
public class HandshakeData {

    private String sessionId;
    private String[] upgrades;
    private int pingInterval;
    private int pingTimeout;

    private HandshakeData(String sessionId, String[] upgrades, int pingInterval, int pingTimeout) {
        this.sessionId = sessionId;
        this.upgrades = upgrades;
        this.pingInterval = pingInterval;
        this.pingTimeout = pingTimeout;
    }

    public static HandshakeData parseHandshake(String data) {
        try {
            JSONObject json = new JSONObject(data);

            String sid = json.getString("sid");
            int pingInterval = json.getInt("pingInterval");
            int pingTimeout = json.getInt("pingTimeout");

            JSONArray upgradesArray = json.getJSONArray("upgrades");
            String[] upgrades = new String[upgradesArray.length()];
            for(int i = 0; i < upgradesArray.length(); i++)
                upgrades[i] = (String) upgradesArray.get(i);

            return new HandshakeData(sid, upgrades, pingInterval, pingTimeout);
        } catch(Exception e) {
            throw new EngineIOException("Error while parsing handshake data=" + data, e);
        }
    }

    public String getSessionId() {
        return sessionId;
    }

    public String[] getUpgrades() {
        return upgrades;
    }

    public int getPingInterval() {
        return pingInterval;
    }

    public int getPingTimeout() {
        return pingTimeout;
    }

    @Override
    public String toString() {
        return "HandshakeData{" +
                "sessionId='" + sessionId + '\'' +
                ", upgrades=" + Arrays.toString(upgrades) +
                ", pingInterval=" + pingInterval +
                ", pingTimeout=" + pingTimeout +
                '}';
    }
}
