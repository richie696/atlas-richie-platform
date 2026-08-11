package cn.richie696.component.mcp.api.server;

import java.time.Instant;

/**
 * Signals that one definition source has new content available.
 *
 * <p>{@link McpToolDefinitionSource} 在内容变更时发布该事件，框架侧订阅后触发
 * {@code McpToolDefinition} 全量重算与缓存失效。这套"事件 + 修订号"机制使运行时
 * 可以无侵入地接入外部源（Nacos/Apollo/数据库等），不需要把 {@code atlas-richie-mcp-server}
 * 与具体配置中心耦合。</p>
 *
 * @param sourceId 来源标识
 * @param sourceRevision 来源版本号
 * @param occurredAt 事件发生时间，缺省回落为 {@link Instant#now()}
 * @author richie696
 * @since 2026-08-11
 */
public record McpToolDefinitionChangeEvent(
        String sourceId,
        String sourceRevision,
        Instant occurredAt) {

    /**
     * 紧凑构造器：未提供发生时间时回落为当前时间，便于业务侧不显式传 {@code now()}。
     *
     * @param sourceId 来源标识
     * @param sourceRevision 来源版本号
     * @param occurredAt 事件发生时间
     */
    public McpToolDefinitionChangeEvent {
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
    }
}
