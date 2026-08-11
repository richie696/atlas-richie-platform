package cn.richie696.component.mcp.api;

/**
 * 表示一次 MCP 调用被对端协议或本地调用方主动取消。
 *
 * <p>该异常是 {@link McpException} 的特化子类，使用统一错误码 {@code MCP_CALL_CANCELLED}，
 * 与"业务执行失败"在错误分类上保持隔离：业务监控/告警系统在统计 Tool 失败率时可以
 * 通过异常类型直接区分"用户主动取消"与"真实错误"，避免取消行为被误报为业务异常。</p>
 *
 * <p>典型触发场景：</p>
 * <ul>
 *   <li>Client 通过 {@link McpCancellationToken} 主动请求中断；</li>
 *   <li>Server 端由于 deadline 到期或资源回收，主动放弃当前调用；</li>
 *   <li>底层传输（HTTP/SSE/Stdio）连接被关闭。</li>
 * </ul>
 *
 * @author richie696
 * @since 2026-08-11
 */
public final class McpCallCancelledException extends McpException {
    /**
     * 使用固定错误码 {@code MCP_CALL_CANCELLED} 与默认英文消息构造取消异常。
     */
    public McpCallCancelledException() {
        super("MCP_CALL_CANCELLED", "MCP call was cancelled");
    }
}
