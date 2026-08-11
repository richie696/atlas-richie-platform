package cn.richie696.component.mcp.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 {@link McpCallContext} 紧凑构造器的不可变包装与缺省回落行为，
 * 以及 {@link McpCallContext#optionalDeadline()} 在有/无 deadline 时的不同表现。
 */
@DisplayName("McpCallContext 横切上下文 record")
class McpCallContextTest {

    @Test
    @DisplayName("完整参数：访问器返回原值并对 attributes 做不可变拷贝")
    void shouldExposeAllFieldsAndImmutableAttributes() {
        Map<String, Object> raw = Map.of("traceId", "abc", "channel", "web");
        McpCancellationToken cancellable = () -> true;
        McpProgressReporter reporter = (p, t, m) -> { };
        McpCallContext context = new McpCallContext(
                "req-1", "2025-11-25", "tenant-a", "alice",
                Instant.parse("2030-01-01T00:00:00Z"),
                raw, cancellable, reporter);

        assertThat(context.requestId()).isEqualTo("req-1");
        assertThat(context.protocolVersion()).isEqualTo("2025-11-25");
        assertThat(context.tenantId()).isEqualTo("tenant-a");
        assertThat(context.subject()).isEqualTo("alice");
        assertThat(context.deadline()).isEqualTo(Instant.parse("2030-01-01T00:00:00Z"));
        assertThat(context.attributes())
                .containsExactlyInAnyOrderEntriesOf(raw)
                .isUnmodifiable();
        assertThat(context.cancellationToken()).isSameAs(cancellable);
        assertThat(context.progressReporter()).isSameAs(reporter);
    }

    @Test
    @DisplayName("缺省回落：cancellationToken 与 progressReporter 在 null 时回落到 NONE/NOOP")
    void shouldFallbackToNoopInstances() {
        McpCallContext context = new McpCallContext(
                "req", "v1", "t", "s", null, Map.of(), null, null);

        assertThat(context.cancellationToken().isCancellationRequested()).isFalse();
        assertThat(context.progressReporter()).isSameAs(McpProgressReporter.NOOP);
        assertThat(context.attributes()).isEmpty();
    }

    @Test
    @DisplayName("attributes 为 null 时回落到空不可变 Map")
    void shouldFallbackAttributesToEmptyMap() {
        McpCallContext context = new McpCallContext(
                "req", "v1", "t", "s", null, null, null, null);

        assertThat(context.attributes()).isEmpty();
    }

    @Test
    @DisplayName("protocolVersion 为 null 时抛 NullPointerException")
    void shouldRejectNullProtocolVersion() {
        assertThatThrownBy(() -> new McpCallContext(
                "req", null, "t", "s", null, Map.of(), null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("protocolVersion");
    }

    @Test
    @DisplayName("optionalDeadline：无 deadline 时返回空 Optional")
    void shouldReturnEmptyOptionalWhenDeadlineIsNull() {
        McpCallContext context = new McpCallContext(
                "req", "v1", "t", "s", null, Map.of(), null, null);

        assertThat(context.optionalDeadline()).isEmpty();
    }

    @Test
    @DisplayName("optionalDeadline：有 deadline 时返回带值 Optional")
    void shouldWrapDeadlineInOptional() {
        Instant deadline = Instant.parse("2030-06-01T00:00:00Z");
        McpCallContext context = new McpCallContext(
                "req", "v1", "t", "s", deadline, Map.of(), null, null);

        assertThat(context.optionalDeadline()).contains(deadline);
    }

    @Test
    @DisplayName("equals/hashCode：相同字段的两个 record 相等")
    void shouldRespectValueEquality() {
        McpCallContext a = new McpCallContext("r", "v", "t", "s", null, Map.of("k", 1), null, null);
        McpCallContext b = new McpCallContext("r", "v", "t", "s", null, Map.of("k", 1), null, null);
        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    }
}
