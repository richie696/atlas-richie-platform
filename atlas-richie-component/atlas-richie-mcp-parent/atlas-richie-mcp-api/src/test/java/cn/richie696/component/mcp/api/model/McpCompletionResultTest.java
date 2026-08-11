package cn.richie696.component.mcp.api.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 {@link McpCompletionResult} 紧凑构造器对 values 上限（100）与 total 非负的校验。
 */
@DisplayName("McpCompletionResult 参数补全结果 record")
class McpCompletionResultTest {

    @Test
    @DisplayName("合法值：暴露 values/total/hasMore")
    void shouldExposeAllFields() {
        McpCompletionResult result = new McpCompletionResult(List.of("a", "b"), 10, true);

        assertThat(result.values()).containsExactly("a", "b");
        assertThat(result.total()).isEqualTo(10);
        assertThat(result.hasMore()).isTrue();
    }

    @Test
    @DisplayName("null values 回落为不可变空列表")
    void shouldFallbackNullValues() {
        McpCompletionResult result = new McpCompletionResult(null, null, false);

        assertThat(result.values()).isEmpty();
        assertThat(result.values()).isUnmodifiable();
        assertThat(result.total()).isNull();
    }

    @Test
    @DisplayName("values 不可变：构造后外部突变不影响内部")
    void shouldDefensivelyCopyValues() {
        java.util.ArrayList<String> mutable = new java.util.ArrayList<>(List.of("a"));
        McpCompletionResult result = new McpCompletionResult(mutable, null, false);

        mutable.add("b");

        assertThat(result.values()).containsExactly("a");
    }

    @Test
    @DisplayName("values 数量恰为 100 时合法")
    void shouldAcceptExactlyHundredValues() {
        List<String> hundred = IntStream.range(0, 100).mapToObj(i -> "v" + i).toList();
        McpCompletionResult result = new McpCompletionResult(hundred, 100, false);

        assertThat(result.values()).hasSize(100);
    }

    @Test
    @DisplayName("values 数量超过 100 抛 IllegalArgumentException")
    void shouldRejectOverHundredValues() {
        List<String> oneTooMany = IntStream.range(0, 101).mapToObj(i -> "v" + i).toList();

        assertThatThrownBy(() -> new McpCompletionResult(oneTooMany, 101, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("100");
    }

    @Test
    @DisplayName("负数 total 抛 IllegalArgumentException")
    void shouldRejectNegativeTotal() {
        assertThatThrownBy(() -> new McpCompletionResult(List.of("a"), -1, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-negative");
    }

    @Test
    @DisplayName("total=0 被接受")
    void shouldAcceptZeroTotal() {
        McpCompletionResult result = new McpCompletionResult(List.of(), 0, false);
        assertThat(result.total()).isEqualTo(0);
    }
}
