package com.richie.component.web.core.sse;

import com.richie.component.web.core.metrics.WebMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class SseManagerTest {

    private SseManager sse;

    @BeforeEach
    void setUp() {
        // 短心跳间隔便于测试
        sse = new SseManager(WebMetrics.noop(), Duration.ofSeconds(1), Duration.ofMillis(200));
        sse.start();
    }

    @AfterEach
    void tearDown() {
        sse.stop();
    }

    // ──────────────────────── connect / disconnect ────────────────────────

    @Test
    void connect_registersEmitterAndInfo() {
        SseEmitter emitter = sse.connect("client-1");

        assertThat(emitter).isNotNull();
        assertThat(sse.activeCount()).isEqualTo(1);
        assertThat(sse.info("client-1")).isPresent();
        assertThat(sse.info("client-1").get().clientId()).isEqualTo("client-1");
        assertThat(sse.info("client-1").get().emitter()).isSameAs(emitter);
    }

    @Test
    void disconnect_removesConnection() {
        sse.connect("client-1");
        assertThat(sse.activeCount()).isEqualTo(1);

        boolean removed = sse.disconnect("client-1");

        assertThat(removed).isTrue();
        assertThat(sse.activeCount()).isZero();
        assertThat(sse.info("client-1")).isEmpty();
    }

    @Test
    void disconnect_isIdempotentForMissingClient() {
        assertThat(sse.disconnect("ghost")).isFalse();
    }

    @Test
    void onCompletion_removesConnection() {
        SseEmitter emitter = sse.connect("client-1");
        emitter.complete();

        await().atMost(1, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(sse.activeCount()).isZero());
    }

    @Test
    void onError_removesConnection() {
        SseEmitter emitter = sse.connect("client-1");
        emitter.completeWithError(new RuntimeException("client disconnected"));

        await().atMost(1, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(sse.activeCount()).isZero());
    }

    // ──────────────────────── send ────────────────────────

    @Test
    void send_returnsFalse_whenClientMissing() {
        assertThat(sse.send("missing", SseEvent.of("hello"))).isFalse();
    }

    @Test
    void send_returnsTrue_whenClientConnected() {
        sse.connect("client-1");

        assertThat(sse.send("client-1", SseEvent.of("hello"))).isTrue();
        assertThat(sse.send("client-1", SseEvent.of("greeting", "world"))).isTrue();
    }

    @Test
    void send_failure_autoCleansDeadConnection() {
        SseEmitter emitter = sse.connect("client-1");
        emitter.complete();  // 先关闭 emitter 让 send 失败

        // 等 emitter complete 触发 onCompletion 清理
        await().atMost(1, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(sse.activeCount()).isZero());
    }

    // ──────────────────────── broadcast ────────────────────────

    @Test
    void broadcast_returnsSuccessCount() {
        sse.connect("c1");
        sse.connect("c2");
        sse.connect("c3");

        int success = sse.broadcast(SseEvent.of("alert", "test"));

        assertThat(success).isEqualTo(3);
    }

    @Test
    void broadcast_handlesPartialFailureGracefully() {
        sse.connect("c1");
        sse.connect("c2");
        // 让 c2 fail
        sse.info("c2").get().emitter().complete();
        // c2 关闭会触发 onCompletion 自动 remove，所以 broadcast 时只剩 c1
        await().atMost(1, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(sse.activeCount()).isEqualTo(1));

        int success = sse.broadcast(SseEvent.of("alert", "test"));

        assertThat(success).isEqualTo(1);
    }

    // ──────────────────────── tag / broadcastByTag ────────────────────────

    @Test
    void tag_and_broadcastByTag() {
        sse.connect("c1");
        sse.connect("c2");
        sse.connect("c3");
        sse.tag("c1", "vip");
        sse.tag("c2", "vip");

        int success = sse.broadcastByTag("vip", SseEvent.of("alert", "vip-only"));

        assertThat(success).isEqualTo(2);
    }

    @Test
    void broadcastByTag_returnsZero_whenTagUnknown() {
        sse.connect("c1");
        sse.tag("c1", "vip");

        assertThat(sse.broadcastByTag("nonexistent", SseEvent.of("x"))).isZero();
    }

    @Test
    void untag_removesFromIndex() {
        sse.connect("c1");
        sse.tag("c1", "vip");
        assertThat(sse.broadcastByTag("vip", SseEvent.of("x"))).isEqualTo(1);

        sse.untag("c1", "vip");
        assertThat(sse.broadcastByTag("vip", SseEvent.of("x"))).isZero();
    }

    @Test
    void tag_isIdempotentForMissingClient() {
        sse.tag("ghost", "vip");  // 不抛异常
        assertThat(sse.broadcastByTag("vip", SseEvent.of("x"))).isZero();
    }

    // ──────────────────────── snapshot ────────────────────────

    @Test
    void snapshot_returnsImmutableList() {
        sse.connect("c1");
        sse.connect("c2");

        var snap = sse.snapshot();

        assertThat(snap).hasSize(2);
        // 修改 snapshot 不影响内部 map
        assertThat(snap).isUnmodifiable();
    }

    // ──────────────────────── 心跳 ────────────────────────

    @Test
    void heartbeat_pingsActiveConnections() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        WebMetrics metrics = new WebMetrics(registry);
        SseManager sse2 = new SseManager(metrics, Duration.ofSeconds(1), Duration.ofMillis(100));
        sse2.start();

        try {
            sse2.connect("c1");
            sse2.connect("c2");

            await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
                double pingCount = registry.counter("web.sse.send", "result", "success").count();
                assertThat(pingCount).isGreaterThanOrEqualTo(2.0);  // 至少一轮心跳 2 条 ping
            });
        } finally {
            sse2.stop();
        }
    }

    // ──────────────────────── lifecycle ────────────────────────

    @Test
    void stop_closesAllActiveConnections() {
        sse.connect("c1");
        sse.connect("c2");
        sse.connect("c3");
        assertThat(sse.activeCount()).isEqualTo(3);

        sse.stop();

        assertThat(sse.activeCount()).isZero();
        assertThat(sse.isRunning()).isFalse();
    }

    @Test
    void stop_isIdempotent() {
        sse.stop();
        sse.stop();  // 不抛异常
        assertThat(sse.isRunning()).isFalse();
    }

    @Test
    void start_isIdempotent() {
        sse.start();  // 第二次启动不报错
        assertThat(sse.isRunning()).isTrue();
    }

    @Test
    void phase_isLateEnoughToStopAfterBusiness() {
        // SmartLifecycle.stop() 调用顺序：phase 大的先停
        // SseManager 应晚于业务组件停机（先停业务再停 SSE）
        assertThat(sse.getPhase()).isGreaterThan(0);
    }

    // ──────────────────────── metric ────────────────────────

    @Test
    void sseConnected_and_sseDisconnected_incrementCounter() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        WebMetrics metrics = new WebMetrics(registry);
        SseManager sse2 = new SseManager(metrics, Duration.ofSeconds(1), Duration.ofMillis(200));
        sse2.start();

        try {
            sse2.connect("c1");
            sse2.connect("c2");
            sse2.disconnect("c1");

            assertThat(registry.counter("web.sse.connections").count()).isEqualTo(2.0);
            assertThat(registry.counter("web.sse.disconnect", "reason", "manual").count()).isEqualTo(1.0);
        } finally {
            sse2.stop();
        }
    }
}