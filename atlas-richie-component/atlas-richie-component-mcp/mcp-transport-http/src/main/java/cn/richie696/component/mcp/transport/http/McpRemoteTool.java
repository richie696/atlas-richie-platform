package cn.richie696.component.mcp.transport.http;

import java.util.Map;

/** Tool metadata returned by a remote MCP server. */
public record McpRemoteTool(
        String name,
        String title,
        String description,
        Map<String, Object> inputSchema,
        Map<String, Object> outputSchema,
        Map<String, Object> annotations) {
    public McpRemoteTool {
        inputSchema = inputSchema == null ? Map.of() : Map.copyOf(inputSchema);
        outputSchema = outputSchema == null ? Map.of() : Map.copyOf(outputSchema);
        annotations = annotations == null ? Map.of() : Map.copyOf(annotations);
    }
}
