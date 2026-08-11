package cn.richie696.component.mcp.protocol.dialect;

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
 * 验证 {@link Mcp20251125Dialect}（legacy 会话时代）的归一化语义：
 * {@code initialize} 必传 {@code protocolVersion / clientInfo / capabilities}；
 * 非 initialize 请求版本从 {@code transportProtocolVersion} 推断；响应只支持
 * {@code COMPLETE}（{@code INPUT_REQUIRED} 在编码时显式报错）。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("Mcp20251125Dialect legacy 方言")
class Mcp20251125DialectTest {

    private Mcp20251125Dialect dialect;

    @BeforeEach
    void setUp() {
        dialect = new Mcp20251125Dialect();
    }

    @Test
    @DisplayName("version() / era() 始终返回 legacy 常量")
    void exposesLegacyVersionAndEra() {
        assertThat(dialect.version()).isEqualTo(McpProtocolVersions.V_2025_11_25);
        assertThat(dialect.era()).isEqualTo(cn.richie696.component.mcp.protocol.McpProtocolEra.SESSION_2025);
    }

    @Test
    @DisplayName("initialize 请求：必填字段齐全时归一成功，握手元数据从 arguments 剥离")
    void normalizeInitializeStripsHandshakeMetadata() {
        McpJsonRpcRequest request = new McpJsonRpcRequest("2.0", 1, "initialize", Map.of(
                "protocolVersion", McpProtocolVersions.V_2025_11_25,
                "clientInfo", Map.of("name", "legacy-client", "version", "1.0"),
                "capabilities", Map.of("roots", Map.of())));

        McpNormalizedRequest normalized = dialect.normalizeRequest(request, null);

        assertThat(normalized.era()).isEqualTo(cn.richie696.component.mcp.protocol.McpProtocolEra.SESSION_2025);
        assertThat(normalized.peer().name()).isEqualTo("legacy-client");
        assertThat(normalized.arguments()).isEmpty();
        assertThat(normalized.capabilities()).containsKey("roots");
    }

    @Test
    @DisplayName("initialize 请求缺失 protocolVersion / clientInfo / capabilities 必须报错")
    void initializeRequiresAllHandshakeFields() {
        assertThatThrownBy(() -> dialect.normalizeRequest(
                new McpJsonRpcRequest("2.0", 1, "initialize", Map.of(
                        "protocolVersion", McpProtocolVersions.V_2025_11_25,
                        "capabilities", Map.of())),
                null))
                .isInstanceOfSatisfying(McpProtocolException.class,
                        exception -> assertThat(exception.jsonRpcCode()).isEqualTo(-32602));

        assertThatThrownBy(() -> dialect.normalizeRequest(
                new McpJsonRpcRequest("2.0", 1, "initialize", Map.of(
                        "clientInfo", Map.of("name", "c", "version", "1"),
                        "capabilities", Map.of())),
                null))
                .isInstanceOf(McpProtocolException.class);

        assertThatThrownBy(() -> dialect.normalizeRequest(
                new McpJsonRpcRequest("2.0", 1, "initialize", Map.of(
                        "protocolVersion", McpProtocolVersions.V_2025_11_25,
                        "clientInfo", Map.of("name", "c", "version", "1"))),
                null))
                .isInstanceOf(McpProtocolException.class);
    }

    @Test
    @DisplayName("非 initialize 请求：版本从 transportProtocolVersion 推断；缺失时回退到 dialect 版本")
    void nonInitializeUsesTransportVersion() {
        McpJsonRpcRequest request = new McpJsonRpcRequest("2.0", 1, "tools/list", Map.of("k", "v"));

        McpNormalizedRequest fromHeader = dialect.normalizeRequest(
                request, McpProtocolVersions.V_2025_11_25);
        McpNormalizedRequest fromNull = dialect.normalizeRequest(request, null);

        assertThat(fromHeader.arguments()).containsEntry("k", "v");
        assertThat(fromHeader.peer()).isNull();
        assertThat(fromHeader.capabilities()).isEmpty();
        assertThat(fromHeader.protocolVersion()).isEqualTo(McpProtocolVersions.V_2025_11_25);
        assertThat(fromNull.protocolVersion()).isEqualTo(McpProtocolVersions.V_2025_11_25);
    }

    @Test
    @DisplayName("请求协议版本不匹配 dialect 版本时抛 -32022")
    void rejectsMismatchedProtocolVersion() {
        McpJsonRpcRequest request = new McpJsonRpcRequest("2.0", 1, "initialize", Map.of(
                "protocolVersion", McpProtocolVersions.V_2026_07_28,
                "clientInfo", Map.of("name", "c", "version", "1"),
                "capabilities", Map.of()));

        assertThatThrownBy(() -> dialect.normalizeRequest(request, null))
                .isInstanceOfSatisfying(McpProtocolException.class, exception -> {
                    assertThat(exception.jsonRpcCode()).isEqualTo(-32022);
                    assertThat(exception.errorCode()).isEqualTo("MCP_PROTOCOL_VERSION_MISMATCH");
                });
    }

    @Test
    @DisplayName("normalizeResult：缺省或 complete → COMPLETE；其他 resultType 报错")
    void normalizeResultHandlesCompleteOnly() {
        McpNormalizedResult empty = dialect.normalizeResult(null);
        assertThat(empty.resultType()).isEqualTo(McpNormalizedResult.ResultType.COMPLETE);
        assertThat(empty.payload()).isEmpty();

        McpNormalizedResult explicit = dialect.normalizeResult(Map.of("resultType", "complete", "k", "v"));
        assertThat(explicit.resultType()).isEqualTo(McpNormalizedResult.ResultType.COMPLETE);
        assertThat(explicit.payload()).containsEntry("k", "v");

        assertThatThrownBy(() -> dialect.normalizeResult(Map.of("resultType", "input_required")))
                .isInstanceOfSatisfying(McpProtocolException.class,
                        exception -> assertThat(exception.jsonRpcCode()).isEqualTo(-32602));
    }

    @Test
    @DisplayName("encodeResult：仅支持 COMPLETE；INPUT_REQUIRED 直接报错")
    void encodeResultRejectsInputRequired() {
        Map<String, Object> wire = dialect.encodeResult(
                new McpNormalizedResult(McpNormalizedResult.ResultType.COMPLETE, Map.of("k", "v")));

        assertThat(wire).containsEntry("k", "v").doesNotContainKey("resultType");

        assertThatThrownBy(() -> dialect.encodeResult(new McpNormalizedResult(
                McpNormalizedResult.ResultType.INPUT_REQUIRED, Map.of())))
                .isInstanceOfSatisfying(McpProtocolException.class,
                        exception -> assertThat(exception.jsonRpcCode()).isEqualTo(-32602));
    }
}
