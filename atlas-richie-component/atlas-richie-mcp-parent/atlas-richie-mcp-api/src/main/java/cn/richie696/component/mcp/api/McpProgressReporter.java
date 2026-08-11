package cn.richie696.component.mcp.api;

/**
 * 业务代码使用的进度回报端口。
 *
 * <p>Tool 执行为异步长任务时，需要向 Client 端反馈当前进度（百分比、消息、可选的总数），
 * 由底层传输层把进度事件转发给对端。{@code McpProgressReporter} 是这层解耦的抽象：
 * 业务代码只关心"我现在完成了多少"，不关心进度如何被序列化、通过 HTTP/SSE 还是
 * 其他通道发送。实现方会负责把进度事件路由到正确的调用方。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
@FunctionalInterface
public interface McpProgressReporter {
    /**
     * 静默丢弃所有进度事件的空对象，便于 {@link McpCallContext} 安全缺省回落。
     */
    McpProgressReporter NOOP = (progress, total, message) -> {
    };

    /**
     * 上报一次进度。
     *
     * @param progress 当前进度值，约定为 {@code [0.0, 1.0]} 区间（具体语义由实现约定）
     * @param total 总数，可为 {@code null} 表示未知（如无限流）
     * @param message 可读的进度说明，可为 {@code null}
     */
    void report(double progress, Double total, String message);
}
