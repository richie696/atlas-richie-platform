package cn.richie696.component.mcp.api.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Protocol-neutral resource template descriptor. */
public record McpResourceTemplateDescriptor(
        String uriTemplate,
        String name,
        String title,
        String description,
        String mimeType,
        List<Map<String, Object>> icons,
        Map<String, Object> annotations) {

    public McpResourceTemplateDescriptor {
        uriTemplate = Objects.requireNonNull(uriTemplate, "uriTemplate");
        name = Objects.requireNonNull(name, "name");
        icons = icons == null ? List.of() : List.copyOf(icons);
        annotations = annotations == null ? Map.of() : Map.copyOf(annotations);
    }
}
