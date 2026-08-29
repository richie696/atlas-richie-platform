/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.provider.vault;

import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * 仅重试网络错误、429 与 5xx 的有界执行器。调用点只包含读取与
 * Transit 无持久化副作用的密码运算；重试产生的未采用密文不会被持久化。
 */
final class VaultRetryExecutor {
    private final int maxAttempts;

    VaultRetryExecutor(int maxAttempts) {
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    <T> T execute(Supplier<T> operation) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return operation.get();
            } catch (RuntimeException exception) {
                last = exception;
                if (attempt == maxAttempts || !isTransient(exception)) {
                    throw exception;
                }
                pause(attempt, retryAfter(exception));
            }
        }
        throw last == null ? new IllegalStateException("Vault operation did not execute") : last;
    }

    private boolean isTransient(RuntimeException exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof ResourceAccessException) {
                return true;
            }
            if (current instanceof RestClientResponseException response) {
                return response.getStatusCode().value() == 429 || response.getStatusCode().is5xxServerError();
            }
            current = current.getCause();
        }
        return false;
    }

    private Duration retryAfter(RuntimeException exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof RestClientResponseException response
                    && response.getResponseHeaders() != null) {
                String value = response.getResponseHeaders().getFirst("Retry-After");
                if (value == null || value.isBlank()) return Duration.ZERO;
                try {
                    return Duration.ofSeconds(Math.max(0, Long.parseLong(value.trim())));
                } catch (NumberFormatException ignored) {
                    try {
                        Duration delay = Duration.between(Instant.now(), ZonedDateTime.parse(
                                value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant());
                        return delay.isNegative() ? Duration.ZERO : delay;
                    } catch (DateTimeParseException invalidDate) {
                        return Duration.ZERO;
                    }
                }
            }
            current = current.getCause();
        }
        return Duration.ZERO;
    }

    private void pause(int attempt, Duration retryAfter) {
        long ceiling = Math.min(1_000L, 100L << Math.min(attempt - 1, 3));
        long millis = ThreadLocalRandom.current().nextLong(Math.max(1L, ceiling / 2), ceiling + 1);
        millis = Math.max(millis, Math.min(2_000L, Math.max(0, retryAfter.toMillis())));
        try {
            Thread.sleep(Duration.ofMillis(millis));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while retrying Vault operation", exception);
        }
    }
}
