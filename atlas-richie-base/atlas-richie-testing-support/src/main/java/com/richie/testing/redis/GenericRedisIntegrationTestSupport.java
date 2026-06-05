package com.richie.testing.redis;

import com.richie.testing.spring.PropertyContributor;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

/**
 * 可复用的 Redis 集测支撑：连接解析委托 {@link RedisContainerSupport}，业务属性通过 {@link PropertyContributor} 扩展。
 */
public final class GenericRedisIntegrationTestSupport implements RedisIntegrationTestAccess {

    private final RedisContainerSupport delegate;
    private final PropertyContributor extraContributor;

    private GenericRedisIntegrationTestSupport(
            RedisContainerSupport delegate,
            PropertyContributor extraContributor) {
        this.delegate = delegate;
        this.extraContributor = extraContributor;
    }

    public static GenericRedisIntegrationTestSupport create(
            DockerImageName image,
            int externalDefaultDatabase,
            String unavailableMessage,
            PropertyContributor extraContributor,
            String... envPrefixes) {
        RedisContainerSupport delegate = RedisContainerSupport.resolve(
                image,
                externalDefaultDatabase,
                unavailableMessage,
                envPrefixes);
        return new GenericRedisIntegrationTestSupport(delegate, extraContributor);
    }

    public static boolean isEnabled(GenericRedisIntegrationTestSupport support) {
        return support != null && support.isEnabled();
    }

    @Override
    public boolean isEnabled() {
        return delegate.isAvailable();
    }

    @Override
    public boolean isExternal() {
        return delegate.isExternal();
    }

    public String skipReason() {
        return delegate.skipReason();
    }

    @Override
    public void appendPropertyPairs(List<String> pairs) {
        delegate.appendConnectionPropertyPairs(pairs);
        if (extraContributor != null) {
            extraContributor.contribute(pairs);
        }
    }
}
