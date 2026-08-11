package cn.richie696.component.mcp.server.resource;

import cn.richie696.component.mcp.api.McpCallContext;
import cn.richie696.component.mcp.api.McpCancellationToken;
import cn.richie696.component.mcp.api.model.McpResourceDescriptor;
import cn.richie696.component.mcp.api.server.McpResourceHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 {@link McpResourceRegistration} 的不可变 record 语义：
 * descriptor / handler 必填校验、字段原样保留、相等 / 不等比较。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpResourceRegistration 测试")
class McpResourceRegistrationTest {

    @Test
    @DisplayName("构造时 null descriptor 抛 NPE")
    void rejectsNullDescriptor() {
        McpResourceHandler handler = (u, c) -> null;
        assertThatThrownBy(() -> new McpResourceRegistration(null, handler))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("descriptor");
    }

    @Test
    @DisplayName("构造时 null handler 抛 NPE")
    void rejectsNullHandler() {
        McpResourceDescriptor descriptor = new McpResourceDescriptor(
                "file://a.txt", "a", null, null, "text/plain", null);
        assertThatThrownBy(() -> new McpResourceRegistration(descriptor, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("handler");
    }

    @Test
    @DisplayName("字段原样保留")
    void exposesDescriptorAndHandler() {
        McpResourceDescriptor descriptor = new McpResourceDescriptor(
                "file://a.txt", "a", null, null, "text/plain", null);
        McpResourceHandler handler = (u, c) -> null;

        McpResourceRegistration registration = new McpResourceRegistration(descriptor, handler);

        assertThat(registration.descriptor()).isSameAs(descriptor);
        assertThat(registration.handler()).isSameAs(handler);
    }

    @Test
    @DisplayName("相等语义：相同字段 equals 为 true 且 hashCode 一致")
    void equalRecordsBehaveEqually() {
        McpResourceDescriptor descriptor = new McpResourceDescriptor(
                "file://a.txt", "a", null, null, "text/plain", null);
        McpResourceHandler handler = (u, c) -> null;

        McpResourceRegistration first = new McpResourceRegistration(descriptor, handler);
        McpResourceRegistration second = new McpResourceRegistration(descriptor, handler);

        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
    }

    @Test
    @DisplayName("不等语义：handler 不同则对象不等")
    void differentHandlersAreNotEqual() {
        McpResourceDescriptor descriptor = new McpResourceDescriptor(
                "file://a.txt", "a", null, null, "text/plain", null);
        McpResourceHandler left = (u, c) -> null;
        McpResourceHandler right = (u, c) -> null;

        assertThat(new McpResourceRegistration(descriptor, left))
                .isNotEqualTo(new McpResourceRegistration(descriptor, right));
    }

    @SuppressWarnings("unused")
    private static McpCallContext unusedContext() {
        return null;
    }

    @SuppressWarnings("unused")
    private static Map<String, Object> unusedAnnotations() {
        return Map.of();
    }

    @SuppressWarnings("unused")
    private static McpCancellationToken unusedToken() {
        return McpCancellationToken.NONE;
    }
}