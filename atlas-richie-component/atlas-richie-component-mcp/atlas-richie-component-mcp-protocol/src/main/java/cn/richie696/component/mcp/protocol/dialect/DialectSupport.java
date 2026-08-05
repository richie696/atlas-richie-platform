package cn.richie696.component.mcp.protocol.dialect;

import cn.richie696.component.mcp.protocol.McpProtocolException;
import cn.richie696.component.mcp.protocol.model.McpImplementationInfo;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

final class DialectSupport {
    private DialectSupport() {
    }

    static Map<String, Object> object(Map<String, Object> source, String key, boolean required) {
        Object value = source.get(key);
        if (value == null && !required) {
            return Map.of();
        }
        if (!(value instanceof Map<?, ?> raw)) {
            throw invalidParams(key + " must be an object");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((entryKey, entryValue) -> {
            if (!(entryKey instanceof String stringKey)) {
                throw invalidParams(key + " contains a non-string key");
            }
            result.put(stringKey, entryValue);
        });
        return Collections.unmodifiableMap(result);
    }

    static String string(Map<String, Object> source, String key, boolean required) {
        Object value = source.get(key);
        if (value == null && !required) {
            return null;
        }
        if (!(value instanceof String stringValue) || stringValue.isBlank()) {
            throw invalidParams(key + " must be a non-blank string");
        }
        return stringValue;
    }

    static McpImplementationInfo implementation(Map<String, Object> source, String key, boolean required) {
        Map<String, Object> value = object(source, key, required);
        if (value.isEmpty() && !required) {
            return null;
        }
        try {
            return McpImplementationInfo.fromWire(value);
        } catch (IllegalArgumentException exception) {
            throw invalidParams(key + ": " + exception.getMessage());
        }
    }

    static McpProtocolException invalidParams(String message) {
        return new McpProtocolException("MCP_INVALID_PARAMS", -32602, message, Map.of());
    }
}
