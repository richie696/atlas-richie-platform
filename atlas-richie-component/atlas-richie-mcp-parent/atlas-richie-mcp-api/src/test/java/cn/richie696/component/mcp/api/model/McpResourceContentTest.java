package cn.richie696.component.mcp.api.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 {@link McpResourceContent} 紧凑构造器对 contents 列表的不可变拷贝。
 */
@DisplayName("McpResourceContent Resource 内容 record")
class McpResourceContentTest {

    @Test
    @DisplayName("非空 contents：暴露并保持原值")
    void shouldExposeContents() {
        List<Map<String, Object>> raw = List.of(Map.of("text", "hello"));
        McpResourceContent content = new McpResourceContent(raw);

        assertThat(content.contents()).isEqualTo(raw);
    }

    @Test
    @DisplayName("null contents 回落为不可变空列表")
    void shouldFallbackNullContents() {
        McpResourceContent content = new McpResourceContent(null);

        assertThat(content.contents()).isEmpty();
        assertThat(content.contents()).isUnmodifiable();
    }

    @Test
    @DisplayName("contents 不可变：构造后外部突变不影响内部")
    void shouldDefensivelyCopyContents() {
        java.util.ArrayList<Map<String, Object>> mutable = new java.util.ArrayList<>();
        mutable.add(new java.util.LinkedHashMap<>(Map.of("text", "v")));

        McpResourceContent content = new McpResourceContent(mutable);
        mutable.clear();

        assertThat(content.contents()).hasSize(1);
        assertThatThrownBy(() -> content.contents().add(Map.of()))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
