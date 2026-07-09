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
package com.richie.component.storage.local.config;

import com.richie.component.cache.GlobalCache;
import com.richie.component.cache.enums.KeyTypeEnum;
import com.richie.component.cache.ops.CacheInfrastructure;
import com.richie.component.cache.ops.KeyOps;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 缓存配置检查器
 * 用于检查缓存配置是否正确，并在应用启动时给出提示
 *
 * @author richie696
 * @version 1.0
 * @since 2025-10-14
 */
@Slf4j
@Component
public class CacheConfigurationChecker {

    @EventListener(ApplicationReadyEvent.class)
    public void checkCacheConfiguration() {
        log.info("=== 文件存储组件缓存配置检查 ===");

        CacheInfrastructure infra = resolveCacheInfrastructure();
        if (infra == null) {
            log.warn("⚠️  无法读取缓存基础设施配置，跳过 L2 检查");
            log.info("=== 缓存配置检查完成 ===");
            return;
        }

        // 检查二级缓存是否启用
        boolean l2CachingEnabled = infra.enableL2Caching();
        if (l2CachingEnabled) {
            log.info("✅ Redis 二级缓存已启用");

            // 检查缓存类型配置
            boolean stringCacheEnabled = infra.enableKeyTypeCache(KeyTypeEnum.STRING);
            boolean hashCacheEnabled = infra.enableKeyTypeCache(KeyTypeEnum.HASH);

            if (stringCacheEnabled) {
                log.info("✅ STRING 类型缓存已启用（文件存在性、文件内容缓存）");
            } else {
                log.warn("⚠️  STRING 类型缓存未启用，文件存在性和内容缓存将不可用");
            }

            if (hashCacheEnabled) {
                log.info("✅ HASH 类型缓存已启用（文件元数据缓存）");
            } else {
                log.warn("⚠️  HASH 类型缓存未启用，文件元数据缓存将不可用");
            }

            // 测试缓存连接
            testCacheConnection();

        } else {
            log.warn("⚠️  Redis 二级缓存未启用，将只使用本地缓存");
            log.info("💡 要启用分布式缓存，请在配置文件中添加：");
            log.info("   spring.data.redis.enable-l2-caching: true");
            log.info("   spring.data.redis.l2-caching-data: [STRING, HASH]");
        }

        log.info("=== 缓存配置检查完成 ===");
    }

    /**
     * 测试缓存连接
     */
    private void testCacheConnection() {
        try {
            String testKey = "cache_test_" + System.currentTimeMillis();
            String testValue = "test_value";

            // 测试写入
            GlobalCache.value().set(testKey, testValue, 5000); // 5秒过期

            // 测试读取
            String retrievedValue = GlobalCache.value().get(testKey, String.class);
            if (testValue.equals(retrievedValue)) {
                log.info("✅ 缓存读写测试成功");
            } else {
                log.error("❌ 缓存读写测试失败：写入值不匹配");
            }

            // 清理测试数据
            GlobalCache.key().removeCache(testKey);

        } catch (Exception e) {
            log.error("❌ 缓存连接测试失败: {}", e.getMessage());
            log.info("💡 请检查 Redis 配置是否正确");
        }
    }

    /**
     * 获取缓存状态信息
     */
    public void logCacheStatus() {
        log.info("=== 当前缓存状态 ===");
        CacheInfrastructure infra = resolveCacheInfrastructure();
        if (infra == null) {
            log.warn("无法获取缓存基础设施信息");
            return;
        }
        log.info("二级缓存启用状态: {}", infra.enableL2Caching());
        log.info("STRING 缓存启用状态: {}", infra.enableKeyTypeCache(KeyTypeEnum.STRING));
        log.info("HASH 缓存启用状态: {}", infra.enableKeyTypeCache(KeyTypeEnum.HASH));

        try {
            log.info("Redis 连接信息: {}", infra.getConnectionString());
        } catch (Exception e) {
            log.warn("无法获取 Redis 连接信息: {}", e.getMessage());
        }
    }

    private static CacheInfrastructure resolveCacheInfrastructure() {
        KeyOps keyOps = GlobalCache.key();
        return keyOps instanceof CacheInfrastructure ci ? ci : null;
    }
}
