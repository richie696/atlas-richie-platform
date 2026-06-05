package com.richie.component.cache.support;

import com.richie.testing.redis.RedisContainerSupport;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

/**
 * Cache 模块 Redis 集测连接：委托 {@link RedisContainerSupport}，仅追加本组件 Spring 属性。
 */
public final class RedisIntegrationTestSupport {

    private static final DockerImageName REDIS_IMAGE = DockerImageName.parse("redis:7-alpine");
    private static final int EXTERNAL_DEFAULT_DATABASE = 15;
    private static final String UNAVAILABLE_MESSAGE =
            "Redis 集成测试需要 Docker（Testcontainers）。请安装并启动 Docker 后执行 mvn verify；"
                    + "CI 请设置 IT_REQUIRE_DOCKER=true。本机已有 Redis 时可设 "
                    + "IT_USE_EXTERNAL=true，参见 atlas-richie-testing-support/README.md";

    private static final RedisContainerSupport DELEGATE = RedisContainerSupport.resolve(
            REDIS_IMAGE,
            EXTERNAL_DEFAULT_DATABASE,
            UNAVAILABLE_MESSAGE,
            "CACHE");

    private RedisIntegrationTestSupport() {
    }

    public static RedisIntegrationTestSupport getInstance() {
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

    public boolean isTestcontainers() {
        return DELEGATE.isTestcontainers();
    }

    public String skipReason() {
        return DELEGATE.skipReason();
    }

    public void registerRedisProperties(DynamicPropertyRegistry registry) {
        List<String> pairs = new java.util.ArrayList<>();
        appendPropertyPairs(pairs);
        pairs.forEach(pair -> {
            int eq = pair.indexOf('=');
            String key = pair.substring(0, eq);
            String value = pair.substring(eq + 1);
            registry.add(key, () -> value);
        });
    }

    /** 供 {@link RedisIntegrationTestInitializer} 与 {@link #registerRedisProperties} 共用。 */
    void appendPropertyPairs(List<String> pairs) {
        DELEGATE.appendConnectionPropertyPairs(pairs);
        appendComponentPropertyPairs(pairs);
    }

    private void appendComponentPropertyPairs(List<String> pairs) {
        pairs.add("platform.cache.cache-provider=REDIS");
        pairs.add("spring.data.redis.enable-l2-caching=false");
        pairs.add("spring.data.redis.perf.enabled=true");
        pairs.add("spring.data.redis.perf.block-forbidden-tiers=true");
        pairs.add("spring.data.redis.perf.block-string-payload-violations=true");
        pairs.add("platform.cache.bloom-filter.enable=false");
        pairs.add("spring.data.local.provider=CAFFEINE");
    }

    private static final class Holder {
        private static final RedisIntegrationTestSupport INSTANCE = new RedisIntegrationTestSupport();
    }
}
