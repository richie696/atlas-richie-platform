package cn.richie696.component.mcp.client.spring.boot;

/**
 * MCP 客户端缓存失效的对外 SPI，专供 list_changed / resource_updated 等通知消费者主动清理缓存。
 *
 * <p>之所以单独抽出该接口：(1) 协议层只关心"何时清缓存"，实现层 {@link McpHttpOperations} 持有多种缓存（结果缓存 + 协议 Era 缓存），
 * 通过该 SPI 暴露统一入口；(2) 业务方可以注入此 SPI 实现自定义失效策略（如批量失效 + 异步通知）。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
public interface McpClientCacheControl {
    /**
     * 失效指定 server 的全部缓存家族。
     *
     * @param serverId 配置中的 server ID；为 {@code null} 或空白时不执行任何操作
     */
    void invalidateServerCache(String serverId);

    /**
     * 清空全部缓存，常用于运维侧的强制刷新或单元测试间重置。
     */
    void clearCaches();
}
