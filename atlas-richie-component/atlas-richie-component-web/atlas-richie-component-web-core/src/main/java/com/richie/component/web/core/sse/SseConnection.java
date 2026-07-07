package com.richie.component.web.core.sse;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SSE 连接包装（DESIGN §4.4）。
 * <p>
 * 持有 {@link SseEmitter} + 元数据（tags / createdAt / lastHeartbeatAt），
 * 供 {@link SseManager} 在内部 map 中跟踪每个活跃连接。
 *
 * @param clientId        业务侧客户端唯一 ID（必填）
 * @param emitter         底层 Spring SseEmitter（必填）
 * @param createdAt       连接建立时刻
 * @param tags            业务标签集合（线程安全；用于 broadcastByTag）
 * @param lastHeartbeatAt 最近一次成功心跳的 epoch millis（用于 stale 检测）
 *
 * @author richie696
 * @since 2026-07
 */
public record SseConnection(String clientId,
                            SseEmitter emitter,
                            Instant createdAt,
                            Set<String> tags,
                            AtomicLong lastHeartbeatAt) {

    public SseConnection(String clientId, SseEmitter emitter) {
        this(clientId, emitter, Instant.now(),
                ConcurrentHashMap.newKeySet(),
                new AtomicLong(System.currentTimeMillis()));
    }

    public void tag(String tag) {
        tags.add(tag);
    }

    public void untag(String tag) {
        tags.remove(tag);
    }

    public boolean hasTag(String tag) {
        return tags.contains(tag);
    }

    public void touch() {
        lastHeartbeatAt.set(System.currentTimeMillis());
    }
}