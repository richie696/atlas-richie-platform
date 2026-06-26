package com.richie.component.tenant.circuit;

import com.richie.component.tenant.config.MultiTenancyProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 数据源熔断器。
 *
 * <p>管理 shared + 各租户数据源的熔断状态。key 约定为 String：
 * {@code "shared"} 或 {@code String.valueOf(tenantId)}。</p>
 *
 * <p>状态机：CLOSED →（failures ≥ threshold）→ OPEN →（超时）→ HALF_OPEN
 * →（探测成功）→ CLOSED /（探测失败）→ OPEN</p>
 *
 * @author richie696
 * @since 2.0
 */
public class DataSourceCircuitBreaker {

    private static final Logger log = LoggerFactory.getLogger(DataSourceCircuitBreaker.class);

    private final MultiTenancyProperties properties;
    private final Map<String, CircuitState> states = new ConcurrentHashMap<>();

    private Consumer<String> onOpenCallback;
    private Consumer<String> onCloseCallback;

    public DataSourceCircuitBreaker(MultiTenancyProperties properties) {
        this.properties = properties;
    }

    /**
     * 判断指定数据源是否处于熔断状态。
     */
    public boolean isOpen(String key) {
        CircuitState state = states.get(key);
        if (state == null) return false;
        if (state.status == CircuitStatus.OPEN) {
            long elapsed = System.currentTimeMillis() - state.openedAt.toEpochMilli();
            if (elapsed >= properties.getCircuit().getOpenWindowMs()) {
                state.status = CircuitStatus.HALF_OPEN;
                return false;
            }
            return true;
        }
        return false;
    }

    /**
     * 记录一次成功调用。
     */
    public void recordSuccess(String key) {
        CircuitState state = states.get(key);
        if (state == null) return;
        if (state.status == CircuitStatus.HALF_OPEN) {
            state.status = CircuitStatus.CLOSED;
            state.failureCount = 0;
            state.openedAt = null;
            log.info("Circuit breaker CLOSED for datasource: {}", key);
            if (onCloseCallback != null) onCloseCallback.accept(key);
        }
    }

    /**
     * 记录一次失败调用。
     */
    public void recordFailure(String key) {
        CircuitState state = states.computeIfAbsent(key, k -> new CircuitState());
        state.failureCount++;
        if (state.failureCount >= properties.getCircuit().getFailureThreshold()
                && state.status != CircuitStatus.OPEN) {
            state.status = CircuitStatus.OPEN;
            state.openedAt = Instant.now();
            log.warn("Circuit breaker OPEN for datasource: {} (failures={})", key, state.failureCount);
            if (onOpenCallback != null) onOpenCallback.accept(key);
        }
    }

    /**
     * 获取指定数据源的熔断状态。
     */
    public CircuitStatus getStatus(String key) {
        CircuitState state = states.get(key);
        return state != null ? state.status : CircuitStatus.CLOSED;
    }

    /**
     * 获取所有数据源的熔断状态（用于 Actuator 端点）。
     */
    public Map<String, CircuitStatusSnapshot> getAllStatuses() {
        Map<String, CircuitStatusSnapshot> result = new ConcurrentHashMap<>();
        states.forEach((key, state) -> result.put(key, new CircuitStatusSnapshot(
            state.status.name(), state.failureCount, state.openedAt)));
        return result;
    }

    public void onOpen(Consumer<String> callback) { this.onOpenCallback = callback; }
    public void onClose(Consumer<String> callback) { this.onCloseCallback = callback; }

    // ==================== 内部类型 ====================

    public enum CircuitStatus { CLOSED, OPEN, HALF_OPEN }

    private static class CircuitState {
        CircuitStatus status = CircuitStatus.CLOSED;
        int failureCount = 0;
        Instant openedAt;
    }

    public record CircuitStatusSnapshot(String status, int failures, Instant openedAt) {}
}
