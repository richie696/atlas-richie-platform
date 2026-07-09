/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.cache.support;

import com.richie.testing.spring.SpringPropertyInitializer;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.spi.CachingProvider;

/**
 * 在 Spring 上下文刷新前注入 Redis 集成测试连接信息（比抽象类 {@code @DynamicPropertySource} 更可靠）。
 */
public final class RedisIntegrationTestInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        closeJCacheManagers();
        SpringPropertyInitializer.applyIfAvailable(
                RedisIntegrationTestSupport::isEnabled,
                pairs -> RedisIntegrationTestSupport.getInstance().appendPropertyPairs(pairs),
                context);
    }

    /** 同一 JVM 内多次 {@code @SpringBootTest} 时，释放 JSR-107 单例 CacheManager。 */
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
