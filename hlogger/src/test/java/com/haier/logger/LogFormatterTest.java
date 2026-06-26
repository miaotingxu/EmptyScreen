package com.haier.logger;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class LogFormatterTest {

    @Test
    public void formatBytes_basic() {
        byte[] data = "Hello World!".getBytes();
        String result = LogFormatter.formatBytes(data);
        assertTrue(result.contains("Byte Array: 12 bytes"));
        assertTrue(result.contains("48 65 6C 6C 6F"));
        assertTrue(result.contains("|Hello World!|"));
    }

    @Test
    public void formatBytes_withOffset() {
        byte[] data = "Hello World!".getBytes();
        String result = LogFormatter.formatBytes(data, 6, 6);
        assertTrue(result.contains("Byte Array: 6 bytes"));
        assertTrue(result.contains("|World!|"));
    }

    @Test
    public void formatBytes_null() {
        String result = LogFormatter.formatBytes(null);
        assertEquals("[null]", result);
    }

    @Test
    public void formatObject_null() {
        assertEquals("null", LogFormatter.formatObject(null));
    }

    @Test
    public void formatObject_string() {
        assertEquals("hello", LogFormatter.formatObject("hello"));
    }

    @Test
    public void formatObject_number() {
        assertEquals("42", LogFormatter.formatObject(42));
    }

    @Test
    public void formatObject_array() {
        int[] arr = {1, 2, 3};
        String result = LogFormatter.formatObject(arr);
        assertEquals("[1, 2, 3]", result);
    }

    @Test
    public void formatObject_collection() {
        String result = LogFormatter.formatObject(Arrays.asList("a", "b", "c"));
        assertTrue(result.contains("[0] a"));
        assertTrue(result.contains("[1] b"));
        assertTrue(result.contains("[2] c"));
    }

    @Test
    public void formatObject_map() {
        Map<String, Integer> map = new HashMap<>();
        map.put("key", 123);
        String result = LogFormatter.formatObject(map);
        assertTrue(result.contains("key = 123"));
    }

    @Test
    public void formatObject_customParser() {
        LogFormatter.registerParser(StringBuilder.class, sb -> "Custom: " + sb.toString());
        String result = LogFormatter.formatObject(new StringBuilder("test"));
        assertEquals("Custom: test", result);
    }

    @Test
    public void formatXml_valid() {
        String xml = "<root><child>text</child></root>";
        String result = LogFormatter.formatXml(xml);
        assertTrue(result.contains("<root>"));
        assertTrue(result.contains("<child>"));
    }

    @Test
    public void formatXml_empty() {
        assertEquals("[Empty XML]", LogFormatter.formatXml(""));
        assertEquals("[Empty XML]", LogFormatter.formatXml(null));
    }
}
