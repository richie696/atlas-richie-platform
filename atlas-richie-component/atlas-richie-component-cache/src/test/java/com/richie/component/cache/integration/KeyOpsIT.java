package com.richie.component.cache.integration;

import com.richie.component.cache.GlobalCache;
import com.richie.component.cache.support.AbstractRedisIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KeyOpsIT extends AbstractRedisIntegrationTest {

    @Test
    void expireAndExists() {
        GlobalCache.value().set("it:key:ttl", "v", 60_000L);
        assertThat(GlobalCache.key().hasKey("it:key:ttl")).isTrue();
        GlobalCache.key().setExpiredTime("it:key:ttl", 120_000L);
        assertThat(GlobalCache.key().getExpire("it:key:ttl")).isPositive();
    }

    @Test
    void removeCache_shouldDeleteKey() {
        GlobalCache.value().set("it:key:del", "v", 60_000L);
        GlobalCache.key().removeCache("it:key:del");
        assertThat(GlobalCache.key().hasKey("it:key:del")).isFalse();
    }
}
