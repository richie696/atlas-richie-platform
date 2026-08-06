package cn.richie696.component.mcp.api;

import java.util.Map;
import java.util.concurrent.CompletionStage;

/** Client-side MRTR input collection hook. */
@FunctionalInterface
public interface McpInputProvider {
    CompletionStage<Map<String, Object>> collect(Map<String, Object> inputRequests);
}
