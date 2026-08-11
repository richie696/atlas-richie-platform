package cn.richie696.component.mcp.server.resource;

import cn.richie696.component.mcp.api.McpCallContext;
import cn.richie696.component.mcp.api.McpCancellationToken;
import cn.richie696.component.mcp.api.model.McpResourceDescriptor;
import cn.richie696.component.mcp.protocol.McpProtocolVersions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@link McpResourceVisibilityPolicy} 的常量与函数式契约：
 * {@link #ALLOW_ALL} 对任意描述符与上下文返回 true；作为函数式接口可被 lambda 实现。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpResourceVisibilityPolicy 测试")
class McpResourceVisibilityPolicyTest {

    @Test
    @DisplayName("ALLOW_ALL 对任意 Resource 描述符与上下文返回 true")
    void allowAllPermitsEverything() {
        McpResourceDescriptor descriptor = new McpResourceDescriptor(
                "file://a.txt", "a", null, null, "text/plain", null);
        McpCallContext context = new McpCallContext(
                "request-1",
                McpProtocolVersions.V_2026_07_28,
                "tenant-1",
                "alice",
                null,
                Map.of(),
                McpCancellationToken.NONE,
                null);

        assertThat(McpResourceVisibilityPolicy.ALLOW_ALL.isVisible(descriptor, context)).isTrue();
    }

    @Test
    @DisplayName("作为函数式接口：自定义策略可以基于 URI 前缀过滤")
    void functionalInterfaceAcceptsLambda() {
        McpResourceVisibilityPolicy policy = (descriptor, context) ->
                descriptor.uri().startsWith("file:///public/");

        McpResourceDescriptor publicFile = new McpResourceDescriptor(
                "file:///public/a.txt", "a", null, null, "text/plain", null);
        McpResourceDescriptor privateFile = new McpResourceDescriptor(
                "file:///private/a.txt", "a", null, null, "text/plain", null);
        McpCallContext context = new McpCallContext(
                "r", McpProtocolVersions.V_2026_07_28, "t", "alice",
                null, Map.of(), null, null);

        assertThat(policy.isVisible(publicFile, context)).isTrue();
        assertThat(policy.isVisible(privateFile, context)).isFalse();
    }
}