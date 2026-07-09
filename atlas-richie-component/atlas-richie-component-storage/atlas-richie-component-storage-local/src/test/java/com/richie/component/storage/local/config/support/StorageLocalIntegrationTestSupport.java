/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.storage.local.config.support;

import com.richie.testing.local.LocalComposeDefaults;
import com.richie.testing.mysql.MySqlContainerSupport;
import com.richie.testing.redis.RedisContainerSupport;
import com.richie.testing.spring.DynamicPropertyRegistration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * storage-local 双中间件集测：Redis + MySQL，Liquibase 由组件自行跑 DDL。
 */
public final class StorageLocalIntegrationTestSupport {

    private static final DockerImageName REDIS_IMAGE = DockerImageName.parse("redis:7-alpine");
    private static final DockerImageName MYSQL_IMAGE = DockerImageName.parse("mysql:8.0");
    private static final int EXTERNAL_DEFAULT_DATABASE = 15;
    private static final String UNAVAILABLE_MESSAGE =
            "storage-local 集成测试需要 Docker（Redis + MySQL）。请安装并启动 Docker 后执行 "
                    + "IT_REQUIRE_DOCKER=true mvn verify -am；本机已有中间件时可设 IT_USE_EXTERNAL=true，"
                    + "或启动 compose（Redis 16379 / MySQL 53366），参见 atlas-richie-testing-support/README.zh.md";

    private static final RedisContainerSupport REDIS = createRedis();
    private static final MySqlContainerSupport MYSQL = createMySql();
    private static final Path STORAGE_ROOT = createStorageRoot();

    private StorageLocalIntegrationTestSupport() {
    }

    private static RedisContainerSupport createRedis() {
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

    private static MySqlContainerSupport createMySql() {
        if (LocalComposeDefaults.isMySqlReachable()) {
            return MySqlContainerSupport.externalConnection(
                    LocalComposeDefaults.MYSQL_HOST,
                    LocalComposeDefaults.MYSQL_PORT,
                    LocalComposeDefaults.MYSQL_DATABASE,
                    LocalComposeDefaults.MYSQL_USERNAME,
                    LocalComposeDefaults.MYSQL_PASSWORD,
                    UNAVAILABLE_MESSAGE,
                    "STORAGE");
        }
        return MySqlContainerSupport.resolve(MYSQL_IMAGE, UNAVAILABLE_MESSAGE, "STORAGE");
    }

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("storage-local-it-");
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create storage-local IT directory", e);
        }
    }

    private static final StorageLocalIntegrationTestSupport INSTANCE = new StorageLocalIntegrationTestSupport();

    public static StorageLocalIntegrationTestSupport getInstance() {
        return INSTANCE;
    }

    /** JUnit {@code @EnabledIf} 入口。 */
    public static boolean isEnabled() {
        return REDIS.isAvailable() && MYSQL.isAvailable();
    }

    public boolean isExternal() {
        return REDIS.isExternal() || MYSQL.isExternal();
    }

    void appendPropertyPairs(List<String> pairs) {
        REDIS.appendConnectionPropertyPairs(pairs);
        MYSQL.appendConnectionPropertyPairs(pairs);
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
        pairs.add("spring.data.redis.enable-l2-caching=true");
        pairs.add("spring.data.redis.l2-caching-data[0]=STRING");
        pairs.add("spring.data.redis.l2-caching-data[1]=HASH");
        pairs.add("spring.data.local.provider=CAFFEINE");
        pairs.add("platform.component.liquibase.enable=true");
        pairs.add("platform.component.liquibase.enableScan=false");
        pairs.add("platform.component.liquibase.dryRun=false");
        pairs.add("platform.component.storage.local.path=" + STORAGE_ROOT.toAbsolutePath());
    }
}
