package cn.richie696.component.mcp.api.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 {@link McpToolResponse} 紧凑构造器对 resultType 枚举的强校验，
 * 以及便捷构造器与 inputRequired 工厂的语义。
 */
@DisplayName("McpToolResponse Tool 结果 record")
class McpToolResponseTest {

    @Test
    @DisplayName("便捷构造器：默认 resultType=complete 且不带多轮字段")
    void convenienceCtorShouldUseCompleteResultType() {
        McpToolResponse response = new McpToolResponse(List.of(Map.of("text", "ok")), null, false);

        assertThat(response.resultType()).isEqualTo("complete");
        assertThat(response.inputRequests()).isEmpty();
        assertThat(response.requestState()).isNull();
        assertThat(response.error()).isFalse();
    }

    @Test
    @DisplayName("inputRequired 工厂：填入 inputRequests 与 requestState")
    void inputRequiredFactoryShouldSetRoundTripState() {
        McpToolResponse response = McpToolResponse.inputRequired(
                Map.of("city", "city name"),
                "state-1");

        assertThat(response.resultType()).isEqualTo("input_required");
        assertThat(response.inputRequests()).containsEntry("city", "city name");
        assertThat(response.requestState()).isEqualTo("state-1");
        assertThat(response.error()).isFalse();
        assertThat(response.content()).isEmpty();
    }

    @Test
    @DisplayName("inputRequired 工厂：仅有 requestState 也合法")
    void inputRequiredShouldAcceptStateOnly() {
        McpToolResponse response = McpToolResponse.inputRequired(null, "state-1");

        assertThat(response.resultType()).isEqualTo("input_required");
        assertThat(response.inputRequests()).isEmpty();
        assertThat(response.requestState()).isEqualTo("state-1");
    }

    @Test
    @DisplayName("inputRequired 工厂：缺关键字段时抛 IllegalArgumentException")
    void inputRequiredShouldRejectEmptyBoth() {
        assertThatThrownBy(() -> McpToolResponse.inputRequired(null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inputRequests or requestState");
        assertThatThrownBy(() -> McpToolResponse.inputRequired(Map.of(), ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inputRequests or requestState");
    }

    @Test
    @DisplayName("非法 resultType 抛 IllegalArgumentException")
    void shouldRejectUnsupportedResultType() {
        assertThatThrownBy(() -> new McpToolResponse(List.of(), null, false, "partial", Map.of(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported MCP resultType");
    }

    @Test
    @DisplayName("resultType 为 null/空白时回落为 complete")
    void shouldFallbackResultTypeWhenBlank() {
        McpToolResponse r1 = new McpToolResponse(List.of(), null, false, null, Map.of(), null);
        McpToolResponse r2 = new McpToolResponse(List.of(), null, false, "  ", Map.of(), null);

        assertThat(r1.resultType()).isEqualTo("complete");
        assertThat(r2.resultType()).isEqualTo("complete");
    }

    @Test
    @DisplayName("content/structuredContent/inputRequests 都被不可变拷贝")
    void shouldDefensivelyCopyCollections() {
        List<Map<String, Object>> mutableContent = new ArrayList<>();
        mutableContent.add(new LinkedHashMap<>(Map.of("text", "v")));
        Map<String, Object> mutableInput = new LinkedHashMap<>(Map.of("k", "v"));

        McpToolResponse response = new McpToolResponse(
                mutableContent, "structured", false, "complete", mutableInput, null);

        mutableContent.clear();
        mutableInput.put("new", "x");

        assertThat(response.content()).hasSize(1);
        assertThat(response.inputRequests()).containsOnlyKeys("k");
        assertThatThrownBy(() -> response.content().add(Map.of()))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
