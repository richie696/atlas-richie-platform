package cn.richie696.component.mcp.api.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 {@link McpPromptDescriptor} 紧凑构造器对 name 必填与 arguments 列表的不可变拷贝。
 */
@DisplayName("McpPromptDescriptor Prompt 描述 record")
class McpPromptDescriptorTest {

    @Test
    @DisplayName("完整参数：暴露全部字段")
    void shouldExposeAllFields() {
        List<Map<String, Object>> args = List.of(Map.of("name", "topic", "required", true));
        McpPromptDescriptor descriptor = new McpPromptDescriptor(
                "summarize", "Summarize", "summarize a doc", args);

        assertThat(descriptor.name()).isEqualTo("summarize");
        assertThat(descriptor.title()).isEqualTo("Summarize");
        assertThat(descriptor.description()).isEqualTo("summarize a doc");
        assertThat(descriptor.arguments()).isEqualTo(args);
    }

    @Test
    @DisplayName("null name 抛 NullPointerException")
    void shouldRejectNullName() {
        assertThatThrownBy(() -> new McpPromptDescriptor(null, "t", "d", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("name");
    }

    @Test
    @DisplayName("null arguments 回落为不可变空列表")
    void shouldFallbackNullArguments() {
        McpPromptDescriptor descriptor = new McpPromptDescriptor("n", "t", "d", null);

        assertThat(descriptor.arguments()).isEmpty();
        assertThat(descriptor.arguments()).isUnmodifiable();
    }

    @Test
    @DisplayName("arguments 不可变：外部突变不影响 descriptor")
    void shouldDefensivelyCopyArguments() {
        List<Map<String, Object>> mutable = new ArrayList<>();
        mutable.add(new java.util.LinkedHashMap<>(Map.of("name", "topic")));

        McpPromptDescriptor descriptor = new McpPromptDescriptor("n", "t", "d", mutable);
        mutable.clear();

        assertThat(descriptor.arguments()).hasSize(1);
    }
}
