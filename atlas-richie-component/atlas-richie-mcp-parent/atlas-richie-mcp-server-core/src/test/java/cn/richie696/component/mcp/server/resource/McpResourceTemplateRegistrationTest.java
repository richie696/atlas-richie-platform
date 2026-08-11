package cn.richie696.component.mcp.server.resource;

import cn.richie696.component.mcp.api.model.McpResourceTemplateDescriptor;
import cn.richie696.component.mcp.api.server.McpResourceHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 {@link McpResourceTemplateRegistration} 的不可变 record 语义：
 * descriptor / handler 必填校验、字段原样保留、相等比较。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpResourceTemplateRegistration 测试")
class McpResourceTemplateRegistrationTest {

    @Test
    @DisplayName("构造时 null descriptor 抛 NPE")
    void rejectsNullDescriptor() {
        McpResourceHandler handler = (u, c) -> null;
        assertThatThrownBy(() -> new McpResourceTemplateRegistration(null, handler))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("descriptor");
    }

    @Test
    @DisplayName("构造时 null handler 抛 NPE")
    void rejectsNullHandler() {
        McpResourceTemplateDescriptor descriptor = new McpResourceTemplateDescriptor(
                "file:///{path}", "file", null, null, "text/plain", null, null);
        assertThatThrownBy(() -> new McpResourceTemplateRegistration(descriptor, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("handler");
    }

    @Test
    @DisplayName("字段原样保留")
    void exposesDescriptorAndHandler() {
        McpResourceTemplateDescriptor descriptor = new McpResourceTemplateDescriptor(
                "file:///{path}", "file", null, null, "text/plain", null, null);
        McpResourceHandler handler = (u, c) -> null;

        McpResourceTemplateRegistration registration =
                new McpResourceTemplateRegistration(descriptor, handler);

        assertThat(registration.descriptor()).isSameAs(descriptor);
        assertThat(registration.handler()).isSameAs(handler);
    }

    @Test
    @DisplayName("相等语义：相同字段 equals 为 true 且 hashCode 一致")
    void equalRecordsBehaveEqually() {
        McpResourceTemplateDescriptor descriptor = new McpResourceTemplateDescriptor(
                "file:///{path}", "file", null, null, "text/plain", null, null);
        McpResourceHandler handler = (u, c) -> null;

        McpResourceTemplateRegistration first = new McpResourceTemplateRegistration(descriptor, handler);
        McpResourceTemplateRegistration second = new McpResourceTemplateRegistration(descriptor, handler);

        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
    }
}