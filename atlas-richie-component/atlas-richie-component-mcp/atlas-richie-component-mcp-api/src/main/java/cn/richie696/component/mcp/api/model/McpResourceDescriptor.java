package cn.richie696.component.mcp.api.model;

import java.util.Map;
import java.util.Objects;

public record McpResourceDescriptor(
        String uri,
        String name,
        String title,
        String description,
        String mimeType,
        Map<String, Object> annotations) {

    public McpResourceDescriptor {
        uri = Objects.requireNonNull(uri, "uri");
        name = Objects.requireNonNull(name, "name");
        annotations = annotations == null ? Map.of() : Map.copyOf(annotations);
    }
}
