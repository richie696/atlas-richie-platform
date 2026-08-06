package cn.richie696.component.mcp.protocol.model;

import cn.richie696.component.mcp.protocol.McpProtocolEra;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 两个协议时代汇合后的内部请求。
 */
public record McpNormalizedRequest(
        Object id,
        String method,
        Map<String, Object> arguments,
        String protocolVersion,
        McpProtocolEra era,
        McpImplementationInfo peer,
        Map<String, Object> capabilities,
        Map<String, Object> metadata) {

    public McpNormalizedRequest {
        method = Objects.requireNonNull(method, "method");
        arguments = arguments == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(arguments));
        protocolVersion = Objects.requireNonNull(protocolVersion, "protocolVersion");
        era = Objects.requireNonNull(era, "era");
        capabilities = capabilities == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(capabilities));
        metadata = metadata == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }

    public boolean notification() {
        return id == null;
    }
}
