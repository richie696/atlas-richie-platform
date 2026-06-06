package com.richie.component.storage.local.config.support;

import com.richie.testing.spring.SpringPropertyInitializer;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.spi.CachingProvider;

public final class StorageLocalIntegrationTestInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        closeJCacheManagers();
        SpringPropertyInitializer.applyIfAvailable(
                StorageLocalIntegrationTestSupport::isEnabled,
                pairs -> StorageLocalIntegrationTestSupport.getInstance().appendPropertyPairs(pairs),
                context);
    }

    private static void closeJCacheManagers() {
        for (CachingProvider provider : Caching.getCachingProviders()) {
            try {
                CacheManager manager = provider.getCacheManager();
                if (manager != null) {
                    manager.close();
                }
            } catch (Exception ignored) {
                // 首次启动或无已注册 CacheManager 时忽略
            }
        }
    }
}
