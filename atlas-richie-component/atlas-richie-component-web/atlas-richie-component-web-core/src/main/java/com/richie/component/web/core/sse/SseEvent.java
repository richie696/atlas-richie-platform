package com.richie.component.web.core.sse;

/**
 * SSE 事件数据模型（DESIGN §4.4 长连接支持）。
 * <p>
 * 对应 SSE 协议一行：{@code event: <name>\ndata: <data>\nid: <id>\nretry: <ms>}。
 * 业务方通过 {@link #builder()} 构造或静态工厂 {@link #of(Object)} / {@link #of(String, Object)}。
 *
 * @param name         事件名（{@code event:} 字段）；默认 {@code "message"}
 * @param data         事件数据（{@code data:} 字段）；任意 {@link Object}，由 Spring Jackson 序列化
 * @param id           事件 ID（{@code id:} 字段）；可选
 * @param reconnectMs  客户端重连间隔（{@code retry:} 字段）；可选，毫秒
 *
 * @author richie696
 * @since 2026-07
 */
public record SseEvent(String name, Object data, String id, Long reconnectMs) {

    public SseEvent {
        if (name == null || name.isBlank()) {
            name = "message";
        }
    }

    /**
     * 默认事件（{@code event: message}）。
     */
    public static SseEvent of(Object data) {
        return new SseEvent("message", data, null, null);
    }

    /**
     * 命名事件（{@code event: <name>}）。
     */
    public static SseEvent of(String name, Object data) {
        return new SseEvent(name, data, null, null);
    }

    /**
     * 心跳事件（{@code event: ping, data: "ping"}），用于穿透代理 idle timeout。
     */
    public static SseEvent ping() {
        return new SseEvent("ping", "ping", null, null);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String name = "message";
        private Object data;
        private String id;
        private Long reconnectMs;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder data(Object data) {
            this.data = data;
            return this;
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder reconnectMs(Long reconnectMs) {
            this.reconnectMs = reconnectMs;
            return this;
        }

        public SseEvent build() {
            return new SseEvent(name, data, id, reconnectMs);
        }
    }
}