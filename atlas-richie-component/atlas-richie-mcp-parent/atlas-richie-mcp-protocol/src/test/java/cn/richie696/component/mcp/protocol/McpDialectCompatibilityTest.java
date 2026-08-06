package cn.richie696.component.mcp.protocol;

import cn.richie696.component.mcp.protocol.dialect.Mcp20251125Dialect;
import cn.richie696.component.mcp.protocol.dialect.Mcp20260728Dialect;
import cn.richie696.component.mcp.protocol.model.McpJsonRpcRequest;
import cn.richie696.component.mcp.protocol.model.McpNormalizedRequest;
import cn.richie696.component.mcp.protocol.model.McpNormalizedResult;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpDialectCompatibilityTest {
    private final Mcp20260728Dialect modern = new Mcp20260728Dialect();
    private final Mcp20251125Dialect legacy = new Mcp20251125Dialect();

    @Test
    void modernRequestRequiresCompletePerRequestMetadata() {
        McpJsonRpcRequest request = new McpJsonRpcRequest("2.0", 1, "tools/list", Map.of());

        assertThatThrownBy(() -> modern.normalizeRequest(request, McpProtocolVersions.V_2026_07_28))
                .isInstanceOfSatisfying(McpProtocolException.class,
                        exception -> assertThat(exception.jsonRpcCode()).isEqualTo(-32602));
    }

    @Test
    void modernRequestNormalizesMetadataAndArguments() {
        McpJsonRpcRequest request = new McpJsonRpcRequest("2.0", "r1", "tools/call", Map.of(
                "name", "customer_lookup",
                "arguments", Map.of("customerId", "C-1"),
                "_meta", modernMetadata()));

        McpNormalizedRequest normalized =
                modern.normalizeRequest(request, McpProtocolVersions.V_2026_07_28);

        assertThat(normalized.era()).isEqualTo(McpProtocolEra.STATELESS_2026);
        assertThat(normalized.peer().name()).isEqualTo("test-client");
        assertThat(normalized.arguments()).containsKey("name").doesNotContainKey("_meta");
    }

    @Test
    void modernRequestRejectsHeaderMetadataMismatch() {
        McpJsonRpcRequest request = new McpJsonRpcRequest(
                "2.0", 1, "tools/list", Map.of("_meta", modernMetadata()));

        assertThatThrownBy(() -> modern.normalizeRequest(request, McpProtocolVersions.V_2025_11_25))
                .isInstanceOfSatisfying(McpProtocolException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo("MCP_HEADER_MISMATCH"));
    }

    @Test
    void modernClientInfoIsOptional() {
        McpJsonRpcRequest request = new McpJsonRpcRequest("2.0", 1, "tools/list", Map.of(
                "_meta", Map.of(
                        McpMetaKeys.PROTOCOL_VERSION, McpProtocolVersions.V_2026_07_28,
                        McpMetaKeys.CLIENT_CAPABILITIES, Map.of())));

        McpNormalizedRequest normalized =
                modern.normalizeRequest(request, McpProtocolVersions.V_2026_07_28);

        assertThat(normalized.peer()).isNull();
    }

    @Test
    void modernSupportsInputRequiredResult() {
        McpNormalizedResult normalized = modern.normalizeResult(Map.of(
                "resultType", "input_required",
                "requestState", "opaque"));

        assertThat(normalized.resultType()).isEqualTo(McpNormalizedResult.ResultType.INPUT_REQUIRED);
        assertThat(modern.encodeResult(normalized))
                .containsEntry("resultType", "input_required")
                .containsEntry("requestState", "opaque");
    }

    @Test
    void legacyInitializeNormalizesSessionHandshake() {
        McpJsonRpcRequest request = new McpJsonRpcRequest("2.0", 1, "initialize", Map.of(
                "protocolVersion", McpProtocolVersions.V_2025_11_25,
                "clientInfo", Map.of("name", "legacy-client", "version", "1.0"),
                "capabilities", Map.of("roots", Map.of())));

        McpNormalizedRequest normalized = legacy.normalizeRequest(request, null);

        assertThat(normalized.era()).isEqualTo(McpProtocolEra.SESSION_2025);
        assertThat(normalized.peer().name()).isEqualTo("legacy-client");
        assertThat(normalized.arguments()).isEmpty();
    }

    @Test
    void legacyMissingResultTypeMeansComplete() {
        McpNormalizedResult normalized = legacy.normalizeResult(Map.of("content", "ok"));

        assertThat(normalized.resultType()).isEqualTo(McpNormalizedResult.ResultType.COMPLETE);
        assertThat(legacy.encodeResult(normalized))
                .containsEntry("content", "ok")
                .doesNotContainKey("resultType");
    }

    @Test
    void modernAlwaysEmitsExplicitResultType() {
        McpNormalizedResult result = new McpNormalizedResult(
                McpNormalizedResult.ResultType.COMPLETE,
                Map.of("content", "ok"));

        assertThat(modern.encodeResult(result)).containsEntry("resultType", "complete");
    }

    private Map<String, Object> modernMetadata() {
        return Map.of(
                McpMetaKeys.PROTOCOL_VERSION, McpProtocolVersions.V_2026_07_28,
                McpMetaKeys.CLIENT_INFO, Map.of("name", "test-client", "version", "1.0"),
                McpMetaKeys.CLIENT_CAPABILITIES, Map.of("tools", Map.of()));
    }
}
