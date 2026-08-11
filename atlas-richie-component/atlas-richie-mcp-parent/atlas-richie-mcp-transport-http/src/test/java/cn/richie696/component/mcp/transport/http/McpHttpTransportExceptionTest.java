package cn.richie696.component.mcp.transport.http;

import cn.richie696.component.mcp.protocol.McpProtocolException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@link McpHttpTransportException} 作为服务端"HTTP 状态码 + 协议错误"复合异常
 * 的契约：协议错误为空时仍然能单独携带 HTTP 状态码；{@link McpHttpTransportException#protocolError()}
 * 在传入 null 时返回空 Optional；并通过 {@link RuntimeException#getCause()} 链路可见原始协议错误。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpHttpTransportException 服务端复合异常")
class McpHttpTransportExceptionTest {

    @Test
    @DisplayName("带协议错误构造：协议错误入参原样回传并作为 cause")
    void protocolErrorIsReturnedAndCausable() {
        McpProtocolException protocol =
                new McpProtocolException("MCP_INVALID_REQUEST", -32600, "bad", Map.of());
        McpHttpTransportException exception = new McpHttpTransportException(400, "bad http", protocol);

        assertThat(exception.httpStatus()).isEqualTo(400);
        assertThat(exception.protocolError()).contains(protocol);
        assertThat(exception.getCause()).isSameAs(protocol);
    }

    @Test
    @DisplayName("不带协议错误：protocolError 为空 Optional")
    void protocolErrorAbsentWhenNull() {
        McpHttpTransportException exception = new McpHttpTransportException(415, "no media", null);

        assertThat(exception.httpStatus()).isEqualTo(415);
        assertThat(exception.protocolError()).isEmpty();
    }

    @Test
    @DisplayName("message 与父类 getMessage 一致")
    void messageEqualsParentMessage() {
        McpHttpTransportException exception = new McpHttpTransportException(
                500, "internal http", null);

        assertThat(exception.getMessage()).isEqualTo("internal http");
    }
}
