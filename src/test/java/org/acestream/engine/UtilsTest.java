package org.acestream.engine;

import org.acestream.sdk.utils.MiscUtils;
import org.junit.Test;

import java.util.Map;
import static org.junit.Assert.*;

public class UtilsTest {
    @Test
    public void test_getQueryParameters() throws Exception {
        Map<String,String> result;
        MiscUtils.getQueryParameters("http://test.mp4");

        result = MiscUtils.getQueryParameters("acestream:?content_id=test&index=");
        assertEquals(2, result.size());
        assertTrue(result.containsKey("content_id"));
        assertEquals("test", result.get("content_id"));
        assertTrue(result.containsKey("index"));
        assertEquals("", result.get("index"));

        result = MiscUtils.getQueryParameters("acestream:?content_id=test&index&");
        assertEquals(2, result.size());
        assertTrue(result.containsKey("content_id"));
        assertEquals("test", result.get("content_id"));
        assertTrue(result.containsKey("index"));
        assertEquals("", result.get("index"));

        result = MiscUtils.getQueryParameters("acestream:?content_id=test&index&=");
        assertEquals(2, result.size());
        assertTrue(result.containsKey("content_id"));
        assertEquals("test", result.get("content_id"));
        assertTrue(result.containsKey("index"));
        assertEquals("", result.get("index"));

        result = MiscUtils.getQueryParameters("acestream:?content_id=test% aaa&index&=");
        assertEquals(2, result.size());
        assertTrue(result.containsKey("content_id"));
        assertEquals("test% aaa", result.get("content_id"));
        assertTrue(result.containsKey("index"));
        assertEquals("", result.get("index"));
    }
}