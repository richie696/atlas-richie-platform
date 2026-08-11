package cn.richie696.component.mcp.server.tool;

import cn.richie696.component.mcp.api.McpCallContext;
import cn.richie696.component.mcp.api.McpCancellationToken;
import cn.richie696.component.mcp.api.model.McpToolDescriptor;
import cn.richie696.component.mcp.protocol.McpProtocolVersions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@link McpToolVisibilityPolicy} 的常量与函数式契约：
 * {@link #ALLOW_ALL} 对任意描述符与上下文返回 true；作为函数式接口可被 lambda 实现。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpToolVisibilityPolicy 测试")
class McpToolVisibilityPolicyTest {

    @Test
    @DisplayName("ALLOW_ALL 对任意 Tool 描述符与上下文返回 true")
    void allowAllPermitsEverything() {
        McpToolDescriptor descriptor = new McpToolDescriptor(
                "alpha", "Alpha", "test",
                Map.of("type", "object", "properties", Map.of()),
                Map.of(),
                Map.of());
        McpCallContext context = new McpCallContext(
                "request-1",
                McpProtocolVersions.V_2026_07_28,
                "tenant-1",
                "alice",
                null,
                Map.of(),
                McpCancellationToken.NONE,
                null);

        assertThat(McpToolVisibilityPolicy.ALLOW_ALL.isVisible(descriptor, context)).isTrue();
    }

    @Test
    @DisplayName("作为函数式接口：自定义策略可以基于描述符/上下文过滤")
    void functionalInterfaceAcceptsLambda() {
        McpToolVisibilityPolicy policy = (descriptor, context) ->
                descriptor.name().startsWith(context.subject());

        McpToolDescriptor alpha = new McpToolDescriptor(
                "alpha.tool", null, null,
                Map.of("type", "object"), Map.of(), Map.of());
        McpToolDescriptor beta = new McpToolDescriptor(
                "beta.tool", null, null,
                Map.of("type", "object"), Map.of(), Map.of());
        McpCallContext alice = new McpCallContext(
                "r", McpProtocolVersions.V_2026_07_28, "t", "alpha",
                null, Map.of(), null, null);

        assertThat(policy.isVisible(alpha, alice)).isTrue();
        assertThat(policy.isVisible(beta, alice)).isFalse();
    }
}