/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.provider.vault;

import org.springframework.http.client.ClientHttpRequestInterceptor;

import java.util.concurrent.atomic.AtomicReference;

/** Captures Vault's response request id without exposing the HTTP client to callers. */
final class VaultRequestIdCapture {
    private static final String REQUEST_HEADER = "X-Vault-Request";

    private final AtomicReference<String> lastRequestId = new AtomicReference<>();

    ClientHttpRequestInterceptor interceptor() {
        return (request, body, execution) -> {
            var response = execution.execute(request, body);
            String requestId = response.getHeaders().getFirst(REQUEST_HEADER);
            if (requestId != null && !requestId.isBlank()) {
                lastRequestId.set(requestId);
            }
            return response;
        };
    }

    void clear() {
        lastRequestId.set(null);
    }

    String consume() {
        return lastRequestId.getAndSet(null);
    }
}
