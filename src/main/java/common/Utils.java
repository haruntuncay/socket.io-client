package common;

import engineio_client.transports.Transport;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static java.util.stream.Collectors.joining;

/**
 * Static utility methods that are used through out the entire API.
 */
public class Utils {

    /**
     * Given a {@code Map<String, String>} builds a query string from its entries.
     *
     * @param queryMap The {@code Map<String, String>} instance that contains key->value pairs.
     * @return The query string that will be appended to the URL when {@link Transport#open} is called.
     */
    public static String getQueryStringFromMap(Map<String, String> queryMap) {
       if(queryMap == null)
           return "";

       return queryMap.entrySet()
                        .stream()
                        .map(entry -> encodeQueryString(entry.getKey()) + "=" + encodeQueryString(entry.getValue()))
                        .collect(joining("&"));
    }

    static String encodeQueryString(String str) {
       try {
           return URLEncoder.encode(str, StandardCharsets.UTF_8.name())
                   .replace("+", "%20")
                   .replace("%21", "!")
                   .replace("%27", "'")
                   .replace("%28", "(")
                   .replace("%29", ")")
                   .replace("%7E", "~");
       } catch (UnsupportedEncodingException e) {
           return str;
       }
    }

    /**
     *  By default, path portion of the given URI is treated as the namespace, and actual path becomes "/engine.io".
     *  For example, "https://abc.com/admin" actually connects to "https://abc.com/engine.io" with "/admin" as the namespace.
     *  Given a URI and an explicit path, get a connection path.
     */
    public static String getConnectionPath(URL url, String path) {
        return url.getHost()
                + (url.getPort() != -1 ? ":" + url.getPort() : "")
                + (path != null ? path : "/");
    }

    /**
     * Figure out whether the {@link socketio_client.parser.Packet} contains any binary data (in form of {@code byte[]}) or not.
     *
     * @param data Entire data array to check for any binary data (in form of {@code byte[]}).
     * @return true if there is any binary data is found.
     */
    public static boolean containsBinaryData(Object[] data) {
        return isBinaryData(data);
    }

    private static boolean isBinaryData(Object data) {
        if(data == null)
            return false;
        if(data instanceof byte[])
            return true;
        if(data instanceof Object[]) {
            for (Object obj : (Object[]) data)
                if (isBinaryData(obj))
                    return true;
            return false;
        }
        if(data instanceof JSONArray) {
            JSONArray jsonArray = (JSONArray) data;
            for(Object obj : jsonArray)
                if(isBinaryData(obj))
                    return true;
            return false;
        }
        if(data instanceof JSONObject) {
            JSONObject json = (JSONObject) data;
            for(String key : json.keySet())
                if(isBinaryData(json.get(key)))
                    return true;
            return false;
        }
        return false;
    }

    /**
     * Unless the data is {@code byte[]}, socketio_client transports always communicate using a {@code JSONArray}.
     * This methods turns given {@code JSONArray} into {@code Object[]} so that it can be passed as argument
     *  where {@code Object... args} is expected.
     *
     * @param jsonArray JSONArray that will be transformed into Object[].
     * @return Object[] that contains elements items of JSONArray.
     */
    public static Object[] jsonArrayToObjectArray(JSONArray jsonArray) {
        if(jsonArray == null)
            return null;
        return jsonArray.toList().toArray(new Object[]{});
    }

}
