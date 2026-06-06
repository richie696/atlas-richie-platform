package com.richie.component.logging.support;

import com.richie.testing.redis.RedisContainerSupport;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.List;

public final class LoggingRedisIntegrationTestSupport {

    private static final DockerImageName REDIS_IMAGE = DockerImageName.parse("redis:7-alpine");
    private static final String UNAVAILABLE_MESSAGE =
            "Logging Redis 集成测试需要 Docker。CI 请设置 IT_REQUIRE_DOCKER=true。";

    private static final RedisContainerSupport DELEGATE = RedisContainerSupport.resolve(
            REDIS_IMAGE,
            15,
            UNAVAILABLE_MESSAGE,
            "LOGGING");

    private LoggingRedisIntegrationTestSupport() {
    }

    public static LoggingRedisIntegrationTestSupport getInstance() {
        return Holder.INSTANCE;
    }

    public static boolean isEnabled() {
        return DELEGATE.isAvailable();
    }

    public void registerRedisProperties(DynamicPropertyRegistry registry) {
        List<String> pairs = new ArrayList<>();
        appendPropertyPairs(pairs);
        pairs.forEach(pair -> {
            int eq = pair.indexOf('=');
            registry.add(pair.substring(0, eq), () -> pair.substring(eq + 1));
        });
    }

    void appendPropertyPairs(List<String> pairs) {
        DELEGATE.appendConnectionPropertyPairs(pairs);
        pairs.add("platform.cache.cache-provider=REDIS");
        pairs.add("spring.data.redis.enable-l2-caching=false");
        pairs.add("spring.data.local.provider=CAFFEINE");
        pairs.add("spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration");
    }

    private static final class Holder {
        private static final LoggingRedisIntegrationTestSupport INSTANCE = new LoggingRedisIntegrationTestSupport();
    }
}
