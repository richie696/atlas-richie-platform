package cn.richie696.component.mcp.api;

/**
 * 与传输实现无关的协作式取消信号。
 *
 * <p>该接口为 MCP 体系提供统一的"取消"抽象：无论是 HTTP 请求被客户端断开、SSE 连接关闭，
 * 还是用户主动调用 abort，底层适配层都应将取消事件封装为 {@code McpCancellationToken} 并
 * 通过 {@link McpCallContext} 透传给业务实现。业务实现只需要在长循环/阻塞操作中定期
 * 询问 {@link #isCancellationRequested()}，或直接调用 {@link #throwIfCancellationRequested()}
 * 触发标准 {@link McpCallCancelledException}。</p>
 *
 * <p>采用协作式取消而非抢占式中断的原因：业务代码可能持有数据库连接、外部资源等不可中断状态，
 * 强制线程中断有资源泄漏风险；协作式取消把"是否终止"交给业务自身判断，更安全可控。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
@FunctionalInterface
public interface McpCancellationToken {
    /**
     * 永远不触发取消的空对象实例，便于在 {@link McpCallContext} 中安全缺省回落。
     */
    McpCancellationToken NONE = () -> false;

    /**
     * 询问当前调用是否已被请求取消。
     *
     * @return {@code true} 表示调用方希望中止本次调用，业务应尽快释放资源并抛出取消异常
     */
    boolean isCancellationRequested();

    /**
     * 若已请求取消，则抛出标准 {@link McpCallCancelledException}。
     *
     * <p>业务代码通常在循环入口或重计算前调用此方法，行为上等价于"快速失败检查"。</p>
     *
     * @throws McpCallCancelledException 当 {@link #isCancellationRequested()} 返回 {@code true} 时
     */
    default void throwIfCancellationRequested() {
        if (isCancellationRequested()) {
            throw new McpCallCancelledException();
        }
    }
}
