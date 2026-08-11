package cn.richie696.component.mcp.api.server;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@link McpToolDefinitionChangeEvent} 紧凑构造器对 occurredAt=null 时的缺省回落。
 */
@DisplayName("McpToolDefinitionChangeEvent 定义变更事件 record")
class McpToolDefinitionChangeEventTest {

    @Test
    @DisplayName("occurredAt=null 时回落为当前时间")
    void shouldFallbackOccurredAtToNow() {
        Instant before = Instant.now();
        McpToolDefinitionChangeEvent event = new McpToolDefinitionChangeEvent(
                "source-a", "rev-1", null);
        Instant after = Instant.now();

        assertThat(event.occurredAt()).isBetween(before, after);
    }

    @Test
    @DisplayName("occurredAt 显式传入时被保留")
    void shouldPreserveExplicitOccurredAt() {
        Instant fixed = Instant.parse("2030-01-01T00:00:00Z");
        McpToolDefinitionChangeEvent event = new McpToolDefinitionChangeEvent(
                "source-a", "rev-1", fixed);

        assertThat(event.sourceId()).isEqualTo("source-a");
        assertThat(event.sourceRevision()).isEqualTo("rev-1");
        assertThat(event.occurredAt()).isEqualTo(fixed);
    }

    @Test
    @DisplayName("equals/hashCode：相同字段的两个 record 相等")
    void shouldRespectValueEquality() {
        Instant fixed = Instant.parse("2030-01-01T00:00:00Z");
        McpToolDefinitionChangeEvent a = new McpToolDefinitionChangeEvent("s", "r", fixed);
        McpToolDefinitionChangeEvent b = new McpToolDefinitionChangeEvent("s", "r", fixed);

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    }

    @Test
    @DisplayName("sourceRevision 可为任意字符串（含空）")
    void shouldAcceptAnyRevision() {
        McpToolDefinitionChangeEvent event = new McpToolDefinitionChangeEvent(
                "s", "", Instant.parse("2030-01-01T00:00:00Z"));

        assertThat(event.sourceRevision()).isEmpty();
    }
}
