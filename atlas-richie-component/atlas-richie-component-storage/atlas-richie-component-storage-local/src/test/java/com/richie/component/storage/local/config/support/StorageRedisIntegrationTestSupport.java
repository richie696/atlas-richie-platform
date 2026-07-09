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

import com.richie.testing.local.LocalComposeDefaults;
import com.richie.testing.redis.RedisContainerSupport;
import com.richie.testing.spring.DynamicPropertyRegistration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.List;

/**
 * storage-local Redis 集测：委托 {@link RedisContainerSupport}，与 cache 模块同模式。
 */
public final class StorageRedisIntegrationTestSupport {

    private static final DockerImageName REDIS_IMAGE = DockerImageName.parse("redis:7-alpine");
    private static final int EXTERNAL_DEFAULT_DATABASE = 15;
    private static final String UNAVAILABLE_MESSAGE =
            "Redis 集成测试需要 Docker（Testcontainers）。请安装并启动 Docker 后执行 mvn verify；"
                    + "CI 请设置 IT_REQUIRE_DOCKER=true。本机已有 Redis 时可设 "
                    + "IT_USE_EXTERNAL=true，或启动 compose（16379），参见 atlas-richie-testing-support/README.zh.md";

    private static final RedisContainerSupport DELEGATE = createDelegate();

    private static RedisContainerSupport createDelegate() {
        if (LocalComposeDefaults.isRedisReachable()) {
            return RedisContainerSupport.externalConnection(
                    LocalComposeDefaults.REDIS_HOST,
                    LocalComposeDefaults.REDIS_PORT,
                    LocalComposeDefaults.REDIS_PASSWORD,
                    EXTERNAL_DEFAULT_DATABASE,
                    UNAVAILABLE_MESSAGE,
                    "STORAGE");
        }
        return RedisContainerSupport.resolve(
                REDIS_IMAGE,
                EXTERNAL_DEFAULT_DATABASE,
                UNAVAILABLE_MESSAGE,
                "STORAGE");
    }

    private StorageRedisIntegrationTestSupport() {
    }

    public static StorageRedisIntegrationTestSupport getInstance() {
        return Holder.INSTANCE;
    }

    /** JUnit {@code @EnabledIf} 入口。 */
    public static boolean isEnabled() {
        return DELEGATE.isAvailable();
    }

    public boolean isAvailable() {
        return DELEGATE.isAvailable();
    }

    public boolean isExternal() {
        return DELEGATE.isExternal();
    }

    void appendPropertyPairs(List<String> pairs) {
        DELEGATE.appendConnectionPropertyPairs(pairs);
        appendComponentPropertyPairs(pairs);
    }

    /** 供 {@link org.springframework.test.context.DynamicPropertySource} 与 Initializer 共用。 */
    public void registerProperties(DynamicPropertyRegistry registry) {
        List<String> pairs = new ArrayList<>();
        appendPropertyPairs(pairs);
        DynamicPropertyRegistration.applyPairs(registry, pairs);
    }

    private static void appendComponentPropertyPairs(List<String> pairs) {
        pairs.add("platform.cache.cache-provider=REDIS");
    }

    private static final class Holder {
        private static final StorageRedisIntegrationTestSupport INSTANCE = new StorageRedisIntegrationTestSupport();
    }
}
