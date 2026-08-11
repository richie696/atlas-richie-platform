package cn.richie696.component.mcp.transport.http;

import cn.richie696.component.mcp.api.McpCancellationToken;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 桥接 JSON-RPC {@code notifications/cancelled} 与正在执行的请求令牌。
 *
 * <p>现代 MCP 协议允许客户端随时通过 {@code notifications/cancelled} 异步终止一个未完成请求，
 * 而服务端在执行 {@code tools/call} 等可能耗时较长的调用时，需要有一种机制把客户端通知
 * 反向传播到调用栈中通过 {@link McpCancellationToken} 协作轮询的代码。本类就是这条反向桥：
 * 注册表以 {@code requestId} 为主键，每个请求开始时注册一个 {@link AtomicBoolean}，
 * 结束时移除；{@link #cancel(Object)} 由端点的 {@code notifications/cancelled} 处理器调用，
 * 设置对应标志位；正在执行的工具调用周期性调用 {@link McpCancellationToken} 接口即可感知。</p>
 *
 * <p>设计要点：使用 {@link ConcurrentHashMap} 是因为请求开始/取消/完成分别发生在不同线程
 * （HTTP 工作线程、JSON-RPC 分发线程、工具执行线程）；使用 {@link AtomicBoolean}
 * 是因为在写-读并发下保证可见性；不在 {@link #finish(String)} 阶段显式关闭令牌，
 * 而是把"是否被取消"的判断责任留给调用方，是为了让该类保持简单且不持有工具的回调闭包。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
public final class McpCancellationRegistry {
    private final ConcurrentMap<String, AtomicBoolean> requests = new ConcurrentHashMap<>();

    /**
     * 注册一个新请求，并返回一个 {@link McpCancellationToken} 给工具调用方协作轮询。
     *
     * <p>同一个 {@code requestId} 不允许重复注册；但本方法并不主动检测冲突，
     * 冲突的语义由调用方根据业务决定（一般由 JSON-RPC 编号唯一性保证）。</p>
     *
     * @param requestId JSON-RPC 请求 id（即 {@code McpJsonRpcRequest.id} 的字符串形式）
     * @return 一个在外部线程调用 {@link #cancel(Object)} 后立即返回 {@code true} 的取消令牌
     */
    public McpCancellationToken begin(String requestId) {
        AtomicBoolean cancelled = new AtomicBoolean();
        requests.put(requestId, cancelled);
        return cancelled::get;
    }

    /**
     * 把外部的 {@code notifications/cancelled} 通知反转到注册表中对应请求的取消标志位。
     *
     * <p>方法接收 {@link Object} 是因为协议允许请求 id 为 String、Number 或 null；
     * 对 null 直接忽略——null id 的 JSON-RPC 请求通常意味着广播式通知，本就不应取消具体任务。</p>
     *
     * @param requestId 客户端在 {@code params.requestId} 中携带的 id，可以是 null
     */
    public void cancel(Object requestId) {
        if (requestId != null) {
            AtomicBoolean cancelled = requests.get(String.valueOf(requestId));
            if (cancelled != null) cancelled.set(true);
        }
    }

    /**
     * 请求生命周期结束时清理注册表，释放对 {@link AtomicBoolean} 的引用，避免内存泄漏。
     *
     * <p>建议放在 {@code try-finally} 的 finally 分支中执行，确保即使工具执行抛出异常也能回收。</p>
     *
     * @param requestId 与 {@link #begin(String)} 传入相同的请求 id
     */
    public void finish(String requestId) {
        requests.remove(requestId);
    }
}
