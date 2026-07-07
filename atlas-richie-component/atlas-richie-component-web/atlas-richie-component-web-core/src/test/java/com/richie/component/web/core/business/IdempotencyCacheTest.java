package com.richie.component.web.core.business;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class IdempotencyCacheTest {

    @Test
    void firstPut_returnsTrue() {
        IdempotencyCache cache = new IdempotencyCache(60);
        assertThat(cache.putIfAbsent("k1")).isTrue();
    }

    @Test
    void secondPutWithinTtl_returnsFalse() {
        IdempotencyCache cache = new IdempotencyCache(60);
        cache.putIfAbsent("k1");
        assertThat(cache.putIfAbsent("k1")).isFalse();
    }

    @Test
    void putAfterTtl_returnsTrue() throws InterruptedException {
        IdempotencyCache cache = new IdempotencyCache(1);
        cache.putIfAbsent("k1");
        Thread.sleep(1100);
        assertThat(cache.putIfAbsent("k1")).isTrue();
    }

    @Test
    void differentKeys_independent() {
        IdempotencyCache cache = new IdempotencyCache(60);
        cache.putIfAbsent("k1");
        assertThat(cache.putIfAbsent("k2")).isTrue();
    }

    @Test
    void nullKey_throwsNpe() {
        IdempotencyCache cache = new IdempotencyCache(60);
        assertThatNullPointerException().isThrownBy(() -> cache.putIfAbsent(null));
    }

    @Test
    void size_excludesExpired() throws InterruptedException {
        IdempotencyCache cache = new IdempotencyCache(1);
        cache.putIfAbsent("k1");
        cache.putIfAbsent("k2");
        assertThat(cache.size()).isEqualTo(2);
        Thread.sleep(1100);
        assertThat(cache.size()).isEqualTo(0);
    }

    @Test
    void invalidTtl_throws() {
        org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
                .isThrownBy(() -> new IdempotencyCache(0));
    }
}