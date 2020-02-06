package common;

import org.json.JSONArray;
import org.junit.Test;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static common.Utils.*;
import static org.junit.Assert.*;

public class UtilsTest {

    @Test
    public void testGetQueryStringFromMap() {
        Map<String, String> queryMap = new LinkedHashMap<String, String>() {{
            put("key1", "+value1");
            put("key2", "=value2");
            put("key 3", "value 3");
        }};

        assertEquals("key1=%2Bvalue1&key2=%3Dvalue2&key%203=value%203", getQueryStringFromMap(queryMap));
    }

    @Test
    public void testEncodeQueryString() {
        assertEquals("key%20with%20spaces", encodeQueryString("key with spaces"));
        assertEquals("SpecialChars%20!'()~", encodeQueryString("SpecialChars !'()~"));
    }

    @Test
    public void testGetConnectionPath() throws MalformedURLException {
        URL url = new URL("http://localhost:3001/namespace");
        assertEquals("localhost:3001/nsp", getConnectionPath(url, "/nsp"));
        assertEquals("localhost:3001/", getConnectionPath(url, null));
    }

    @Test
    public void testContainsBinaryData() {
        Object[] binData = {1, 2, "", new Object(), new byte[]{1}};
        assertTrue(containsBinaryData(binData));

        Object[] nestedBinData = {1, 2, 3, "", new Object[]{new byte[]{1}}};
        assertTrue(containsBinaryData(nestedBinData));

        Object[] deepNestedBinData = {new Object[]{new Object[]{new Object[]{new byte[]{1}}}}};        assertTrue(containsBinaryData(nestedBinData));
        assertTrue(containsBinaryData(nestedBinData));
    }

    @Test
    public void testJsonArrayToObjectArray() {
        Object obj = new Object();
        String str = "";
        byte[] byteArr = {1,2,3};
        Object[] objArr = {1, 2, 3, "", true};

        JSONArray jsonArray = new JSONArray()
                .put(obj)
                .put(str)
                .put(byteArr)
                .put(objArr);
        Object[] array = {obj, str, byteArr, objArr};

        assertArrayEquals(jsonArrayToObjectArray(jsonArray), array);
    }
}
