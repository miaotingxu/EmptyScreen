package com.haier.logger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class LogFormatter {

    private static final Map<Class<?>, ObjectParser<?>> parsers = new HashMap<>();
    private static ObjectParser<Object> defaultParser = null;

    public static void registerParser(Class<?> clazz, ObjectParser<?> parser) {
        parsers.put(clazz, parser);
    }

    public static void registerDefaultParser(ObjectParser<Object> parser) {
        defaultParser = parser;
    }

    public static String formatJson(String jsonString) {
        if (jsonString == null || jsonString.trim().isEmpty()) {
            return "[Empty JSON]";
        }

        try {
            if (jsonString.trim().startsWith("{")) {
                JSONObject json = new JSONObject(jsonString);
                return json.toString(4);
            } else if (jsonString.trim().startsWith("[")) {
                JSONArray json = new JSONArray(jsonString);
                return json.toString(4);
            } else {
                return "[Invalid JSON] " + jsonString;
            }
        } catch (JSONException e) {
            return "[Invalid JSON] " + jsonString;
        }
    }

    public static String formatXml(String xmlString) {
        if (xmlString == null || xmlString.trim().isEmpty()) {
            return "[Empty XML]";
        }

        try {
            return formatXmlInternal(xmlString);
        } catch (Exception e) {
            return "[Invalid XML] " + xmlString;
        }
    }

    private static String formatXmlInternal(String xml) {
        StringBuilder formatted = new StringBuilder();
        int indent = 0;
        String[] lines = xml.split("(?<=>)");
        
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            
            if (line.startsWith("</")) {
                indent--;
            }
            
            for (int i = 0; i < indent; i++) {
                formatted.append("    ");
            }
            formatted.append(line).append("\n");
            
            if (line.startsWith("<") && !line.startsWith("</") && !line.endsWith("/>")) {
                indent++;
            }
        }
        
        return formatted.toString();
    }

    @SuppressWarnings("unchecked")
    public static String formatObject(Object obj) {
        if (obj == null) {
            return "null";
        }

        Class<?> clazz = obj.getClass();
        
        if (parsers.containsKey(clazz)) {
            ObjectParser parser = parsers.get(clazz);
            return parser.parse(obj);
        }

        if (defaultParser != null) {
            return defaultParser.parse(obj);
        }

        if (obj instanceof String) {
            return (String) obj;
        }

        if (obj instanceof Number || obj instanceof Boolean || obj instanceof Character) {
            return obj.toString();
        }

        if (obj instanceof Collection) {
            return formatCollection((Collection<?>) obj);
        }

        if (obj instanceof Map) {
            return formatMap((Map<?, ?>) obj);
        }

        if (clazz.isArray()) {
            return formatArray(obj);
        }

        return formatObjectFields(obj);
    }

    private static String formatCollection(Collection<?> collection) {
        StringBuilder sb = new StringBuilder();
        sb.append("[\n");
        int index = 0;
        for (Object item : collection) {
            sb.append("  [").append(index++).append("] ").append(formatObject(item)).append("\n");
        }
        sb.append("]");
        return sb.toString();
    }

    private static String formatMap(Map<?, ?> map) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            sb.append("  ").append(entry.getKey()).append(" = ").append(formatObject(entry.getValue())).append("\n");
        }
        sb.append("}");
        return sb.toString();
    }

    private static String formatArray(Object array) {
        int length = Array.getLength(array);
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(formatObject(Array.get(array, i)));
        }
        sb.append("]");
        return sb.toString();
    }

    private static String formatObjectFields(Object obj) {
        StringBuilder sb = new StringBuilder();
        sb.append(obj.getClass().getSimpleName()).append(" {\n");
        
        Field[] fields = obj.getClass().getDeclaredFields();
        for (Field field : fields) {
            field.setAccessible(true);
            try {
                Object value = field.get(obj);
                sb.append("  ").append(field.getName()).append(" = ").append(value).append("\n");
            } catch (IllegalAccessException e) {
                sb.append("  ").append(field.getName()).append(" = <inaccessible>\n");
            }
        }
        
        sb.append("}");
        return sb.toString();
    }

    public static String formatBytes(byte[] bytes) {
        if (bytes == null) return "[null]";
        return formatBytes(bytes, 0, bytes.length);
    }

    public static String formatBytes(byte[] bytes, int offset, int length) {
        if (bytes == null) {
            return "[null]";
        }

        int maxBytes = Math.min(1024, length);
        StringBuilder sb = new StringBuilder();
        sb.append("Byte Array: ").append(length).append(" bytes\n");

        for (int i = 0; i < maxBytes; i += 16) {
            sb.append(String.format("%08X  ", offset + i));

            for (int j = 0; j < 16; j++) {
                if (i + j < maxBytes) {
                    sb.append(String.format("%02X ", bytes[offset + i + j]));
                } else {
                    sb.append("   ");
                }
                if (j == 7) sb.append(" ");
            }

            sb.append(" |");
            for (int j = 0; j < 16 && i + j < maxBytes; j++) {
                byte b = bytes[offset + i + j];
                if (b >= 32 && b < 127) {
                    sb.append((char) b);
                } else {
                    sb.append('.');
                }
            }
            sb.append("|\n");
        }

        if (length > maxBytes) {
            sb.append("... (").append(length - maxBytes).append(" more bytes)");
        }

        return sb.toString();
    }
}
