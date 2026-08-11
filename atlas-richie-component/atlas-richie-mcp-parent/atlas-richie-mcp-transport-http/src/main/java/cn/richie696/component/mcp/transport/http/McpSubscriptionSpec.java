package cn.richie696.component.mcp.transport.http;

import java.util.Set;

/**
 * 描述一次 MCP 订阅感兴趣的服务器通知类型，驱动 {@link McpSubscriptionManager} 选择性分发。
 *
 * <p>代表客户端在 {@code subscriptions/listen} 中声明的兴趣列表：哪些"列表变更"通知该推上来，
 * 哪些具体资源的更新通知要发到这条订阅。一个订阅只会接收到与之 spec 匹配的通知，
 * 避免 N 个订阅对同一变更广播 N 次的资源浪费。</p>
 *
 * <p>{@link #resourceSubscriptions} 使用 {@link Set} 而非 {@link java.util.List} 是因为同一 URI
 * 多次出现在订阅里对客户端而言是冗余的；构造时通过 {@link Set#copyOf} 保证不可变以便在并发
 * 遍历期间安全共享。</p>
 *
 * @param toolsListChanged         是否订阅 {@code notifications/tools/list_changed}
 * @param promptsListChanged       是否订阅 {@code notifications/prompts/list_changed}
 * @param resourcesListChanged     是否订阅 {@code notifications/resources/list_changed}
 * @param resourceSubscriptions    关心的具体资源 URI 集合，驱动 {@code notifications/resources/updated} 的定向推送
 * @author richie696
 * @since 2026-08-11
 */
public record McpSubscriptionSpec(
        boolean toolsListChanged,
        boolean promptsListChanged,
        boolean resourcesListChanged,
        Set<String> resourceSubscriptions) {
    /**
     * 规范化 {@link #resourceSubscriptions} 为不可变 Set；{@code null} 替换为 {@link Set#of()}。
     */
    public McpSubscriptionSpec {
        resourceSubscriptions = resourceSubscriptions == null ? Set.of() : Set.copyOf(resourceSubscriptions);
    }
}
