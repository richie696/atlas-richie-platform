package cn.richie696.component.mcp.api.server;

import cn.richie696.component.mcp.api.McpException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 {@link McpArgumentBindingException} 错误码、argumentName 与继承关系。
 */
@DisplayName("McpArgumentBindingException 参数绑定异常")
class McpArgumentBindingExceptionTest {

    @Test
    @DisplayName("两参构造：暴露固定错误码与 argumentName")
    void shouldExposeFields() {
        McpArgumentBindingException ex = new McpArgumentBindingException("age", "must be integer");

        assertThat(ex.errorCode()).isEqualTo("MCP_TOOL_ARGUMENT_BINDING_FAILED");
        assertThat(ex.getMessage()).isEqualTo("must be integer");
        assertThat(ex.argumentName()).isEqualTo("age");
        assertThat(ex.getCause()).isNull();
    }

    @Test
    @DisplayName("三参构造：暴露根因")
    void shouldExposeCause() {
        Throwable cause = new NumberFormatException("not a number");
        McpArgumentBindingException ex = new McpArgumentBindingException("age", "not integer", cause);

        assertThat(ex.getCause()).isSameAs(cause);
        assertThat(ex.argumentName()).isEqualTo("age");
    }

    @Test
    @DisplayName("null argumentName 允许：业务可能只知道消息")
    void shouldAllowNullArgumentName() {
        McpArgumentBindingException ex = new McpArgumentBindingException(null, "bad");
        assertThat(ex.argumentName()).isNull();
    }

    @Test
    @DisplayName("继承自 McpException：可被业务统一 catch")
    void shouldBeMcpException() {
        assertThat(new McpArgumentBindingException("a", "b")).isInstanceOf(McpException.class);
    }

    @Test
    @DisplayName("错误码固定为 MCP_TOOL_ARGUMENT_BINDING_FAILED")
    void errorCodeShouldBeFixed() {
        assertThat(new McpArgumentBindingException("a", "b").errorCode())
                .isEqualTo("MCP_TOOL_ARGUMENT_BINDING_FAILED");
        assertThat(new McpArgumentBindingException("a", "b", new RuntimeException()).errorCode())
                .isEqualTo("MCP_TOOL_ARGUMENT_BINDING_FAILED");
    }

    @Test
    @DisplayName("null message 不抛 NPE：父类 McpException 不做必填校验")
    void shouldAcceptNullMessage() {
        McpArgumentBindingException ex = new McpArgumentBindingException("age", null);
        assertThat(ex.argumentName()).isEqualTo("age");
        assertThat(ex.getMessage()).isNull();
    }
}
