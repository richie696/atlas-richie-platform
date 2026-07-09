/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.web.core.sse;

import com.richie.component.web.core.metrics.WebMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Server-Sent Events 管理器（DESIGN §4.4 长连接支持）。
 *
 * <h2>设计原则</h2>
 * <ul>
 *   <li><strong>Spring bean + 注入</strong>：不再是静态单例；通过 {@code @Autowired SseManager} 注入，单元测试友好</li>
 *   <li><strong>生命周期自动化</strong>：onCompletion / onTimeout / onError / send 失败都自动 cleanup，业务方无需手动 remove</li>
 *   <li><strong>Tag 索引</strong>：支持 {@code broadcastByTag(tag, event)} 按业务标签定向推送</li>
 *   <li><strong>心跳</strong>：后台 {@link ScheduledExecutorService} 每 {@link #heartbeatInterval} 发 {@code event: ping}，穿透代理 idle timeout</li>
 *   <li><strong>优雅停机</strong>：{@link SmartLifecycle#stop()} 自动 complete 所有 emitter + shutdown executor</li>
 *   <li><strong>可观测性</strong>：通过 {@link WebMetrics} 暴露 connections gauge / send counter / disconnect counter</li>
 * </ul>
 *
 * <h2>典型用法</h2>
 * <pre>{@code
 * @RestController
 * class NotificationController {
 *     private final SseManager sse;
 *     NotificationController(SseManager sse) { this.sse = sse; }
 *
 *     @GetMapping(path = "/sse/notifications/{clientId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
 *     SseEmitter connect(@PathVariable String clientId) {
 *         return sse.connect(clientId);    // 自动注册生命周期回调 + 心跳
 *     }
 * }
 *
 * @Service
 * class NotificationService {
 *     void publishToUser(String userId, Notification n) {
 *         sse.send(userId, SseEvent.of(n));   // 单点推送
 *     }
 *     void broadcast(Notification n) {
 *         sse.broadcast(SseEvent.of(n));      // 全员推送
 *     }
 *     void publishToOrg(String orgId, Notification n) {
 *         sse.broadcastByTag(orgId, SseEvent.of(n));   // 按 tag 推送
 *     }
 * }
 * }</pre>
 *
 * <h2>多实例部署</h2>
 * <p>SseEmitter 不可序列化（绑定 servlet 容器 AsyncContext），多实例部署需引入 Redis Pub/Sub / MQ 广播 ClientID，
 * 由目标实例消费后调 {@link #send(String, SseEvent)}。当前实现仅支持单实例。
 *
 * @author richie696
 * @since 2026-07
 */
public class SseManager implements SmartLifecycle, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(SseManager.class);

    /** 默认 emitter 超时（1 小时）。 */
    static final Duration DEFAULT_TIMEOUT = Duration.ofHours(1);
    /** 默认心跳间隔（15 秒），远小于常见代理 idle timeout（60s）。 */
    static final Duration DEFAULT_HEARTBEAT_INTERVAL = Duration.ofSeconds(15);

    private final ConcurrentMap<String, SseConnection> connections = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Set<String>> tagIndex = new ConcurrentHashMap<>();
    private final WebMetrics metrics;
    private final Duration timeout;
    private final Duration heartbeatInterval;
    private final ScheduledExecutorService heartbeatExecutor;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile ScheduledFuture<?> heartbeatTask;

    public SseManager() {
        this(WebMetrics.noop());
    }

    public SseManager(WebMetrics metrics) {
        this(metrics, DEFAULT_TIMEOUT, DEFAULT_HEARTBEAT_INTERVAL);
    }

    /** 主构造器（测试可见）。 */
    SseManager(WebMetrics metrics, Duration timeout, Duration heartbeatInterval) {
        this.metrics = metrics == null ? WebMetrics.noop() : metrics;
        this.timeout = timeout == null ? DEFAULT_TIMEOUT : timeout;
        this.heartbeatInterval = heartbeatInterval == null ? DEFAULT_HEARTBEAT_INTERVAL : heartbeatInterval;
        this.heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "richie-sse-heartbeat");
            t.setDaemon(true);
            return t;
        });
    }

    // ──────────────────────────── 公开 API ────────────────────────────

    /**
     * 注册 SSE 连接，返回 Spring {@link SseEmitter} 供 controller 直接 return。
     * <p>自动注册 onCompletion / onTimeout / onError 回调，连接关闭时自动从 map 移除。
     */
    public SseEmitter connect(String clientId) {
        Objects.requireNonNull(clientId, "clientId must not be null");
        SseEmitter emitter = new SseEmitter(timeout.toMillis());
        SseConnection conn = new SseConnection(clientId, emitter);
        // 注册生命周期回调（注意顺序：先 put 进 map，回调里 remove）
        connections.put(clientId, conn);
        emitter.onCompletion(() -> removeConnection(clientId, DisconnectReason.COMPLETION));
        emitter.onTimeout(() -> removeConnection(clientId, DisconnectReason.TIMEOUT));
        emitter.onError(t -> removeConnection(clientId, DisconnectReason.ERROR));
        metrics.sseConnected();
        log.debug("SSE connected: clientId={} timeoutMs={}", clientId, timeout.toMillis());
        return emitter;
    }

    /**
     * 发送事件给指定客户端。失败（emitter 已关闭 / 网络断）自动清理 dead emitter。
     *
     * @return true=成功发送；false=clientId 不存在 或 发送失败
     */
    public boolean send(String clientId, SseEvent event) {
        SseConnection conn = connections.get(clientId);
        if (conn == null) {
            metrics.sseSend("miss");
            return false;
        }
        try {
            var builder = SseEmitter.event().name(event.name());
            if (event.data() != null) {
                builder.data(event.data());
            }
            if (event.id() != null) {
                builder.id(event.id());
            }
            if (event.reconnectMs() != null) {
                builder.reconnectTime(event.reconnectMs());
            }
            conn.emitter().send(builder);
            conn.touch();
            metrics.sseSend("success");
            return true;
        } catch (Exception ex) {
            log.warn("SSE send failed: clientId={} cause={}", clientId, ex.toString());
            removeConnection(clientId, DisconnectReason.ERROR);
            metrics.sseSend("failure");
            return false;
        }
    }

    /**
     * 广播给所有活跃连接。返回成功发送的连接数。
     */
    public int broadcast(SseEvent event) {
        int success = 0;
        for (String id : List.copyOf(connections.keySet())) {
            if (send(id, event)) {
                success++;
            }
        }
        return success;
    }

    /**
     * 按 tag 广播。返回成功发送的连接数。
     */
    public int broadcastByTag(String tag, SseEvent event) {
        Set<String> ids = tagIndex.get(tag);
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        int success = 0;
        for (String id : List.copyOf(ids)) {
            if (send(id, event)) {
                success++;
            }
        }
        return success;
    }

    /**
     * 给连接打 tag（用于 broadcastByTag）。
     */
    public void tag(String clientId, String tag) {
        SseConnection conn = connections.get(clientId);
        if (conn == null) {
            return;
        }
        conn.tag(tag);
        tagIndex.computeIfAbsent(tag, k -> ConcurrentHashMap.newKeySet()).add(clientId);
    }

    /**
     * 移除 tag。
     */
    public void untag(String clientId, String tag) {
        SseConnection conn = connections.get(clientId);
        if (conn == null) {
            return;
        }
        conn.untag(tag);
        Set<String> ids = tagIndex.get(tag);
        if (ids != null) {
            ids.remove(clientId);
        }
    }

    /**
     * 主动断开连接。返回 true 表示该 clientId 之前是活跃状态。
     */
    public boolean disconnect(String clientId) {
        return removeConnection(clientId, DisconnectReason.MANUAL);
    }

    /**
     * 当前活跃连接数。
     */
    public int activeCount() {
        return connections.size();
    }

    /**
     * 当前所有连接的不可变快照。
     */
    public Collection<SseConnection> snapshot() {
        return List.copyOf(connections.values());
    }

    /**
     * 查连接元信息。
     */
    public Optional<SseConnection> info(String clientId) {
        return Optional.ofNullable(connections.get(clientId));
    }

    // ──────────────────────────── 内部 ────────────────────────────

    /**
     * 移除连接并清理 tagIndex + metrics。
     * <p>幂等：重复调用（clientId 不存在）返回 false。
     */
    boolean removeConnection(String clientId, DisconnectReason reason) {
        SseConnection conn = connections.remove(clientId);
        if (conn == null) {
            return false;
        }
        // 清理 tag 索引
        for (String tag : new ArrayList<>(conn.tags())) {
            Set<String> ids = tagIndex.get(tag);
            if (ids != null) {
                ids.remove(clientId);
                if (ids.isEmpty()) {
                    tagIndex.remove(tag, ids);
                }
            }
        }
        // 尝试 complete（幂等）
        try {
            conn.emitter().complete();
        } catch (Exception ignored) {
            // emitter 可能已 complete 或 error，忽略
        }
        metrics.sseDisconnected(reason.tag());
        log.debug("SSE disconnected: clientId={} reason={}", clientId, reason);
        return true;
    }

    // ──────────────────────────── SmartLifecycle ────────────────────────────

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        heartbeatTask = heartbeatExecutor.scheduleWithFixedDelay(
                this::tickHeartbeat,
                heartbeatInterval.toMillis(),
                heartbeatInterval.toMillis(),
                TimeUnit.MILLISECONDS);
        log.info("SseManager started: heartbeatInterval={}ms timeoutMs={}ms",
                heartbeatInterval.toMillis(), timeout.toMillis());
    }

    @Override
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
            heartbeatTask = null;
        }
        heartbeatExecutor.shutdown();
        try {
            if (!heartbeatExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                heartbeatExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            heartbeatExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        // 优雅关闭所有连接
        for (SseConnection conn : List.copyOf(connections.values())) {
            removeConnection(conn.clientId(), DisconnectReason.SHUTDOWN);
        }
        log.info("SseManager stopped: closedConnections={}", connections.size());
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public int getPhase() {
        // 在业务组件之后停机（先停业务，最后停 SSE）
        return Integer.MAX_VALUE - 1000;
    }

    private void tickHeartbeat() {
        if (!running.get()) {
            return;
        }
        SseEvent ping = SseEvent.ping();
        for (String id : List.copyOf(connections.keySet())) {
            send(id, ping);
        }
    }

    @Override
    public void close() {
        stop();
    }

    /**
     * 连接断开原因（埋点 tag 用）。
     */
    public enum DisconnectReason {
        COMPLETION("completion"),
        TIMEOUT("timeout"),
        ERROR("error"),
        MANUAL("manual"),
        SHUTDOWN("shutdown");

        private final String tag;

        DisconnectReason(String tag) {
            this.tag = tag;
        }

        public String tag() {
            return tag;
        }
    }
}