/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.provider.common;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Bounded retry policy for stateless Secret read/crypto HTTP calls.
 * Authentication/authorization failures and other 4xx responses are never retried.
 */
public final class HttpResponseRetryExecutor {
    private static final long MAX_BACKOFF_MILLIS = 2_000;

    private final int maxAttempts;

    public HttpResponseRetryExecutor(int maxAttempts) {
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    public HttpResponse<byte[]> send(HttpClient client, HttpRequest request)
            throws IOException, InterruptedException {
        IOException lastIoFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                HttpResponse<byte[]> response = client.send(
                        request, HttpResponse.BodyHandlers.ofByteArray());
                if (!retryable(response.statusCode()) || attempt == maxAttempts) {
                    return response;
                }
                byte[] discarded = response.body();
                if (discarded != null) {
                    Arrays.fill(discarded, (byte) 0);
                }
                pause(attempt, retryAfter(response));
            } catch (IOException failure) {
                lastIoFailure = failure;
                if (attempt == maxAttempts) {
                    throw failure;
                }
                pause(attempt, Duration.ZERO);
            }
        }
        throw lastIoFailure == null
                ? new IOException("Secret provider request retry budget was exhausted")
                : lastIoFailure;
    }

    private boolean retryable(int status) {
        return status == 429 || status >= 500;
    }

    private Duration retryAfter(HttpResponse<?> response) {
        String value = response.headers().firstValue("Retry-After").orElse(null);
        if (value == null || value.isBlank()) {
            return Duration.ZERO;
        }
        try {
            return Duration.ofSeconds(Math.max(0, Long.parseLong(value.trim())));
        } catch (NumberFormatException ignored) {
            try {
                Duration delay = Duration.between(
                        Instant.now(),
                        ZonedDateTime.parse(value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant());
                return delay.isNegative() ? Duration.ZERO : delay;
            } catch (DateTimeParseException invalidDate) {
                return Duration.ZERO;
            }
        }
    }

    private void pause(int attempt, Duration retryAfter) throws InterruptedException {
        long exponential = Math.min(MAX_BACKOFF_MILLIS, 100L << Math.min(attempt - 1, 4));
        long jitter = ThreadLocalRandom.current().nextLong(Math.max(1, exponential / 4));
        long requested = Math.min(MAX_BACKOFF_MILLIS, Math.max(0, retryAfter.toMillis()));
        Thread.sleep(Math.max(requested, exponential + jitter));
    }
}
