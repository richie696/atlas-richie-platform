package cn.richie696.component.mcp.api.server;

/**
 * Adapter implemented by the platform or business audit component.
 *
 * <p>{@code McpToolAuditSink} 是审计事件投递的扩展点：{@code atlas-richie-mcp-server} 在
 * 每次 Tool 调用结束后构造 {@link McpToolAuditEvent} 并调用 {@link #record(McpToolAuditEvent)}，
 * 业务侧可以接入到 ELK/Kafka/自建审计存储等任意后端。函数式接口签名让"按需覆盖 + Spring 注入"
 * 即可生效，无需新增注解或配置。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
@FunctionalInterface
public interface McpToolAuditSink {
    /**
     * 投递一条 Tool 调用审计事件。
     *
     * <p>实现方应保证该方法的非阻塞性——若写入慢存储，应自行切换到异步队列，避免阻塞
     * Tool 调用的主链路。</p>
     *
     * @param event 审计事件（含已脱敏的入参）
     */
    void record(McpToolAuditEvent event);
}
