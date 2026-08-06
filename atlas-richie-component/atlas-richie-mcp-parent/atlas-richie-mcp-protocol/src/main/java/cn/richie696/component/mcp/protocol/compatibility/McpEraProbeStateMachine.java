package cn.richie696.component.mcp.protocol.compatibility;

import cn.richie696.component.mcp.protocol.McpProtocolEra;
import cn.richie696.component.mcp.protocol.McpProtocolException;
import cn.richie696.component.mcp.protocol.McpProtocolNegotiator;
import cn.richie696.component.mcp.protocol.McpProtocolVersions;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 按 transport binding 执行 modern/legacy era 判定。
 */
public final class McpEraProbeStateMachine {
    private static final int METHOD_NOT_FOUND = -32601;
    private static final int HEADER_MISMATCH = -32020;
    private static final int MISSING_REQUIRED_CLIENT_CAPABILITY = -32021;
    private static final int UNSUPPORTED_PROTOCOL_VERSION = -32022;
    private static final Set<Integer> MCP_MODERN_ERRORS = Set.of(
            HEADER_MISMATCH,
            MISSING_REQUIRED_CLIENT_CAPABILITY,
            UNSUPPORTED_PROTOCOL_VERSION);

    private final McpProtocolNegotiator negotiator;

    public McpEraProbeStateMachine() {
        this(new McpProtocolNegotiator());
    }

    public McpEraProbeStateMachine(McpProtocolNegotiator negotiator) {
        this.negotiator = Objects.requireNonNull(negotiator, "negotiator");
    }

    public McpProbeDecision evaluate(
            McpTransportBinding binding,
            String requestedVersion,
            McpProbeEvent event) {
        Objects.requireNonNull(binding, "binding");
        Objects.requireNonNull(requestedVersion, "requestedVersion");
        Objects.requireNonNull(event, "event");

        return switch (event.type()) {
            case DISCOVER_RESULT -> selectAdvertised(event.advertisedVersions(), false);
            case MODERN_SUCCESS -> modern(requestedVersion, McpProbeDecision.Action.USE_MODERN);
            case JSON_RPC_ERROR -> jsonRpcError(binding, requestedVersion, event);
            case TRANSPORT_ERROR -> transportFailure(binding, event.httpStatus());
            case TIMEOUT -> timeout(binding);
        };
    }

    private McpProbeDecision jsonRpcError(
            McpTransportBinding binding,
            String requestedVersion,
            McpProbeEvent event) {
        int code = Objects.requireNonNull(event.jsonRpcErrorCode(), "jsonRpcErrorCode");
        if (code == UNSUPPORTED_PROTOCOL_VERSION) {
            return selectAdvertised(event.advertisedVersions(), true);
        }
        if (MCP_MODERN_ERRORS.contains(code)
                || binding == McpTransportBinding.STREAMABLE_HTTP
                && Integer.valueOf(404).equals(event.httpStatus())
                && code == METHOD_NOT_FOUND) {
            return modern(requestedVersion, McpProbeDecision.Action.USE_MODERN);
        }
        return binding == McpTransportBinding.STDIO
                ? legacy()
                : httpUnrecognized(event.httpStatus());
    }

    private McpProbeDecision transportFailure(McpTransportBinding binding, Integer httpStatus) {
        if (binding == McpTransportBinding.STDIO) {
            return legacy();
        }
        return httpUnrecognized(httpStatus);
    }

    private McpProbeDecision timeout(McpTransportBinding binding) {
        return binding == McpTransportBinding.STDIO
                ? legacy()
                : new McpProbeDecision(null, McpProbeDecision.Action.RETRY_PROBE, null);
    }

    private McpProbeDecision httpUnrecognized(Integer httpStatus) {
        if (httpStatus != null && httpStatus >= 400 && httpStatus < 500) {
            return legacy();
        }
        return new McpProbeDecision(null, McpProbeDecision.Action.RETRY_PROBE, null);
    }

    private McpProbeDecision selectAdvertised(List<String> advertisedVersions, boolean retry) {
        if (advertisedVersions == null || advertisedVersions.isEmpty()) {
            throw new McpProtocolException(
                    "MCP_INVALID_PROBE_RESPONSE",
                    -32602,
                    "Modern probe response did not advertise supported protocol versions",
                    Map.of());
        }
        try {
            String selected = negotiator.negotiate(advertisedVersions);
            if (McpProtocolVersions.V_2025_11_25.equals(selected)) {
                return new McpProbeDecision(
                        McpProtocolEra.STATELESS_2026,
                        McpProbeDecision.Action.FAIL_INCOMPATIBLE,
                        null);
            }
            return modern(
                    selected,
                    retry ? McpProbeDecision.Action.RETRY_MODERN : McpProbeDecision.Action.USE_MODERN);
        } catch (McpProtocolException exception) {
            return new McpProbeDecision(
                    McpProtocolEra.STATELESS_2026,
                    McpProbeDecision.Action.FAIL_INCOMPATIBLE,
                    null);
        }
    }

    private McpProbeDecision modern(String version, McpProbeDecision.Action action) {
        return new McpProbeDecision(McpProtocolEra.STATELESS_2026, action, version);
    }

    private McpProbeDecision legacy() {
        return new McpProbeDecision(
                McpProtocolEra.SESSION_2025,
                McpProbeDecision.Action.INITIALIZE_LEGACY,
                null);
    }
}
