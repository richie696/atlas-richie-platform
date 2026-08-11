package cn.richie696.component.mcp.api.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 {@link McpPromptContent} 紧凑构造器对 messages 列表的不可变拷贝。
 */
@DisplayName("McpPromptContent Prompt 渲染内容 record")
class McpPromptContentTest {

    @Test
    @DisplayName("非空 messages：暴露并保持原值")
    void shouldExposeMessages() {
        List<Map<String, Object>> messages = List.of(
                Map.of("role", "system", "content", "you are helpful"),
                Map.of("role", "user", "content", "hi"));
        McpPromptContent content = new McpPromptContent("desc", messages);

        assertThat(content.description()).isEqualTo("desc");
        assertThat(content.messages()).isEqualTo(messages);
    }

    @Test
    @DisplayName("null messages 回落为不可变空列表")
    void shouldFallbackNullMessages() {
        McpPromptContent content = new McpPromptContent("d", null);

        assertThat(content.messages()).isEmpty();
        assertThat(content.messages()).isUnmodifiable();
    }

    @Test
    @DisplayName("messages 不可变：外部突变不影响内部")
    void shouldDefensivelyCopyMessages() {
        List<Map<String, Object>> mutable = new ArrayList<>();
        mutable.add(new java.util.LinkedHashMap<>(Map.of("role", "user")));

        McpPromptContent content = new McpPromptContent("d", mutable);
        mutable.clear();

        assertThat(content.messages()).hasSize(1);
        assertThatThrownBy(() -> content.messages().add(Map.of()))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
