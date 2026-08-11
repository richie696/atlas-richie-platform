package cn.richie696.component.mcp.protocol.compatibility;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@link McpTransportBinding} 枚举的稳定性：覆盖 STDIO 与 STREAMABLE_HTTP 两种传输，
 * 是 {@link McpEraProbeStateMachine} 决策的关键维度。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpTransportBinding 传输绑定枚举")
class McpTransportBindingTest {

    @Test
    @DisplayName("枚举同时包含 STDIO 与 STREAMABLE_HTTP")
    void containsBothBindings() {
        assertThat(McpTransportBinding.values())
                .containsExactlyInAnyOrder(
                        McpTransportBinding.STDIO,
                        McpTransportBinding.STREAMABLE_HTTP);
    }

    @Test
    @DisplayName("valueOf 能按名称还原枚举")
    void valueOfReturnsEnum() {
        assertThat(McpTransportBinding.valueOf("STDIO"))
                .isEqualTo(McpTransportBinding.STDIO);
        assertThat(McpTransportBinding.valueOf("STREAMABLE_HTTP"))
                .isEqualTo(McpTransportBinding.STREAMABLE_HTTP);
    }
}
