package cn.richie696.component.mcp.transport.http;

import java.util.Map;
import java.util.List;

/**
 * 由 MCP 端点适配器输出的框架无关 HTTP 响应。
 *
 * <p>对端点而言，本 record 是协议逻辑的"出口描述"：任意 Web 容器适配器只需把这里的状态码、内容类型、
 * 主体、可选的通知列表映射到自己的响应对象即可。{@code body} 的实际类型为 {@link Map}（JSON-RPC envelope）
 * 或 {@link McpSubscriptionManager.Subscription}（SSE 长连接），由 {@code contentType} 字段决定具体
 * 后续处理路径。</p>
 *
 * <p>为何需要在同一 record 中同时容纳"普通 HTTP"和"SSE 流"两种语义：现代 MCP 协议允许单次
 * 请求在同步返回 result 之前以 SSE 形式推送 progress 通知，本 record 通过将 {@code notifications} 列表
 * 与 {@code body} 并列，让上层适配器一次性把同步响应与流式事件一起回写到客户端（通常的做法是
 * 显式产物后接续 SSE 帧）。</p>
 *
 * @param status        HTTP 状态码
 * @param contentType   响应 Content-Type；{@code null} 表示 202 Accepted 无内容
 * @param body          响应主体；普通请求为 {@link Map}，SSE 订阅场景为 {@link McpSubscriptionManager.Subscription}
 * @param notifications 与 body 一起回传的服务器主动通知（progress 等）
 * @author richie696
 * @since 2026-08-11
 */
public record McpHttpResponse(int status, String contentType, Object body, List<Map<String, Object>> notifications) {
    /**
     * 无通知列表的便捷构造重载。
     *
     * @param status      HTTP 状态码
     * @param contentType 响应 Content-Type
     * @param body        响应主体
     */
    public McpHttpResponse(int status, String contentType, Object body) {
        this(status, contentType, body, List.of());
    }

    /**
     * 规范化 {@code notifications} 为不可变列表；{@code null} 替换为空列表，传递给 {@link List#copyOf}。
     */
    public McpHttpResponse {
        notifications = notifications == null ? List.of() : List.copyOf(notifications);
    }

    /**
     * 构造 {@code application/json} 普通 JSON 响应。
     *
     * @param status HTTP 状态码
     * @param body   作为 JSON 序列化的主体
     * @return 仅含 JSON 主体、无 SSE 通知的响应
     */
    public static McpHttpResponse json(int status, Object body) {
        return new McpHttpResponse(status, "application/json", body);
    }

    /**
     * 构造 {@code 202 Accepted} 无内容响应，用于 JSON-RPC 通知（{@code notifications/cancelled} 等）或
     * 已经交由流推送主结果的订阅确认。
     *
     * @return 状态码 202、Content-Type 与 body 为 null 的空响应
     */
    public static McpHttpResponse accepted() {
        return new McpHttpResponse(202, null, null);
    }

    /**
     * 构造 SSE 长连接响应，同步主体（{@code body} 通常为 {@link McpSubscriptionManager.Subscription}）
     * 会由 Web 适配器持续写出，{@code notifications} 在响应关闭前下发到 SSE 通道。
     *
     * @param status        HTTP 状态码（通常 200）
     * @param body          主响应（SSE 场景下通常为订阅发布器）
     * @param notifications 启动阶段一次性下发的通知列表
     * @return Content-Type 为 {@code text/event-stream} 的 SSE 响应
     */
    public static McpHttpResponse sse(
            int status,
            Object body,
            List<Map<String, Object>> notifications) {
        return new McpHttpResponse(status, "text/event-stream", body, notifications);
    }

    /**
     * 指示当前响应是否包含需要写入客户端的主体。{@code null} body 表示无内容（如 202）。
     *
     * @return {@code true} 表示 body 不为空
     */
    public boolean hasBody() {
        return body != null;
    }
}
