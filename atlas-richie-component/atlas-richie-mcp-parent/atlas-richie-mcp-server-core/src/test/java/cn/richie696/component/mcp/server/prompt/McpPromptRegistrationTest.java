package cn.richie696.component.mcp.server.prompt;

import cn.richie696.component.mcp.api.model.McpPromptDescriptor;
import cn.richie696.component.mcp.api.server.McpPromptHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 {@link McpPromptRegistration} 的不可变 record 语义：
 * descriptor / handler 必填校验、字段原样保留、相等 / 不等比较。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpPromptRegistration 测试")
class McpPromptRegistrationTest {

    @Test
    @DisplayName("构造时 null descriptor 抛 NPE")
    void rejectsNullDescriptor() {
        McpPromptHandler handler = (a, c) -> CompletableFuture.completedFuture(null);
        assertThatThrownBy(() -> new McpPromptRegistration(null, handler))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("descriptor");
    }

    @Test
    @DisplayName("构造时 null handler 抛 NPE")
    void rejectsNullHandler() {
        McpPromptDescriptor descriptor = new McpPromptDescriptor("alpha", null, null, List.of());
        assertThatThrownBy(() -> new McpPromptRegistration(descriptor, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("handler");
    }

    @Test
    @DisplayName("字段原样保留")
    void exposesDescriptorAndHandler() {
        McpPromptDescriptor descriptor = new McpPromptDescriptor("alpha", null, null, List.of());
        McpPromptHandler handler = (a, c) -> CompletableFuture.completedFuture(null);

        McpPromptRegistration registration = new McpPromptRegistration(descriptor, handler);

        assertThat(registration.descriptor()).isSameAs(descriptor);
        assertThat(registration.handler()).isSameAs(handler);
    }

    @Test
    @DisplayName("相等语义：相同字段 equals 为 true 且 hashCode 一致")
    void equalRecordsBehaveEqually() {
        McpPromptDescriptor descriptor = new McpPromptDescriptor("alpha", null, null,
                List.of(Map.of("name", "x", "required", true)));
        McpPromptHandler handler = (a, c) -> CompletableFuture.completedFuture(null);

        McpPromptRegistration first = new McpPromptRegistration(descriptor, handler);
        McpPromptRegistration second = new McpPromptRegistration(descriptor, handler);

        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
    }

    @Test
    @DisplayName("不等语义：handler 不同则对象不等")
    void differentHandlersAreNotEqual() {
        McpPromptDescriptor descriptor = new McpPromptDescriptor("alpha", null, null, List.of());
        McpPromptHandler left = (a, c) -> CompletableFuture.completedFuture(null);
        McpPromptHandler right = (a, c) -> CompletableFuture.completedFuture(null);

        assertThat(new McpPromptRegistration(descriptor, left))
                .isNotEqualTo(new McpPromptRegistration(descriptor, right));
    }
}