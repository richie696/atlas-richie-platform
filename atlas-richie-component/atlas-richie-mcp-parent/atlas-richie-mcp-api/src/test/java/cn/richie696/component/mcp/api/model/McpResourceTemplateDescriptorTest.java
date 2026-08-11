package cn.richie696.component.mcp.api.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 {@link McpResourceTemplateDescriptor} 紧凑构造器对 uriTemplate/name 必填与集合的不可变拷贝。
 */
@DisplayName("McpResourceTemplateDescriptor Resource 模板描述 record")
class McpResourceTemplateDescriptorTest {

    @Test
    @DisplayName("完整参数：暴露全部字段")
    void shouldExposeAllFields() {
        List<Map<String, Object>> icons = List.of(Map.of("src", "x.png"));
        McpResourceTemplateDescriptor descriptor = new McpResourceTemplateDescriptor(
                "file:///{path}", "files", "Files", "param files", "text/plain", icons, Map.of("k", "v"));

        assertThat(descriptor.uriTemplate()).isEqualTo("file:///{path}");
        assertThat(descriptor.name()).isEqualTo("files");
        assertThat(descriptor.icons()).isEqualTo(icons);
        assertThat(descriptor.annotations()).containsEntry("k", "v");
    }

    @Test
    @DisplayName("null uriTemplate 抛 NullPointerException")
    void shouldRejectNullUriTemplate() {
        assertThatThrownBy(() -> new McpResourceTemplateDescriptor(
                null, "n", "t", "d", "text/plain", null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("uriTemplate");
    }

    @Test
    @DisplayName("null name 抛 NullPointerException")
    void shouldRejectNullName() {
        assertThatThrownBy(() -> new McpResourceTemplateDescriptor(
                "u", null, "t", "d", "text/plain", null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("name");
    }

    @Test
    @DisplayName("null icons/annotations 回落为不可变空集合")
    void shouldFallbackEmptyCollections() {
        McpResourceTemplateDescriptor descriptor = new McpResourceTemplateDescriptor(
                "u", "n", "t", "d", "text/plain", null, null);

        assertThat(descriptor.icons()).isEmpty();
        assertThat(descriptor.annotations()).isEmpty();
        assertThat(descriptor.icons()).isUnmodifiable();
    }
}
