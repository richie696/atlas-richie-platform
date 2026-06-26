package com.richie.component.tenant.circuit;

import com.richie.component.tenant.config.MultiTenancyProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * 数据源熔断器（线程安全版本）。
 *
 * <p>管理 shared + 各租户数据源的熔断状态。key 约定为 String:
 * {@code "shared"} 或 {@code String.valueOf(tenantId)}。</p>
 *
 * <p><b>状态机</b>(CAS 保护,无锁):</p>
 * <pre>
 * CLOSED ── failures ≥ threshold ─→ OPEN ── 超时 ──→ HALF_OPEN(probeInFlight=true)
 *   ↑                                            │
 *   └──────────── 探测成功 ──────────── CLOSED  │
 *                                                │
 *                探测失败 ─────────────────→ OPEN
 * </pre>
 *
 * <p><b>并发安全</b>(v2.1.0 重写):
 * <ul>
 *   <li>{@link AtomicInteger} 计数,杜绝 {@code failureCount++} 丢计数</li>
 *   <li>{@link AtomicReference} 整体状态对象,CAS 替换避免锁</li>
 *   <li>OPEN → HALF_OPEN 通过 CAS 保证<b>只有一个探测请求</b>进入半开态,
 *       其余并发请求继续被 OPEN 拒绝,符合标准熔断语义</li>
 *   <li>CLOSED 态下 {@link #recordSuccess} 自动清零 failureCount,
 *       避免零星失败累加误熔断</li>
 * </ul>
 *
 * @author richie696
 * @since 2.0
 */
public class DataSourceCircuitBreaker {

    private static final Logger log = LoggerFactory.getLogger(DataSourceCircuitBreaker.class);

    private final MultiTenancyProperties properties;
    private final Map<String, AtomicReference<CircuitState>> states = new ConcurrentHashMap<>();

    private volatile Consumer<String> onOpenCallback;
    private volatile Consumer<String> onCloseCallback;

    public DataSourceCircuitBreaker(MultiTenancyProperties properties) {
        this.properties = properties;
    }

    /**
     * 判断指定数据源是否处于熔断状态(非阻塞读)。
     *
     * <p>副作用:OPEN 状态超过 {@code openWindowMs} 时,本方法会通过 CAS
     * 将状态翻转为 HALF_OPEN,并<b>立即标记 probeInFlight=true</b>。
     * 同一数据源后续并发调用读到 HALF_OPEN + probeInFlight=true 时,
     * 仍返回 true(被熔断器拒绝),只有第一个探测请求真正执行。</p>
     *
     * @param key 数据源 key
     * @return true 表示熔断中(调用应被拒绝)
     */
    public boolean isOpen(String key) {
        AtomicReference<CircuitState> ref = states.get(key);
        if (ref == null) {
            return false;
        }
        CircuitState state = ref.get();
        // 半开态下:若探测请求已发,后续请求拒绝
        if (state.status == CircuitStatus.HALF_OPEN && state.probeInFlight) {
            return true;
        }
        if (state.status != CircuitStatus.OPEN) {
            return false;
        }
        long elapsed = System.currentTimeMillis() - state.openedAt.toEpochMilli();
        if (elapsed < properties.getCircuit().getOpenWindowMs()) {
            return true;
        }
        // OPEN → HALF_OPEN: CAS 翻转,只一个线程成功
        CircuitState next = new CircuitState(CircuitStatus.HALF_OPEN,
            state.failureCount, state.openedAt, true);
        if (ref.compareAndSet(state, next)) {
            log.info("Circuit breaker HALF_OPEN for datasource: {} (after {}ms)", key, elapsed);
            return false;
        }
        // CAS 失败:重新读取最新状态
        CircuitState latest = ref.get();
        return latest.status == CircuitStatus.OPEN
            || (latest.status == CircuitStatus.HALF_OPEN && latest.probeInFlight);
    }

    /**
     * 记录一次成功调用。
     *
     * <p>HALF_OPEN 态:翻转 CLOSED 并清零 failureCount + 清 probeInFlight。
     * CLOSED 态:同步清零 failureCount(避免零星失败累加误熔断)。
     * OPEN 态:不操作(熔断期间不应有请求成功)。</p>
     */
    public void recordSuccess(String key) {
        AtomicReference<CircuitState> ref = states.get(key);
        if (ref == null) {
            return;
        }
        CircuitState prev = ref.get();
        if (prev.status == CircuitStatus.OPEN) {
            return;
        }
        CircuitState next = new CircuitState(CircuitStatus.CLOSED,
            new AtomicInteger(0), null, false);
        if (ref.compareAndSet(prev, next)) {
            if (prev.status == CircuitStatus.HALF_OPEN) {
                log.info("Circuit breaker CLOSED for datasource: {} (probe success)", key);
                Consumer<String> cb = onCloseCallback;
                if (cb != null) {
                    cb.accept(key);
                }
            } else {
                log.debug("Circuit breaker failure count reset for datasource: {}", key);
            }
        }
    }

    /**
     * 记录一次失败调用。
     *
     * <p>CLOSED 态:递增 failureCount,达到阈值后 CAS 翻转为 OPEN。
     * HALF_OPEN 态:探测失败,翻回 OPEN 并清 probeInFlight。
     * OPEN 态:不重复触发 callback。</p>
     */
    public void recordFailure(String key) {
        AtomicReference<CircuitState> ref = states.computeIfAbsent(key,
            k -> new AtomicReference<>(new CircuitState(CircuitStatus.CLOSED, new AtomicInteger(0), null, false)));
        CircuitState prev = ref.get();
        // CLOSED → CLOSED with count+1
        if (prev.status == CircuitStatus.CLOSED) {
            int newCount = prev.failureCount.incrementAndGet();
            if (newCount < properties.getCircuit().getFailureThreshold()) {
                return;
            }
            // 达到阈值:尝试翻 OPEN
            CircuitState opened = new CircuitState(CircuitStatus.OPEN,
                new AtomicInteger(newCount), Instant.now(), false);
            if (ref.compareAndSet(prev, opened)) {
                log.warn("Circuit breaker OPEN for datasource: {} (failures={})", key, newCount);
                Consumer<String> cb = onOpenCallback;
                if (cb != null) {
                    cb.accept(key);
                }
            }
            return;
        }
        // HALF_OPEN:探测失败,翻回 OPEN
        if (prev.status == CircuitStatus.HALF_OPEN) {
            CircuitState opened = new CircuitState(CircuitStatus.OPEN,
                new AtomicInteger(prev.failureCount.get()), Instant.now(), false);
            if (ref.compareAndSet(prev, opened)) {
                log.warn("Circuit breaker OPEN for datasource: {} (probe failure)", key);
                Consumer<String> cb = onOpenCallback;
                if (cb != null) {
                    cb.accept(key);
                }
            }
        }
        // OPEN 态下失败不做额外处理
    }

    /**
     * 获取指定数据源的熔断状态(非阻塞读)。
     */
    public CircuitStatus getStatus(String key) {
        AtomicReference<CircuitState> ref = states.get(key);
        return ref != null ? ref.get().status : CircuitStatus.CLOSED;
    }

    /**
     * 获取所有数据源的熔断状态(用于 Actuator 端点)。
     */
    public Map<String, CircuitStatusSnapshot> getAllStatuses() {
        Map<String, CircuitStatusSnapshot> result = new ConcurrentHashMap<>();
        states.forEach((key, ref) -> {
            CircuitState state = ref.get();
            result.put(key, new CircuitStatusSnapshot(
                state.status.name(), state.failureCount.get(), state.openedAt));
        });
        return result;
    }

    public void onOpen(Consumer<String> callback) { this.onOpenCallback = callback; }
    public void onClose(Consumer<String> callback) { this.onCloseCallback = callback; }

    // ==================== 内部类型 ====================

    public enum CircuitStatus { CLOSED, OPEN, HALF_OPEN }

    /**
     * 不可变状态对象(每次状态变化都创建新对象,通过 CAS 替换)。
     * {@code failureCount} 仍为可变,但只通过 {@link AtomicInteger} 操作。
     * {@code probeInFlight} 用于 HALF_OPEN 态下确保仅放行 1 个探测请求。
     */
    private static final class CircuitState {
        final CircuitStatus status;
        final AtomicInteger failureCount;
        final Instant openedAt;
        final boolean probeInFlight;

        CircuitState(CircuitStatus status, AtomicInteger failureCount, Instant openedAt, boolean probeInFlight) {
            this.status = status;
            this.failureCount = failureCount;
            this.openedAt = openedAt;
            this.probeInFlight = probeInFlight;
        }
    }

    public record CircuitStatusSnapshot(String status, int failures, Instant openedAt) {}
}