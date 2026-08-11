package cn.richie696.component.mcp.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 {@link McpException} 的错误码存储与 {@link McpCallCancelledException} 的统一错误码约定。
 */
@DisplayName("McpException 异常族")
class McpExceptionTest {

    @Test
    @DisplayName("McpException 暴露 errorCode 与 message")
    void shouldExposeCodeAndMessage() {
        McpException ex = new McpException("CODE_X", "boom");

        assertThat(ex.errorCode()).isEqualTo("CODE_X");
        assertThat(ex.getMessage()).isEqualTo("boom");
        assertThat(ex.getCause()).isNull();
    }

    @Test
    @DisplayName("McpException 可携带根因")
    void shouldPreserveRootCause() {
        Throwable root = new IllegalStateException("down");
        McpException ex = new McpException("CODE_X", "wrap", root);

        assertThat(ex.getCause()).isSameAs(root);
    }

    @Test
    @DisplayName("McpException 是 RuntimeException，调用方无需声明")
    void shouldBeUnchecked() {
        assertThat(RuntimeException.class).isAssignableFrom(McpException.class);
    }

    @Test
    @DisplayName("McpCallCancelledException 使用固定错误码与默认消息")
    void cancelledExceptionShouldUseCanonicalCode() {
        McpCallCancelledException ex = new McpCallCancelledException();

        assertThat(ex.errorCode()).isEqualTo("MCP_CALL_CANCELLED");
        assertThat(ex.getMessage()).isEqualTo("MCP call was cancelled");
        assertThat(ex.getCause()).isNull();
    }

    @Test
    @DisplayName("McpCallCancelledException 继承自 McpException")
    void cancelledShouldBeMcpException() {
        assertThat(new McpCallCancelledException()).isInstanceOf(McpException.class);
    }

    @Test
    @DisplayName("throwIfCancellationRequested 在 token 报告取消时抛出规范异常")
    void shouldThrowWhenTokenRequestsCancellation() {
        McpCancellationToken token = () -> true;

        assertThatThrownBy(token::throwIfCancellationRequested)
                .isInstanceOf(McpCallCancelledException.class)
                .extracting("errorCode")
                .isEqualTo("MCP_CALL_CANCELLED");
    }

    @Test
    @DisplayName("throwIfCancellationRequested 在 token 报告未取消时静默通过")
    void shouldNoopWhenTokenNotCancelled() {
        McpCancellationToken token = () -> false;

        token.throwIfCancellationRequested();
        // 无异常即可
        assertThat(McpCancellationToken.NONE.isCancellationRequested()).isFalse();
    }
}
