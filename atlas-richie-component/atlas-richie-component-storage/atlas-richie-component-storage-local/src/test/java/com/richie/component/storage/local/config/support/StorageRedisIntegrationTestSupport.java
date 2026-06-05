package com.richie.component.storage.local.config.support;

import com.richie.testing.redis.GenericRedisIntegrationTestSupport;
import com.richie.testing.redis.RedisIntegrationTestAccess;
import com.richie.testing.spring.PropertyContributor;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

public final class StorageRedisIntegrationTestSupport implements RedisIntegrationTestAccess {

    private static final GenericRedisIntegrationTestSupport DELEGATE = GenericRedisIntegrationTestSupport.create(
            DockerImageName.parse("redis:7-alpine"),
            15,
            "Redis 集成测试需要 Docker。参见 atlas-richie-testing-support/README.md",
            StorageRedisIntegrationTestSupport::appendComponentProperties,
            "STORAGE");

    private StorageRedisIntegrationTestSupport() {
    }

    private static final StorageRedisIntegrationTestSupport INSTANCE = new StorageRedisIntegrationTestSupport();

    public static StorageRedisIntegrationTestSupport getInstance() {
        return INSTANCE;
    }

    public static boolean isEnabled() {
        return DELEGATE.isEnabled();
    }

    @Override
    public boolean isEnabled() {
        return DELEGATE.isEnabled();
    }

    @Override
    public boolean isExternal() {
        return DELEGATE.isExternal();
    }

    @Override
    public void appendPropertyPairs(List<String> pairs) {
        DELEGATE.appendPropertyPairs(pairs);
    }

    private static void appendComponentProperties(List<String> pairs) {
        // 本模块集测专属属性（按需补充）
    }
}
