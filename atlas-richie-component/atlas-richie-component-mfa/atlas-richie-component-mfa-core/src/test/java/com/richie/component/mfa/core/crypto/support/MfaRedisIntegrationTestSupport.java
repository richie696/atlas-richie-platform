package com.richie.component.mfa.core.crypto.support;

import com.richie.testing.redis.GenericRedisIntegrationTestSupport;
import com.richie.testing.redis.RedisIntegrationTestAccess;
import com.richie.testing.spring.PropertyContributor;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

public final class MfaRedisIntegrationTestSupport implements RedisIntegrationTestAccess {

    private static final GenericRedisIntegrationTestSupport DELEGATE = GenericRedisIntegrationTestSupport.create(
            DockerImageName.parse("redis:7-alpine"),
            15,
            "Redis 集成测试需要 Docker。参见 atlas-richie-testing-support/README.zh.md",
            MfaRedisIntegrationTestSupport::appendComponentProperties,
            "MFA");

    private MfaRedisIntegrationTestSupport() {
    }

    private static final MfaRedisIntegrationTestSupport INSTANCE = new MfaRedisIntegrationTestSupport();

    public static MfaRedisIntegrationTestSupport getInstance() {
        return INSTANCE;
    }

    /** JUnit {@code @EnabledIf} 入口（不可与实例 {@link #isEnabled()} 同名）。 */
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
        pairs.add("spring.cloud.vault.enabled=false");
        pairs.add("spring.cloud.compatibility-verifier.enabled=false");
        pairs.add("platform.component.mfa.enabled=true");
        pairs.add("platform.component.mfa.security.key-management.provider=local");
    }
}
