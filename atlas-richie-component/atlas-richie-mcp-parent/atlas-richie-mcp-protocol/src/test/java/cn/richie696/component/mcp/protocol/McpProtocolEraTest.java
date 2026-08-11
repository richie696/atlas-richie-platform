package cn.richie696.component.mcp.protocol;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@link McpProtocolEra} 枚举的稳定性：覆盖两个时代（2026 / 2025）即可支撑
 * 探测状态机与现代/传统分支决策。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpProtocolEra 协议时代枚举")
class McpProtocolEraTest {

    @Test
    @DisplayName("枚举同时包含无状态与会话两种时代")
    void containsBothStatelessAndSessionEras() {
        assertThat(McpProtocolEra.values())
                .containsExactly(McpProtocolEra.STATELESS_2026, McpProtocolEra.SESSION_2025);
    }

    @Test
    @DisplayName("valueOf 能按名称还原枚举")
    void valueOfReturnsEnum() {
        assertThat(McpProtocolEra.valueOf("STATELESS_2026"))
                .isEqualTo(McpProtocolEra.STATELESS_2026);
        assertThat(McpProtocolEra.valueOf("SESSION_2025"))
                .isEqualTo(McpProtocolEra.SESSION_2025);
    }
}
