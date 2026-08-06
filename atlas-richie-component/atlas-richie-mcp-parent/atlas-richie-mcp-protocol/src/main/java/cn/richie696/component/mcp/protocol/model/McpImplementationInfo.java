package cn.richie696.component.mcp.protocol.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record McpImplementationInfo(
        String name,
        String version,
        String title,
        String description,
        String websiteUrl,
        List<Map<String, Object>> icons) {

    public McpImplementationInfo(String name, String version) {
        this(name, version, null, null, null, List.of());
    }

    public McpImplementationInfo {
        name = Objects.requireNonNull(name, "name");
        version = Objects.requireNonNull(version, "version");
        icons = immutableIcons(icons);
    }

    public static McpImplementationInfo fromWire(Map<String, Object> source) {
        return new McpImplementationInfo(
                requiredString(source.get("name"), "name"),
                requiredString(source.get("version"), "version"),
                optionalString(source.get("title"), "title"),
                optionalString(source.get("description"), "description"),
                optionalString(source.get("websiteUrl"), "websiteUrl"),
                icons(source.get("icons")));
    }

    public Map<String, Object> toWire() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", name);
        result.put("version", version);
        putIfPresent(result, "title", title);
        putIfPresent(result, "description", description);
        putIfPresent(result, "websiteUrl", websiteUrl);
        if (!icons.isEmpty()) {
            result.put("icons", icons);
        }
        return Collections.unmodifiableMap(result);
    }

    private static List<Map<String, Object>> icons(Object value) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> raw)) {
            throw new IllegalArgumentException("icons must be an array");
        }
        List<Map<String, Object>> result = new ArrayList<>(raw.size());
        for (Object entry : raw) {
            if (!(entry instanceof Map<?, ?> icon)) {
                throw new IllegalArgumentException("icons[] must be an object");
            }
            Map<String, Object> typed = new LinkedHashMap<>();
            icon.forEach((key, entryValue) -> {
                if (!(key instanceof String stringKey)) {
                    throw new IllegalArgumentException("icons[] contains a non-string key");
                }
                typed.put(stringKey, entryValue);
            });
            result.add(Collections.unmodifiableMap(typed));
        }
        return List.copyOf(result);
    }

    private static List<Map<String, Object>> immutableIcons(List<Map<String, Object>> icons) {
        return icons == null ? List.of() : icons(icons);
    }

    private static String requiredString(Object value, String field) {
        if (!(value instanceof String string) || string.isBlank()) {
            throw new IllegalArgumentException(field + " must be a non-blank string");
        }
        return string;
    }

    private static String optionalString(Object value, String field) {
        return value == null ? null : requiredString(value, field);
    }

    private static void putIfPresent(Map<String, Object> target, String key, String value) {
        if (value != null) {
            target.put(key, value);
        }
    }
}
