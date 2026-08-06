package cn.richie696.component.mcp.api.model;

import java.util.Map;
import java.util.List;
import java.util.Objects;

public record McpResourceDescriptor(
        String uri,
        String name,
        String title,
        String description,
        String mimeType,
        Long size,
        List<Map<String, Object>> icons,
        Map<String, Object> annotations) {

    public McpResourceDescriptor(
            String uri,
            String name,
            String title,
            String description,
            String mimeType,
            Map<String, Object> annotations) {
        this(uri, name, title, description, mimeType, null, List.of(), annotations);
    }

    public McpResourceDescriptor {
        uri = Objects.requireNonNull(uri, "uri");
        name = Objects.requireNonNull(name, "name");
        if (size != null && size < 0) {
            throw new IllegalArgumentException("size must be non-negative");
        }
        icons = icons == null ? List.of() : List.copyOf(icons);
        annotations = annotations == null ? Map.of() : Map.copyOf(annotations);
    }
}
