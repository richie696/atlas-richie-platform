package cn.richie696.component.mcp.server.tool;

import cn.richie696.component.mcp.api.model.McpToolDescriptor;
import cn.richie696.component.mcp.api.model.McpToolResponse;
import cn.richie696.component.mcp.api.server.McpToolHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 {@link McpToolRegistration} 的不可变 record 语义：
 * descriptor 与 handler 必须非空、自动生成的 equals/hashCode 可用于身份比较、
 * handler 引用原样保留。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpToolRegistration 测试")
class McpToolRegistrationTest {

    @Test
    @DisplayName("构造时 null descriptor 抛 NPE")
    void rejectsNullDescriptor() {
        McpToolHandler handler = (a, c) -> CompletableFuture.completedFuture(null);
        assertThatThrownBy(() -> new McpToolRegistration(null, handler))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("descriptor");
    }

    @Test
    @DisplayName("构造时 null handler 抛 NPE")
    void rejectsNullHandler() {
        McpToolDescriptor descriptor = descriptor("alpha");
        assertThatThrownBy(() -> new McpToolRegistration(descriptor, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("handler");
    }

    @Test
    @DisplayName("成功构造后字段被原样暴露")
    void exposesDescriptorAndHandler() {
        McpToolDescriptor descriptor = descriptor("alpha");
        McpToolHandler handler = (a, c) -> CompletableFuture.completedFuture(null);

        McpToolRegistration registration = new McpToolRegistration(descriptor, handler);

        assertThat(registration.descriptor()).isSameAs(descriptor);
        assertThat(registration.handler()).isSameAs(handler);
    }

    @Test
    @DisplayName("相等语义：相同字段 equals 为 true 且 hashCode 一致")
    void equalRecordsBehaveEqually() {
        McpToolDescriptor descriptor = descriptor("alpha");
        McpToolHandler handler = (a, c) -> CompletableFuture.completedFuture(null);

        McpToolRegistration first = new McpToolRegistration(descriptor, handler);
        McpToolRegistration second = new McpToolRegistration(descriptor, handler);

        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
    }

    @Test
    @DisplayName("不等语义：handler 不同则对象不等")
    void differentHandlersAreNotEqual() {
        McpToolDescriptor descriptor = descriptor("alpha");
        McpToolHandler left = (a, c) -> CompletableFuture.completedFuture(
                new McpToolResponse(java.util.List.of(), Map.of(), false));
        McpToolHandler right = (a, c) -> CompletableFuture.completedFuture(null);

        assertThat(new McpToolRegistration(descriptor, left))
                .isNotEqualTo(new McpToolRegistration(descriptor, right));
    }

    @Test
    @DisplayName("不等语义：descriptor 名不同则对象不等")
    void differentDescriptorsAreNotEqual() {
        McpToolHandler handler = (a, c) -> CompletableFuture.completedFuture(null);

        assertThat(new McpToolRegistration(descriptor("alpha"), handler))
                .isNotEqualTo(new McpToolRegistration(descriptor("beta"), handler));
    }

    private static McpToolDescriptor descriptor(String name) {
        return new McpToolDescriptor(
                name,
                name,
                "test",
                Map.of("type", "object", "properties", Map.of()),
                Map.of(),
                Map.of());
    }
}