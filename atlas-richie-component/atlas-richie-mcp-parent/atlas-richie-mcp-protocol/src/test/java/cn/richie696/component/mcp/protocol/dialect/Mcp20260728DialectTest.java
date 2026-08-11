package cn.richie696.component.mcp.protocol.dialect;

import cn.richie696.component.mcp.protocol.McpMetaKeys;
import cn.richie696.component.mcp.protocol.McpProtocolException;
import cn.richie696.component.mcp.protocol.McpProtocolVersions;
import cn.richie696.component.mcp.protocol.model.McpJsonRpcRequest;
import cn.richie696.component.mcp.protocol.model.McpNormalizedRequest;
import cn.richie696.component.mcp.protocol.model.McpNormalizedResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 {@link Mcp20260728Dialect}（modern 无状态时代）的归一化语义：
 * 握手元数据全部位于 {@code params._meta}；{@code _meta.protocolVersion} 与
 * transport header 一致；响应必须显式带 {@code resultType}。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("Mcp20260728Dialect modern 方言")
class Mcp20260728DialectTest {

    private Mcp20260728Dialect dialect;

    @BeforeEach
    void setUp() {
        dialect = new Mcp20260728Dialect();
    }

    @Test
    @DisplayName("version() / era() 始终返回 modern 常量")
    void exposesModernVersionAndEra() {
        assertThat(dialect.version()).isEqualTo(McpProtocolVersions.V_2026_07_28);
        assertThat(dialect.era()).isEqualTo(cn.richie696.component.mcp.protocol.McpProtocolEra.STATELESS_2026);
    }

    @Test
    @DisplayName("_meta 缺失或非对象时报 -32602")
    void rejectsMissingOrInvalidMeta() {
        assertThatThrownBy(() -> dialect.normalizeRequest(
                new McpJsonRpcRequest("2.0", 1, "tools/list", Map.of()), null))
                .isInstanceOfSatisfying(McpProtocolException.class,
                        exception -> assertThat(exception.jsonRpcCode()).isEqualTo(-32602));

        assertThatThrownBy(() -> dialect.normalizeRequest(
                new McpJsonRpcRequest("2.0", 1, "tools/list", Map.of("_meta", "not-object")), null))
                .isInstanceOfSatisfying(McpProtocolException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo("MCP_INVALID_PARAMS"));
    }

    @Test
    @DisplayName("_meta.protocolVersion 不匹配 dialect 必须拒绝")
    void rejectsMismatchedMetadataVersion() {
        McpJsonRpcRequest request = new McpJsonRpcRequest("2.0", 1, "tools/list", Map.of(
                "_meta", Map.of(
                        McpMetaKeys.PROTOCOL_VERSION, McpProtocolVersions.V_2025_11_25,
                        McpMetaKeys.CLIENT_CAPABILITIES, Map.of())));

        assertThatThrownBy(() -> dialect.normalizeRequest(request, null))
                .isInstanceOfSatisfying(McpProtocolException.class, exception -> {
                    assertThat(exception.jsonRpcCode()).isEqualTo(-32022);
                    assertThat(exception.errorCode()).isEqualTo("MCP_UNSUPPORTED_PROTOCOL_VERSION");
                });
    }

    @Test
    @DisplayName("transport header 与 _meta 不一致时抛 MCP_HEADER_MISMATCH (-32020)")
    void rejectsHeaderMetadataMismatch() {
        McpJsonRpcRequest request = new McpJsonRpcRequest("2.0", 1, "tools/list", Map.of(
                "_meta", Map.of(
                        McpMetaKeys.PROTOCOL_VERSION, McpProtocolVersions.V_2026_07_28,
                        McpMetaKeys.CLIENT_CAPABILITIES, Map.of())));

        assertThatThrownBy(() -> dialect.normalizeRequest(
                request, McpProtocolVersions.V_2025_11_25))
                .isInstanceOfSatisfying(McpProtocolException.class, exception -> {
                    assertThat(exception.jsonRpcCode()).isEqualTo(-32020);
                    assertThat(exception.errorCode()).isEqualTo("MCP_HEADER_MISMATCH");
                });
    }

    @Test
    @DisplayName("完整请求：剥离 _meta 后 arguments 仅含业务参数；metadata 保留 _meta 内容")
    void normalizeStripsMetaAndExposesArguments() {
        McpJsonRpcRequest request = new McpJsonRpcRequest("2.0", "r-1", "tools/call", Map.of(
                "name", "customer_lookup",
                "arguments", Map.of("customerId", "C-1"),
                "_meta", Map.of(
                        McpMetaKeys.PROTOCOL_VERSION, McpProtocolVersions.V_2026_07_28,
                        McpMetaKeys.CLIENT_INFO, Map.of("name", "test-client", "version", "1.0"),
                        McpMetaKeys.CLIENT_CAPABILITIES, Map.of("tools", Map.of()))));

        McpNormalizedRequest normalized = dialect.normalizeRequest(
                request, McpProtocolVersions.V_2026_07_28);

        assertThat(normalized.peer().name()).isEqualTo("test-client");
        assertThat(normalized.arguments())
                .containsKeys("name", "arguments")
                .doesNotContainKey("_meta");
        assertThat(normalized.metadata()).containsKey(McpMetaKeys.PROTOCOL_VERSION);
    }

    @Test
    @DisplayName("normalizeResult 缺 resultType 报 -32602；input_required 与 complete 双向归一")
    void normalizeResultRoundtrip() {
        assertThatThrownBy(() -> dialect.normalizeResult(Map.of("k", "v")))
                .isInstanceOfSatisfying(McpProtocolException.class,
                        exception -> assertThat(exception.jsonRpcCode()).isEqualTo(-32602));

        McpNormalizedResult complete = dialect.normalizeResult(
                Map.of("resultType", "complete", "k", "v"));
        assertThat(complete.resultType()).isEqualTo(McpNormalizedResult.ResultType.COMPLETE);
        assertThat(complete.payload()).containsEntry("k", "v");

        McpNormalizedResult input = dialect.normalizeResult(
                Map.of("resultType", "input_required", "requestState", "opaque"));
        assertThat(input.resultType()).isEqualTo(McpNormalizedResult.ResultType.INPUT_REQUIRED);
        assertThat(input.payload()).containsEntry("requestState", "opaque");

        assertThatThrownBy(() -> dialect.normalizeResult(Map.of("resultType", "unknown")))
                .isInstanceOf(McpProtocolException.class);
    }

    @Test
    @DisplayName("encodeResult 显式写入 resultType（complete / input_required）")
    void encodeResultWritesExplicitResultType() {
        Map<String, Object> complete = dialect.encodeResult(
                new McpNormalizedResult(McpNormalizedResult.ResultType.COMPLETE, Map.of("k", "v")));
        assertThat(complete).containsEntry("resultType", "complete").containsEntry("k", "v");

        Map<String, Object> input = dialect.encodeResult(
                new McpNormalizedResult(McpNormalizedResult.ResultType.INPUT_REQUIRED, Map.of("k", "v")));
        assertThat(input).containsEntry("resultType", "input_required");
    }
}
