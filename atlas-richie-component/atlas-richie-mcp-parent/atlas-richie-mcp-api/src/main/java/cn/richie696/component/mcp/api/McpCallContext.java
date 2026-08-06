package cn.richie696.component.mcp.api;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Server 与 Client 共用的稳定调用上下文。
 */
public record McpCallContext(
        String requestId,
        String protocolVersion,
        String tenantId,
        String subject,
        Instant deadline,
        Map<String, Object> attributes,
        McpCancellationToken cancellationToken,
        McpProgressReporter progressReporter) {

    public McpCallContext {
        protocolVersion = Objects.requireNonNull(protocolVersion, "protocolVersion");
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        cancellationToken = cancellationToken == null ? McpCancellationToken.NONE : cancellationToken;
        progressReporter = progressReporter == null ? McpProgressReporter.NOOP : progressReporter;
    }

    public Optional<Instant> optionalDeadline() {
        return Optional.ofNullable(deadline);
    }
}
