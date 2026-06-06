package com.richie.component.redis.streammq.support;

import com.richie.testing.redis.GenericRedisIntegrationTestSupport;
import com.richie.testing.redis.RedisIntegrationTestAccess;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

public final class StreammqRedisIntegrationTestSupport implements RedisIntegrationTestAccess {

    private static final GenericRedisIntegrationTestSupport DELEGATE = GenericRedisIntegrationTestSupport.create(
            DockerImageName.parse("redis:7-alpine"),
            15,
            "Redis 集成测试需要 Docker（Testcontainers）。请安装并启动 Docker 后执行 mvn verify；"
                    + "CI 请设置 IT_REQUIRE_DOCKER=true。参见 atlas-richie-testing-support/README.md",
            StreammqRedisIntegrationTestSupport::appendComponentProperties,
            "STREAMMQ");

    private static final StreammqRedisIntegrationTestSupport INSTANCE = new StreammqRedisIntegrationTestSupport();

    private StreammqRedisIntegrationTestSupport() {
    }

    public static StreammqRedisIntegrationTestSupport getInstance() {
        return INSTANCE;
    }

    public static boolean integrationTestsEnabled() {
        return getInstance().isEnabled();
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
        pairs.add("platform.cache.cache-provider=REDIS");
        pairs.add("spring.data.redis.enable-l2-caching=false");
        pairs.add("spring.data.redis.perf.enabled=true");
        pairs.add("spring.data.redis.perf.block-forbidden-tiers=true");
        pairs.add("spring.data.redis.perf.block-string-payload-violations=true");
        pairs.add("platform.cache.bloom-filter.enable=false");
        pairs.add("spring.data.local.provider=CAFFEINE");
        pairs.add("platform.cache.redis.stream.monitoring.enabled=true");
        pairs.add("platform.cache.redis.stream.monitoring.health-check.enabled=false");
        pairs.add("management.endpoint.redis-stream.enabled=false");
        pairs.add("platform.cache.redis.stream.tracing.enabled=true");
        pairs.add("platform.cache.redis.stream.tracing.exporters=logging");
        pairs.add("platform.cache.redis.stream.consumers.enabled=false");
    }
}
