package com.richie.component.nats.strategy;

import com.richie.context.common.api.HeaderContextHolder;
import io.nats.client.impl.Headers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link DefaultNatsHeaderExtractor} 单元测试
 */
class DefaultNatsHeaderExtractorTest {

    @BeforeEach
    void setUp() {
        HeaderContextHolder.removeContext();
    }

    @AfterEach
    void tearDown() {
        HeaderContextHolder.removeContext();
    }

    @Test
    void extract_shouldRestoreWhitelistedHeadersToContext() {
        Headers headers = new Headers();
        headers.put("X-Trace-Id", "abc-123");
        headers.put("X-Tenant-Id", "tenant-42");

        DefaultNatsHeaderExtractor extractor = new DefaultNatsHeaderExtractor(
                Set.of("X-Trace-Id", "X-Tenant-Id"));

        extractor.extract(headers);

        assertThat(HeaderContextHolder.getHeader("X-Trace-Id")).isEqualTo("abc-123");
        assertThat(HeaderContextHolder.getHeader("X-Tenant-Id")).isEqualTo("tenant-42");
    }

    @Test
    void extract_shouldIgnoreHeadersNotInWhitelist() {
        Headers headers = new Headers();
        headers.put("X-Trace-Id", "abc-123");
        headers.put("X-Secret", "should-not-restore");

        DefaultNatsHeaderExtractor extractor = new DefaultNatsHeaderExtractor(
                Set.of("X-Trace-Id"));

        extractor.extract(headers);

        assertThat(HeaderContextHolder.getHeader("X-Trace-Id")).isEqualTo("abc-123");
        assertThat(HeaderContextHolder.getHeader("X-Secret")).isNull();
    }

    @Test
    void extract_withNullHeaders_shouldNotThrow() {
        DefaultNatsHeaderExtractor extractor = new DefaultNatsHeaderExtractor(
                Set.of("X-Trace-Id"));

        extractor.extract(null);

        assertThat(HeaderContextHolder.getHeader("X-Trace-Id")).isNull();
    }

    @Test
    void extract_withEmptyHeaders_shouldNotThrow() {
        DefaultNatsHeaderExtractor extractor = new DefaultNatsHeaderExtractor(
                Set.of("X-Trace-Id"));

        extractor.extract(new Headers());

        assertThat(HeaderContextHolder.getHeader("X-Trace-Id")).isNull();
    }

    @Test
    void extract_withEmptyWhitelist_shouldNotRestoreAnything() {
        Headers headers = new Headers();
        headers.put("X-Trace-Id", "abc-123");

        DefaultNatsHeaderExtractor extractor = new DefaultNatsHeaderExtractor(Set.of());

        extractor.extract(headers);

        assertThat(HeaderContextHolder.getHeader("X-Trace-Id")).isNull();
    }
}
