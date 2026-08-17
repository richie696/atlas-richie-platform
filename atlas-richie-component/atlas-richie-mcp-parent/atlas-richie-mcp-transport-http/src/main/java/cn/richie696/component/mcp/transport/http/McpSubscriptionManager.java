package cn.richie696.component.mcp.transport.http;

import cn.richie696.component.mcp.protocol.McpMetaKeys;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;

/**
 * 进程内订阅运行时，负责维护订阅登记表并按订阅 spec 决策性分发通知。
 * 真正的字节流/响应写入由 Web 传输适配器（Servlet/Reactive）拥有，本类只负责"准备事件"。
 *
 * <p>用例：
 * <ul>
 *   <li>客户端通过 {@code subscriptions/listen} 注册一条订阅，{@link McpServerHttpEndpoint}
 *       委派给 {@link #open(String, McpSubscriptionSpec)}。</li>
 *   <li>工具/资源/提示列表发生变化时，端点调用对应的 {@code xxxChanged()} 方法批量通知。</li>
 *   <li>具体资源更新时调用 {@link #resourceUpdated(String)}，只推送给订阅了对应 URI 的连接。</li>
 *   <li>Web 适配器将 {@link Subscription} 桥接到 {@code Flow.Subscriber} 完成字节流输出。</li>
 * </ul>
 *
 * <p>为何用 {@link SubmissionPublisher}：MCP 通知是简单的 producer/consumer 关系，与 JDK 的
 * {@link Flow} 编程模型完全契合；用 {@link java.util.concurrent.Flow.Publisher}
 * 也意味着任何遵循 Reactive Streams 的客户端（Reactor、RxJava 等）都能轻松对接。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
public final class McpSubscriptionManager {
    private final ConcurrentMap<String, Subscription> subscriptions = new ConcurrentHashMap<>();

    /**
     * 打开一条新订阅。
     *
     * <p>如果同一 {@code requestId} 重复注册则抛出 {@link IllegalArgumentException}，
     * 因为两个订阅共用一个 id 会导致通知路由含糊不清。</p>
     *
     * @param requestId 客户端在 {@code subscriptions/listen} 中传递的 id（一般是 JSON-RPC id）
     * @param spec      订阅要接收的通知类型
     * @return 已注册并可被订阅的 {@link Subscription}
     * @throws IllegalArgumentException {@code requestId} 已存在时
     */
    public Subscription open(String requestId, McpSubscriptionSpec spec) {
        Subscription subscription = new Subscription(requestId, spec);
        Subscription existing = subscriptions.putIfAbsent(requestId, subscription);
        if (existing != null) {
            throw new IllegalArgumentException("Duplicate MCP subscription request id: " + requestId);
        }
        return subscription;
    }

    /**
     * 向所有订阅了 {@code toolsListChanged} 的连接广播工具列表变更通知。
     */
    public void toolsChanged() {
        subscriptions.values().forEach(subscription -> {
            if (subscription.spec().toolsListChanged()) subscription.publish(notification(
                    "notifications/tools/list_changed", subscription.requestId(), Map.of()));
        });
    }

    /**
     * 向所有订阅了 {@code promptsListChanged} 的连接广播提示列表变更通知。
     */
    public void promptsChanged() {
        subscriptions.values().forEach(subscription -> {
            if (subscription.spec().promptsListChanged()) subscription.publish(notification(
                    "notifications/prompts/list_changed", subscription.requestId(), Map.of()));
        });
    }

    /**
     * 向所有订阅了 {@code resourcesListChanged} 的连接广播资源列表变更通知。
     */
    public void resourcesChanged() {
        subscriptions.values().forEach(subscription -> {
            if (subscription.spec().resourcesListChanged()) subscription.publish(notification(
                    "notifications/resources/list_changed", subscription.requestId(), Map.of()));
        });
    }

    /**
     * 推送单个资源的更新通知；只有显式订阅了该 URI 的连接会收到。
     *
     * @param uri 被更新的资源 URI
     */
    public void resourceUpdated(String uri) {
        subscriptions.values().forEach(subscription -> {
            if (subscription.spec().resourceSubscriptions().contains(uri)) {
                subscription.publish(notification("notifications/resources/updated",
                        subscription.requestId(), Map.of("uri", uri)));
            }
        });
    }

    /**
     * 主动关闭一条订阅，移除注册表项并向 SSE 输出发布最终完成帧。
     *
     * @param requestId 客户端在打开订阅时使用的 id
     */
    public void close(String requestId) {
        Subscription subscription = subscriptions.remove(requestId);
        if (subscription != null) subscription.close();
    }

    /**
     * 构造符合 MCP 协议的 JSON-RPC 通知 envelope（注意通知没有 {@code id}）。
     *
     * <p>{@code _meta.subscriptionId} 是协议规定的关联标识，客户端据此识别通知来自哪条订阅。</p>
     *
     * @param method    通知的 JSON-RPC method 名
     * @param requestId 订阅 id，会被写入 {@code _meta.subscriptionId}
     * @param params    不含 {@code _meta} 的通知参数
     * @return 已组装的不可变 JSON-RPC 通知 map
     */
    private Map<String, Object> notification(String method, String requestId, Map<String, Object> params) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(McpMetaKeys.SUBSCRIPTION_ID, requestId);
        Map<String, Object> payload = new LinkedHashMap<>(params);
        payload.put("_meta", metadata);
        return Map.of("jsonrpc", "2.0", "method", method, "params", payload);
    }

    /**
     * 一条具体订阅实例：既是 Response body，又能桥接到 Reactive Subscriber。
     *
     * <p>实现 {@link Flow.Publisher} 而非暴露 {@link SubmissionPublisher} 是因为后者无法携带
     * 请求上下文（{@code requestId} / {@code spec}），本类同时持有这两个信息和事件流，
     * 适配器只需拿到一个对象即可统一处理订阅关闭、消息分发等。</p>
     */
    public final class Subscription implements Flow.Publisher<Map<String, Object>> {
        private final String requestId;
        private final McpSubscriptionSpec spec;
        private final SubmissionPublisher<Map<String, Object>> publisher = new SubmissionPublisher<>();

        private Subscription(String requestId, McpSubscriptionSpec spec) {
            this.requestId = requestId;
            this.spec = spec;
        }

        /**
         * @return 与订阅对应的 JSON-RPC request id
         */
        public String requestId() {
            return requestId;
        }

        /**
         * @return 订阅的过滤器，决定哪些通知会推送到此订阅
         */
        public McpSubscriptionSpec spec() {
            return spec;
        }

        /**
         * 主动向关联的订阅者发布一条事件。
         *
         * @param event 已组装好的 JSON-RPC 通知 envelope
         */
        public void publish(Map<String, Object> event) {
            publisher.submit(event);
        }

        /**
         * 释放订阅：先从注册表中删除自己，再写入协议规定的"complete"完成帧，
         * 最后关闭下游的发布器，所有 Subscriber 将在反压消费完后收到 onComplete。
         * 设计成幂等：连续调用 {@code close()} 是安全的（注册表 only-if-equal 删除）。
         */
        public void close() {
            subscriptions.remove(requestId, this);
            publisher.submit(Map.of(
                    "jsonrpc", "2.0",
                    "id", requestId,
                    "result", Map.of(
                            "resultType", "complete",
                            "_meta", Map.of(McpMetaKeys.SUBSCRIPTION_ID, requestId))));
            publisher.close();
        }

        /**
         * 把字节流订阅代理到底层 {@link SubmissionPublisher}。
         *
         * @param subscriber 真正把事件写到 HTTP 通道的订阅者
         */
        @Override
        public void subscribe(Flow.Subscriber<? super Map<String, Object>> subscriber) {
            publisher.subscribe(subscriber);
        }
    }
}
