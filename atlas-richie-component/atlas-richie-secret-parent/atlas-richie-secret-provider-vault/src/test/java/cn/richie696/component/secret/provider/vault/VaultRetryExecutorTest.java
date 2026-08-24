/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.provider.vault;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VaultRetryExecutorTest {

    @Test
    void retriesServerFailureWithinConfiguredBound() {
        AtomicInteger attempts = new AtomicInteger();

        String result = new VaultRetryExecutor(3).execute(() -> {
            if (attempts.incrementAndGet() < 2) {
                throw new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE);
            }
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(attempts).hasValue(2);
    }

    @Test
    void doesNotRetryPermanentPermissionFailure() {
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> new VaultRetryExecutor(3).execute(() -> {
            attempts.incrementAndGet();
            throw new HttpClientErrorException(HttpStatus.FORBIDDEN);
        })).isInstanceOf(HttpClientErrorException.class);
        assertThat(attempts).hasValue(1);
    }
}
