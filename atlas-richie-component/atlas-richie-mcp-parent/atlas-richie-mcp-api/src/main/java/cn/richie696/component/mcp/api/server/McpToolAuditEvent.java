package cn.richie696.component.mcp.api.server;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Sanitized audit event emitted for an audit-enabled Tool invocation.
 *
 * <p>当 {@link cn.richie696.component.mcp.api.annotation.McpTool#audit()} 为 {@code true}
 * （或部署配置覆盖开启）时，框架在每次 Tool 调用结束后向 {@link McpToolAuditSink} 投递该事件。
 * 事件中的 {@code arguments} 已被按 {@code @McpArgument(sensitive=true)} 标记脱敏，
 * 因此下游审计系统可以安全地把整条事件写入审计存储而不必担心敏感数据泄漏。</p>
 *
 * @param toolName Tool 业务名
 * @param requestId 请求 ID（链路追踪用）
 * @param tenantId 租户
 * @param subject 主体
 * @param startedAt 起始时间
 * @param duration 执行耗时
 * @param successful 是否成功
 * @param errorCode 失败错误码（成功时为 {@code null}）
 * @param arguments 已脱敏的入参
 * @author richie696
 * @since 2026-08-11
 */
public record McpToolAuditEvent(
        String toolName,
        String requestId,
        String tenantId,
        String subject,
        Instant startedAt,
        Duration duration,
        boolean successful,
        String errorCode,
        Map<String, Object> arguments) {

    /**
     * 紧凑构造器：对入参 Map 做防御性不可变拷贝。
     *
     * @param toolName Tool 业务名
     * @param requestId 请求 ID
     * @param tenantId 租户
     * @param subject 主体
     * @param startedAt 起始时间
     * @param duration 耗时
     * @param successful 是否成功
     * @param errorCode 错误码
     * @param arguments 入参
     */
    public McpToolAuditEvent {
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
    }
}
