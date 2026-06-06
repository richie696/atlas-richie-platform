package com.richie.component.storage.local.config.integration;

import com.richie.component.cache.GlobalCache;
import com.richie.component.storage.local.config.support.AbstractStorageRedisIntegrationTest;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class LocalStorageCacheOpsIT extends AbstractStorageRedisIntegrationTest {

    @Test
    void fileExistsCache_roundTripThroughGlobalCache() {
        String cacheKey = "it:file:exists:wave-b-demo.txt";
        GlobalCache.value().set(cacheKey, true, 60_000L);
        assertThat(GlobalCache.value().get(cacheKey, Boolean.class)).isTrue();
        GlobalCache.key().removeCache(cacheKey);
        assertThat(GlobalCache.value().get(cacheKey, Boolean.class)).isNull();
    }
}
