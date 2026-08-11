package cn.richie696.component.mcp.server.tool;

/**
 * Tool 注册表变更监听器：在注册表原子替换完成且新状态对外可见后被调用。
 *
 * <p>作为 {@link McpToolRegistry} 事件总线的契约，监听器会在 CAS 成功之后、调用方
 * 拿到结果之前被同步通知（监听器抛出的异常会被注册表捕获并记录，不会影响业务调用）。
 * 适用于"工具集变更后通知 MCP 客户端刷新工具列表"等场景。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
@FunctionalInterface
public interface McpToolRegistryChangeListener {
    /**
     * 当注册表成功完成一次原子变更时被调用。
     *
     * @param result 本次变更的描述（版本号差异、增删改的 Tool 名称集合）
     */
    void onChanged(McpToolRefreshResult result);
}
