package cn.richie696.component.mcp.api.server;

import cn.richie696.component.mcp.api.McpException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 {@link McpToolExecutionException} 固定错误码、消息非空校验与结构化内容。
 */
@DisplayName("McpToolExecutionException 可恢复业务异常")
class McpToolExecutionExceptionTest {

    @Test
    @DisplayName("单参构造：使用默认 null 结构化内容与根因")
    void shouldExposeErrorCodeAndMessage() {
        McpToolExecutionException ex = new McpToolExecutionException("missing required field: city");

        assertThat(ex.errorCode()).isEqualTo("MCP_TOOL_EXECUTION_ERROR");
        assertThat(ex.getMessage()).isEqualTo("missing required field: city");
        assertThat(ex.structuredContent()).isNull();
        assertThat(ex.getCause()).isNull();
    }

    @Test
    @DisplayName("完整构造：暴露结构化内容与根因")
    void shouldExposeStructuredContentAndCause() {
        Throwable cause = new IllegalStateException("down");
        McpToolExecutionException ex = new McpToolExecutionException(
                "invalid value", Map.of("hint", "use ISO date"), cause);

        assertThat(ex.structuredContent()).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> structured = (Map<String, Object>) ex.structuredContent();
        assertThat(structured).containsEntry("hint", "use ISO date");
        assertThat(ex.getCause()).isSameAs(cause);
    }

    @Test
    @DisplayName("null message 抛 NullPointerException")
    void shouldRejectNullMessage() {
        assertThatThrownBy(() -> new McpToolExecutionException(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("modelSafeMessage");
    }

    @Test
    @DisplayName("空白 message 抛 IllegalArgumentException")
    void shouldRejectBlankMessage() {
        assertThatThrownBy(() -> new McpToolExecutionException("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be blank");
    }

    @Test
    @DisplayName("继承自 McpException：可被业务统一 catch")
    void shouldBeMcpException() {
        assertThat(new McpToolExecutionException("x")).isInstanceOf(McpException.class);
    }
}
