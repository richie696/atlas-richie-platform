package cn.richie696.component.mcp.api.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 {@link McpResourceDescriptor} 紧凑构造器对 uri/name 必填与 size 非负校验，
 * 以及便捷构造器对 size/icons 的回落。
 */
@DisplayName("McpResourceDescriptor Resource 描述 record")
class McpResourceDescriptorTest {

    @Test
    @DisplayName("完整构造器：暴露全部字段并对集合做不可变拷贝")
    void shouldExposeAllFields() {
        List<Map<String, Object>> icons = List.of(Map.of("src", "icon.png"));
        Map<String, Object> annotations = Map.of("audience", "user");
        McpResourceDescriptor descriptor = new McpResourceDescriptor(
                "file:///a.txt", "a", "A", "Doc A", "text/plain", 1024L, icons, annotations);

        assertThat(descriptor.uri()).isEqualTo("file:///a.txt");
        assertThat(descriptor.name()).isEqualTo("a");
        assertThat(descriptor.size()).isEqualTo(1024L);
        assertThat(descriptor.icons()).isEqualTo(icons);
        assertThat(descriptor.annotations()).isEqualTo(annotations);
    }

    @Test
    @DisplayName("便捷构造器：size 回落为 null，icons 回落为 List.of()")
    void convenienceCtorShouldDefaultSizeAndIcons() {
        McpResourceDescriptor descriptor = new McpResourceDescriptor(
                "file:///a", "a", "A", "desc", "text/plain", Map.of("k", "v"));

        assertThat(descriptor.size()).isNull();
        assertThat(descriptor.icons()).isEmpty();
        assertThat(descriptor.annotations()).containsEntry("k", "v");
    }

    @Test
    @DisplayName("null uri 抛 NullPointerException")
    void shouldRejectNullUri() {
        assertThatThrownBy(() -> new McpResourceDescriptor(
                null, "a", "t", "d", "text/plain", null, List.of(), Map.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("uri");
    }

    @Test
    @DisplayName("null name 抛 NullPointerException")
    void shouldRejectNullName() {
        assertThatThrownBy(() -> new McpResourceDescriptor(
                "u", null, "t", "d", "text/plain", null, List.of(), Map.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("name");
    }

    @Test
    @DisplayName("负数 size 抛 IllegalArgumentException")
    void shouldRejectNegativeSize() {
        assertThatThrownBy(() -> new McpResourceDescriptor(
                "u", "a", "t", "d", "text/plain", -1L, List.of(), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-negative");
    }

    @Test
    @DisplayName("size=0 被接受（视为合法）")
    void shouldAcceptZeroSize() {
        McpResourceDescriptor descriptor = new McpResourceDescriptor(
                "u", "a", "t", "d", "text/plain", 0L, List.of(), Map.of());
        assertThat(descriptor.size()).isEqualTo(0L);
    }

    @Test
    @DisplayName("null icons/annotations 回落为不可变空集合")
    void shouldFallbackIconsAndAnnotations() {
        McpResourceDescriptor descriptor = new McpResourceDescriptor(
                "u", "a", "t", "d", "text/plain", null, null, null);

        assertThat(descriptor.icons()).isEmpty();
        assertThat(descriptor.annotations()).isEmpty();
        assertThat(descriptor.icons()).isUnmodifiable();
    }
}
