package cn.richie696.component.mcp.protocol.discovery;

import cn.richie696.component.mcp.protocol.McpMetaKeys;
import cn.richie696.component.mcp.protocol.McpProtocolException;
import cn.richie696.component.mcp.protocol.McpProtocolVersions;
import cn.richie696.component.mcp.protocol.model.McpImplementationInfo;
import cn.richie696.component.mcp.protocol.model.McpJsonRpcRequest;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpDiscoveryCodecTest {
    private final McpDiscoveryCodec codec = new McpDiscoveryCodec();

    @Test
    void encodesAndDecodesDiscoverRequestWithQualifiedMetadataKeys() {
        McpJsonRpcRequest request = codec.encodeRequest(
                "discover-1",
                new McpImplementationInfo("atlas-client", "1.0.0"),
                Map.of("roots", Map.of()));

        assertThat(request.method()).isEqualTo(McpDiscoveryCodec.METHOD);
        assertThat(request.params()).containsOnlyKeys("_meta");
        assertThat(castMap(request.params().get("_meta")))
                .containsEntry(McpMetaKeys.PROTOCOL_VERSION, McpProtocolVersions.V_2026_07_28)
                .containsKeys(McpMetaKeys.CLIENT_INFO, McpMetaKeys.CLIENT_CAPABILITIES);
        assertThat(codec.decodeRequest(request, McpProtocolVersions.V_2026_07_28).peer().name())
                .isEqualTo("atlas-client");
    }

    @Test
    void rejectsDiscoverNotificationAndAdditionalParams() {
        McpJsonRpcRequest notification = codec.encodeRequest(null, null, Map.of());
        assertThatThrownBy(() -> codec.decodeRequest(notification, null))
                .isInstanceOf(McpProtocolException.class);

        Map<String, Object> params = new LinkedHashMap<>(codec.encodeRequest(1, null, Map.of()).params());
        params.put("unexpected", true);
        McpJsonRpcRequest extra = new McpJsonRpcRequest("2.0", 1, McpDiscoveryCodec.METHOD, params);
        assertThatThrownBy(() -> codec.decodeRequest(extra, null))
                .isInstanceOf(McpProtocolException.class);
    }

    @Test
    void roundTripsCompleteDiscoverResultAndExtensionFields() {
        McpDiscoverResult source = new McpDiscoverResult(
                List.of(McpProtocolVersions.V_2026_07_28, McpProtocolVersions.V_2025_11_25),
                Map.of("tools", Map.of(), "resources", Map.of()),
                new McpImplementationInfo(
                        "atlas-server",
                        "1.0.0",
                        "Atlas MCP",
                        "Enterprise MCP adapter",
                        "https://example.com/mcp",
                        List.of(Map.of(
                                "src", "https://example.com/icon.png",
                                "mimeType", "image/png"))),
                "Enterprise MCP server",
                3_600_000,
                McpCacheScope.PUBLIC,
                Map.of("com.example/build", "42"));

        Map<String, Object> wire = codec.encodeResult(source);
        McpDiscoverResult decoded = codec.decodeResult(wire);

        assertThat(wire).containsEntry("resultType", "complete");
        assertThat(decoded).isEqualTo(source);
        assertThat(decoded.extensions()).containsEntry("com.example/build", "42");
    }

    @Test
    void serverInfoIsOptionalButCacheFieldsAreRequired() {
        McpDiscoverResult decoded = codec.decodeResult(Map.of(
                "resultType", "complete",
                "supportedVersions", List.of(McpProtocolVersions.V_2026_07_28),
                "capabilities", Map.of(),
                "ttlMs", 0,
                "cacheScope", "private"));

        assertThat(decoded.serverInfo()).isNull();
        assertThat(decoded.cacheScope()).isEqualTo(McpCacheScope.PRIVATE);

        assertThatThrownBy(() -> codec.decodeResult(Map.of(
                "resultType", "complete",
                "supportedVersions", List.of(McpProtocolVersions.V_2026_07_28),
                "capabilities", Map.of(),
                "cacheScope", "private")))
                .isInstanceOfSatisfying(McpProtocolException.class,
                        exception -> assertThat(exception.jsonRpcCode()).isEqualTo(-32602));
    }

    @Test
    void rejectsInputRequiredDiscoverResult() {
        assertThatThrownBy(() -> codec.decodeResult(Map.of(
                "resultType", "input_required",
                "requestState", "opaque")))
                .isInstanceOf(McpProtocolException.class);
    }

    @Test
    void rejectsFractionalTtlBecauseOfficialSchemaRequiresInteger() {
        assertThatThrownBy(() -> codec.decodeResult(Map.of(
                "resultType", "complete",
                "supportedVersions", List.of(McpProtocolVersions.V_2026_07_28),
                "capabilities", Map.of(),
                "ttlMs", 1.5,
                "cacheScope", "public")))
                .isInstanceOf(McpProtocolException.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }
}
