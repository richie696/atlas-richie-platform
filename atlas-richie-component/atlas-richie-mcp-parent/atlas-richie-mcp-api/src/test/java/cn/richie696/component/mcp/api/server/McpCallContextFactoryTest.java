package cn.richie696.component.mcp.api.server;

import cn.richie696.component.mcp.api.McpCallContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@link McpCallContextFactory#anonymous()} 工厂的透传行为：tenant/subject 均为 anonymous，
 * 其它字段（protocolVersion/attributes/cancellationToken/progressReporter）从 request 复制。
 */
@DisplayName("McpCallContextFactory 业务上下文工厂")
class McpCallContextFactoryTest {

    @Test
    @DisplayName("anonymous()：tenant/subject 被硬编码为 anonymous")
    void anonymousShouldForceAnonymousIdentity() {
        McpCallContextFactory factory = McpCallContextFactory.anonymous();
        McpServerCallContextRequest request = new McpServerCallContextRequest(
                "req-1", "2025-11-25",
                Map.of("X-Tenant", List.of("ignored")),
                Map.of("traceId", "abc"),
                Instant.parse("2030-01-01T00:00:00Z"),
                null, null);

        McpCallContext context = factory.create(request);

        assertThat(context.requestId()).isEqualTo("req-1");
        assertThat(context.protocolVersion()).isEqualTo("2025-11-25");
        assertThat(context.tenantId()).isEqualTo("anonymous");
        assertThat(context.subject()).isEqualTo("anonymous");
        assertThat(context.deadline()).isEqualTo(Instant.parse("2030-01-01T00:00:00Z"));
        assertThat(context.attributes()).containsEntry("traceId", "abc");
    }

    @Test
    @DisplayName("anonymous()：可被业务 lambda 覆盖为真实实现")
    void shouldAllowCustomLambda() {
        McpCallContextFactory factory = request -> new McpCallContext(
                request.requestId(),
                request.protocolVersion(),
                "tenant-real",
                "alice",
                request.defaultDeadline(),
                request.attributes(),
                request.cancellationToken(),
                request.progressReporter());

        McpServerCallContextRequest request = new McpServerCallContextRequest(
                "r", "v", null, Map.of("k", "v"), null, null, null);
        McpCallContext context = factory.create(request);

        assertThat(context.tenantId()).isEqualTo("tenant-real");
        assertThat(context.subject()).isEqualTo("alice");
        assertThat(context.attributes()).containsEntry("k", "v");
    }
}
