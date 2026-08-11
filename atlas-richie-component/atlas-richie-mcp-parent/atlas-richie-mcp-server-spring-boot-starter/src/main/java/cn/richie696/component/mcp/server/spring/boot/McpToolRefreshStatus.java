package cn.richie696.component.mcp.server.spring.boot;

import java.time.Instant;

/**
 * 最近一次 MCP Tool 定义刷新结果快照。
 *
 * <p>由 {@link McpSpringToolDefinitionRefresher} 维护，可通过
 * {@link McpSpringToolDefinitionRefresher#status()} 拉取，用于运维查询与健康检查端点。</p>
 *
 * @param successful 是否成功完成刷新
 * @param registryRevision 刷新后注册表的单调递增版本号；调用方可通过版本比对检测配置变更
 * @param attemptedAt 本次刷新尝试的开始时间戳（UTC）
 * @param failureMessage 失败原因摘要；成功时为 {@code null}
 *
 * @author richie696
 * @since 2026-08-11
 */
public record McpToolRefreshStatus(
        boolean successful,
        long registryRevision,
        Instant attemptedAt,
        String failureMessage) {
}
