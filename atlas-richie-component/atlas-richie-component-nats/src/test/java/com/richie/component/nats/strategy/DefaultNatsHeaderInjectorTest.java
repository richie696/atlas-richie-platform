package com.richie.component.nats.strategy;

import com.richie.context.common.api.HeaderContextHolder;
import io.nats.client.impl.Headers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link DefaultNatsHeaderInjector} 单元测试
 */
class DefaultNatsHeaderInjectorTest {

    @BeforeEach
    void setUp() {
        HeaderContextHolder.removeContext();
    }

    @AfterEach
    void tearDown() {
        HeaderContextHolder.removeContext();
    }

    @Test
    void inject_shouldInjectWhitelistedHeadersFromContext() {
        HeaderContextHolder.setHeader("X-Trace-Id", "abc-123");
        HeaderContextHolder.setHeader("X-Tenant-Id", "tenant-42");

        DefaultNatsHeaderInjector injector = new DefaultNatsHeaderInjector(
                Set.of("X-Trace-Id", "X-Tenant-Id"));

        Headers headers = new Headers();
        injector.inject(headers);

        assertThat(headers.get("X-Trace-Id")).contains("abc-123");
        assertThat(headers.get("X-Tenant-Id")).contains("tenant-42");
    }

    @Test
    void inject_shouldSkipHeadersNotInWhitelist() {
        HeaderContextHolder.setHeader("X-Trace-Id", "abc-123");
        HeaderContextHolder.setHeader("X-Secret", "should-not-propagate");

        DefaultNatsHeaderInjector injector = new DefaultNatsHeaderInjector(
                Set.of("X-Trace-Id"));

        Headers headers = new Headers();
        injector.inject(headers);

        assertThat(headers.get("X-Trace-Id")).contains("abc-123");
        assertThat(headers.get("X-Secret")).isNull();
    }

    @Test
    void inject_shouldSkipBlankValues() {
        HeaderContextHolder.setHeader("X-Trace-Id", "");

        DefaultNatsHeaderInjector injector = new DefaultNatsHeaderInjector(
                Set.of("X-Trace-Id"));

        Headers headers = new Headers();
        injector.inject(headers);

        assertThat(headers.get("X-Trace-Id")).isNull();
    }

    @Test
    void inject_withEmptyWhitelist_shouldNotInjectAnything() {
        HeaderContextHolder.setHeader("X-Trace-Id", "abc-123");

        DefaultNatsHeaderInjector injector = new DefaultNatsHeaderInjector(Set.of());

        Headers headers = new Headers();
        injector.inject(headers);

        assertThat(headers.isEmpty()).isTrue();
    }
}
