package cn.richie696.component.mcp.server.tool;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@link McpToolRegistryChangeListener} 的函数式契约：
 * 可由 lambda 实现、接收 {@link McpToolRefreshResult} 载荷并按需消费。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpToolRegistryChangeListener 测试")
class McpToolRegistryChangeListenerTest {

    @Test
    @DisplayName("作为函数式接口：lambda 接收变更结果并可消费")
    void functionalInterfaceAcceptsLambda() {
        AtomicReference<McpToolRefreshResult> captured = new AtomicReference<>();
        McpToolRegistryChangeListener listener = captured::set;

        McpToolRefreshResult result = McpToolRefreshResult.unchanged(1L);
        listener.onChanged(result);

        assertThat(captured.get()).isSameAs(result);
    }

    @Test
    @DisplayName("作为函数式接口：自定义监听器可基于差异字段渲染文本")
    void customListenerRendersDescriptiveText() {
        McpToolRefreshResult result = new McpToolRefreshResult(
                0L, 1L, Set.of("alpha"), Set.of("beta"), Set.of("gamma"));

        AtomicReference<String> rendered = new AtomicReference<>();
        McpToolRegistryChangeListener listener = r -> rendered.set(
                "added=" + r.addedTools() + ";removed=" + r.removedTools() + ";updated=" + r.updatedTools());

        listener.onChanged(result);

        assertThat(rendered.get()).isEqualTo("added=[alpha];removed=[beta];updated=[gamma]");
    }
}