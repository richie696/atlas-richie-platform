package cn.richie696.component.mcp.transport.http;

import java.util.Set;

public record McpSubscriptionSpec(
        boolean toolsListChanged,
        boolean promptsListChanged,
        boolean resourcesListChanged,
        Set<String> resourceSubscriptions) {
    public McpSubscriptionSpec {
        resourceSubscriptions = resourceSubscriptions == null ? Set.of() : Set.copyOf(resourceSubscriptions);
    }
}
