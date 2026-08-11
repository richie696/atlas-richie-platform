package cn.richie696.component.mcp.protocol.discovery;

import cn.richie696.component.mcp.protocol.McpMetaKeys;
import cn.richie696.component.mcp.protocol.McpProtocolException;
import cn.richie696.component.mcp.protocol.McpProtocolVersions;
import cn.richie696.component.mcp.protocol.model.McpImplementationInfo;
import cn.richie696.component.mcp.protocol.model.McpJsonRpcRequest;
import cn.richie696.component.mcp.protocol.model.McpNormalizedRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 {@link McpDiscoveryCodec} 对 MCP 2026-07-28 discover 请求/响应的完整编解码契约：
 * <ul>
 *   <li>请求侧要求参数严格限定为 {@code _meta}，并自动写入带命名空间前缀的协议版本 / 客户端
 *       信息 / 客户端能力等元数据；</li>
 *   <li>响应侧覆盖 {@code complete} / {@code input_required} 两种 result 类型、扩展字段、
 *       必填缓存字段（{@code ttlMs} / {@code cacheScope}）以及非法 TTL 浮点值的拒绝路径；</li>
 *   <li>解码侧覆盖方法名校验、通知拒绝、缺字段、非法字面量等异常路径。</li>
 * </ul>
 * 该测试确保 discovery 入口在协议升级或扩展字段引入时仍能保持兼容。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpDiscoveryCodec discover 请求/响应编解码")
class McpDiscoveryCodecTest {

    private final McpDiscoveryCodec codec = new McpDiscoveryCodec();

    @Test
    @DisplayName("encodeRequest：仅写 _meta，自动注入协议版本/客户端能力；clientInfo 可选")
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
    @DisplayName("encodeRequest：clientInfo 为 null 时不写 CLIENT_INFO")
    void encodesRequestWithoutClientInfo() {
        McpJsonRpcRequest request = codec.encodeRequest("d-2", null, Map.of());

        assertThat(castMap(request.params().get("_meta")))
                .doesNotContainKey(McpMetaKeys.CLIENT_INFO)
                .containsKey(McpMetaKeys.CLIENT_CAPABILITIES);
    }

    @Test
    @DisplayName("decodeRequest：notification 与额外参数都被拒绝")
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
    @DisplayName("decodeRequest：方法名不匹配时拒绝")
    void rejectsRequestWithWrongMethod() {
        McpJsonRpcRequest request = codec.encodeRequest(1, null, Map.of());
        McpJsonRpcRequest wrongMethod = new McpJsonRpcRequest(
                request.jsonrpc(), request.id(), "tools/list", request.params());

        assertThatThrownBy(() -> codec.decodeRequest(wrongMethod, null))
                .isInstanceOfSatisfying(McpProtocolException.class,
                        exception -> assertThat(exception.jsonRpcCode()).isEqualTo(-32602));
    }

    @Test
    @DisplayName("encodeResult → decodeResult 完整 round-trip，扩展字段透传")
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
        assertThat(wire.get("ttlMs")).isInstanceOf(java.math.BigDecimal.class);
        assertThat(decoded).isEqualTo(source);
        assertThat(decoded.extensions()).containsEntry("com.example/build", "42");
    }

    @Test
    @DisplayName("encodeResult：serverInfo / instructions 为 null 时不写入 _meta / instructions 字段")
    void encodeResultOmitsAbsentOptionals() {
        McpDiscoverResult source = new McpDiscoverResult(
                List.of(McpProtocolVersions.V_2026_07_28),
                Map.of(),
                null, null,
                0L, McpCacheScope.PUBLIC, Map.of());

        Map<String, Object> wire = codec.encodeResult(source);

        assertThat(wire).doesNotContainKey("_meta").doesNotContainKey("instructions");
    }

    @Test
    @DisplayName("decodeResult：serverInfo 可选但 cacheFields 必填")
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
    @DisplayName("decodeResult：input_required 直接拒绝（discover 必须返回 complete）")
    void rejectsInputRequiredDiscoverResult() {
        assertThatThrownBy(() -> codec.decodeResult(Map.of(
                "resultType", "input_required",
                "requestState", "opaque")))
                .isInstanceOf(McpProtocolException.class);
    }

    @Test
    @DisplayName("decodeResult：ttlMs 为浮点 1.5 必须拒绝（schema 要求整型）")
    void rejectsFractionalTtlBecauseOfficialSchemaRequiresInteger() {
        assertThatThrownBy(() -> codec.decodeResult(Map.of(
                "resultType", "complete",
                "supportedVersions", List.of(McpProtocolVersions.V_2026_07_28),
                "capabilities", Map.of(),
                "ttlMs", 1.5,
                "cacheScope", "public")))
                .isInstanceOf(McpProtocolException.class);
    }

    @Test
    @DisplayName("decodeResult：supportedVersions 为空数组 / 非数组必须拒绝")
    void rejectsEmptyOrNonArraySupportedVersions() {
        assertThatThrownBy(() -> codec.decodeResult(Map.of(
                "resultType", "complete",
                "supportedVersions", List.of(),
                "capabilities", Map.of(),
                "ttlMs", 0,
                "cacheScope", "public")))
                .isInstanceOfSatisfying(McpProtocolException.class,
                        exception -> assertThat(exception.jsonRpcCode()).isEqualTo(-32602));

        assertThatThrownBy(() -> codec.decodeResult(Map.of(
                "resultType", "complete",
                "supportedVersions", "not-array",
                "capabilities", Map.of(),
                "ttlMs", 0,
                "cacheScope", "public")))
                .isInstanceOf(McpProtocolException.class);
    }

    @Test
    @DisplayName("decodeResult：cacheScope 非法字面量必须拒绝")
    void rejectsUnknownCacheScope() {
        assertThatThrownBy(() -> codec.decodeResult(Map.of(
                "resultType", "complete",
                "supportedVersions", List.of(McpProtocolVersions.V_2026_07_28),
                "capabilities", Map.of(),
                "ttlMs", 0,
                "cacheScope", "internal")))
                .isInstanceOfSatisfying(McpProtocolException.class,
                        exception -> assertThat(exception.jsonRpcCode()).isEqualTo(-32602));
    }

    @Test
    @DisplayName("decodeRequest：含 _meta 且 modern 版本一致时返回归一化结果")
    void decodeRequestProducesNormalizedRequest() {
        McpJsonRpcRequest request = codec.encodeRequest(
                "d-3",
                new McpImplementationInfo("atlas", "1.0"),
                Map.of("tools", Map.of()));

        McpNormalizedRequest normalized = codec.decodeRequest(
                request, McpProtocolVersions.V_2026_07_28);

        assertThat(normalized.method()).isEqualTo(McpDiscoveryCodec.METHOD);
        assertThat(normalized.arguments()).isEmpty();
        assertThat(normalized.peer().name()).isEqualTo("atlas");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }
}
