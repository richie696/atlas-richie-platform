package cn.richie696.component.mcp.api.server;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@link McpToolAuditSink} 作为函数式接口可被 lambda 直接实现并被 record 投递，
 * 同时验证 {@link McpToolAuditEvent} 对 arguments 的不可变拷贝。
 */
@DisplayName("McpToolAuditSink 审计事件投递端口 + McpToolAuditEvent record")
class McpToolAuditSinkTest {

    @Test
    @DisplayName("lambda 实现：record 接收事件后持有引用")
    void shouldReceiveEvent() {
        AtomicReference<McpToolAuditEvent> captured = new AtomicReference<>();
        McpToolAuditSink sink = captured::set;

        McpToolAuditEvent event = new McpToolAuditEvent(
                "customer.lookup", "req-1", "tenant-a", "alice",
                java.time.Instant.parse("2030-01-01T00:00:00Z"),
                java.time.Duration.ofMillis(120),
                true, null, Map.of("customerId", "c-1"));
        sink.record(event);

        assertThat(captured.get()).isSameAs(event);
        assertThat(captured.get().toolName()).isEqualTo("customer.lookup");
        assertThat(captured.get().successful()).isTrue();
    }

    @Test
    @DisplayName("McpToolAuditEvent 暴露全部字段")
    void auditEventShouldExposeAllFields() {
        McpToolAuditEvent event = new McpToolAuditEvent(
                "t", "r", "tenant", "subject",
                java.time.Instant.parse("2030-01-01T00:00:00Z"),
                java.time.Duration.ofMillis(50),
                false, "MCP_TOOL_EXECUTION_ERROR", Map.of("k", "v"));

        assertThat(event.toolName()).isEqualTo("t");
        assertThat(event.requestId()).isEqualTo("r");
        assertThat(event.tenantId()).isEqualTo("tenant");
        assertThat(event.subject()).isEqualTo("subject");
        assertThat(event.startedAt()).isEqualTo(java.time.Instant.parse("2030-01-01T00:00:00Z"));
        assertThat(event.duration()).isEqualTo(java.time.Duration.ofMillis(50));
        assertThat(event.successful()).isFalse();
        assertThat(event.errorCode()).isEqualTo("MCP_TOOL_EXECUTION_ERROR");
        assertThat(event.arguments()).containsEntry("k", "v");
    }

    @Test
    @DisplayName("McpToolAuditEvent.arguments 不可变：外部突变不影响内部")
    void shouldDefensivelyCopyArguments() {
        Map<String, Object> mutable = new LinkedHashMap<>(Map.of("k", "v"));
        McpToolAuditEvent event = new McpToolAuditEvent(
                "t", "r", "t", "s",
                java.time.Instant.now(), java.time.Duration.ZERO,
                true, null, mutable);

        mutable.put("sneaky", "v");

        assertThat(event.arguments()).containsOnlyKeys("k");
    }

    @Test
    @DisplayName("McpToolAuditEvent.arguments 为 null 时回落到不可变空 Map")
    void shouldFallbackNullArguments() {
        McpToolAuditEvent event = new McpToolAuditEvent(
                "t", "r", "t", "s",
                java.time.Instant.now(), java.time.Duration.ZERO,
                true, null, null);

        assertThat(event.arguments()).isEmpty();
    }

    @Test
    @DisplayName("自定义实现：可累计多条事件（模拟批量审计）")
    void shouldAllowBatching() {
        java.util.List<McpToolAuditEvent> events = new ArrayList<>();
        McpToolAuditSink sink = events::add;

        sink.record(new McpToolAuditEvent(
                "a", "r", "t", "s",
                java.time.Instant.now(), java.time.Duration.ZERO, true, null, Map.of()));
        sink.record(new McpToolAuditEvent(
                "b", "r", "t", "s",
                java.time.Instant.now(), java.time.Duration.ZERO, true, null, Map.of()));

        assertThat(events).hasSize(2);
    }
}
