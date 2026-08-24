/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.starter;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 不包含密钥内容的 Secret 刷新诊断状态。
 *
 * <p>该对象只记录计数、时间和异常类型，便于健康检查或 metrics 适配器消费，
 * 不把异常消息中的潜在配置值暴露到诊断数据中。</p>
 */
public final class SecretRefreshDiagnostics {
    private final AtomicLong attempts = new AtomicLong();
    private final AtomicLong successes = new AtomicLong();
    private final AtomicLong failures = new AtomicLong();
    private final AtomicLong listenerFailures = new AtomicLong();
    private volatile Instant lastSuccessAt;
    private volatile Instant lastFailureAt;
    private volatile String lastFailureType;

    public void recordAttempt() {
        attempts.incrementAndGet();
    }

    public void recordSuccess() {
        successes.incrementAndGet();
        lastSuccessAt = Instant.now();
    }

    public void recordFailure(Throwable failure) {
        failures.incrementAndGet();
        lastFailureAt = Instant.now();
        lastFailureType = failure == null ? "unknown" : failure.getClass().getName();
    }

    public void recordListenerFailure(Throwable failure) {
        listenerFailures.incrementAndGet();
        recordFailure(failure);
    }

    public Snapshot snapshot() {
        return new Snapshot(
                attempts.get(),
                successes.get(),
                failures.get(),
                listenerFailures.get(),
                lastSuccessAt,
                lastFailureAt,
                lastFailureType);
    }

    public record Snapshot(
            long attempts,
            long successes,
            long failures,
            long listenerFailures,
            Instant lastSuccessAt,
            Instant lastFailureAt,
            String lastFailureType) {

        public boolean hasUnrecoveredFailure() {
            return lastFailureAt != null
                    && (lastSuccessAt == null || lastFailureAt.isAfter(lastSuccessAt));
        }
    }
}
