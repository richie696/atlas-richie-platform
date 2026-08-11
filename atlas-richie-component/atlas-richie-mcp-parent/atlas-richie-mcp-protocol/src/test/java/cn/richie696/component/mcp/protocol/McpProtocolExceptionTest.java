package cn.richie696.component.mcp.protocol;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 {@link McpProtocolException} 作为协议层异常的契约：
 * 业务错误码与 JSON-RPC 错误码独立传输；{@code data} 字段在构造期归一为不可变视图；
 * 根因链路（{@code cause}）能被调用方精确追溯。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpProtocolException 协议层异常契约")
class McpProtocolExceptionTest {

    @Test
    @DisplayName("构造期归一化 data：null → Map.of()")
    void normalizesNullDataToEmptyMap() {
        McpProtocolException exception = new McpProtocolException(
                "MCP_INVALID_PARAMS", -32602, "bad", null);

        assertThat(exception.data()).isEmpty();
        assertThat(exception.jsonRpcCode()).isEqualTo(-32602);
        assertThat(exception.errorCode()).isEqualTo("MCP_INVALID_PARAMS");
        assertThat(exception.getMessage()).isEqualTo("bad");
    }

    @Test
    @DisplayName("data 字段在构造后不可被外部修改")
    void dataFieldIsImmutableSnapshot() {
        Map<String, Object> mutable = new HashMap<>();
        mutable.put("k", "v");
        McpProtocolException exception = new McpProtocolException(
                "MCP_INVALID_PARAMS", -32602, "bad", mutable);

        // 外部修改入参不影响已构造异常的 data
        mutable.put("intruder", true);
        assertThat(exception.data()).containsOnly(Map.entry("k", "v"));
        assertThatThrownBy(() -> exception.data().put("k2", "v2"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("带 cause 的构造器保留根因链")
    void preservesCauseChain() {
        IllegalStateException rootCause = new IllegalStateException("boom");
        McpProtocolException exception = new McpProtocolException(
                "MCP_UPSTREAM", -32020, "wrapped", Map.of("k", "v"), rootCause);

        assertThat(exception.getCause()).isSameAs(rootCause);
        assertThat(exception.data()).containsExactly(Map.entry("k", "v"));
        assertThat(exception.jsonRpcCode()).isEqualTo(-32020);
    }

    @Test
    @DisplayName("带 cause 的构造器也接受 null data 并归一化")
    void causeConstructorNormalizesNullData() {
        McpProtocolException exception = new McpProtocolException(
                "MCP_UPSTREAM", -32020, "wrapped", null, new RuntimeException("x"));

        assertThat(exception.data()).isEmpty();
        assertThat(exception.getCause()).hasMessage("x");
    }

    @Test
    @DisplayName("McpProtocolException 是 McpException 的特化")
    void isInstanceOfMcpException() {
        McpProtocolException exception = new McpProtocolException(
                "MCP_INVALID_PARAMS", -32602, "bad", Map.of());

        assertThat(exception).isInstanceOf(cn.richie696.component.mcp.api.McpException.class);
    }

    @Test
    @DisplayName("data Map 为 null 时 McpException.errorCode 仍可读")
    void errorCodeIsAlwaysReadable() {
        McpProtocolException exception = new McpProtocolException(
                null, -32602, "no-code", Map.of());

        // errorCode 来自 McpException，可以为 null 但 message 必填
        assertThat(exception.getMessage()).isEqualTo("no-code");
        assertThat(exception.jsonRpcCode()).isEqualTo(-32602);
    }
}
