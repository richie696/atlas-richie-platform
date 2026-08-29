/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.provider.vault;

import org.springframework.http.client.ClientHttpRequestInterceptor;

import java.util.UUID;

/**
 * Attaches and captures a component-owned correlation id without exposing the
 * HTTP client to callers. Vault's {@code X-Vault-Request} response header is a
 * boolean safety flag, not a request id; the provider-native {@code request_id}
 * lives in JSON responses that Spring Vault's typed KV API does not retain.
 */
final class VaultRequestIdCapture {
    static final String CORRELATION_HEADER = "X-Atlas-Request-Id";

    private final ThreadLocal<String> currentCorrelationId = new ThreadLocal<>();

    ClientHttpRequestInterceptor interceptor() {
        return (request, body, execution) -> {
            String correlationId = currentCorrelationId.get();
            if (correlationId == null) {
                correlationId = newCorrelationId();
                currentCorrelationId.set(correlationId);
            }
            request.getHeaders().set(CORRELATION_HEADER, correlationId);
            return execution.execute(request, body);
        };
    }

    void clear() {
        currentCorrelationId.set(newCorrelationId());
    }

    String consume() {
        String correlationId = currentCorrelationId.get();
        currentCorrelationId.remove();
        return correlationId;
    }

    private String newCorrelationId() {
        return UUID.randomUUID().toString();
    }
}
