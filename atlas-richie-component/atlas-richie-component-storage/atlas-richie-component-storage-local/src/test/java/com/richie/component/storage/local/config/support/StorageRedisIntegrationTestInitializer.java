/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.storage.local.config.support;

import com.richie.testing.spring.SpringPropertyInitializer;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.spi.CachingProvider;

public final class StorageRedisIntegrationTestInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        closeJCacheManagers();
        SpringPropertyInitializer.applyIfAvailable(
                StorageRedisIntegrationTestSupport::isEnabled,
                pairs -> StorageRedisIntegrationTestSupport.getInstance().appendPropertyPairs(pairs),
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
