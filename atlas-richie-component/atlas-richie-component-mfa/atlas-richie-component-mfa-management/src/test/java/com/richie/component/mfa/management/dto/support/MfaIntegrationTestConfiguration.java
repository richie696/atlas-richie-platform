package com.richie.component.mfa.management.dto.support;

import com.richie.component.cache.config.CacheAutoConfiguration;
import com.richie.component.cache.redis.config.base.RedisBaseAutoConfiguration;
import com.richie.component.mfa.core.config.MfaAutoConfiguration;
import com.richie.component.mfa.core.crypto.provider.LocalKeyManagementEngine;
import com.richie.component.mfa.core.support.MfaTenantSupport;
import com.richie.component.mfa.management.manager.MfaCacheSyncManager;
import com.richie.component.mfa.management.manager.SecretKeyManager;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;

@SpringBootConfiguration
@EnableAutoConfiguration(excludeName = {
        "org.springframework.cloud.vault.config.VaultAutoConfiguration",
        "org.springframework.cloud.configuration.CompatibilityVerifierAutoConfiguration",
        "com.richie.component.mfa.management.config.MfaManagementAutoConfiguration",
        "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration",
        "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "com.richie.component.dao.config.DaoAutoConfiguration",
        "com.richie.component.liquibase.config.LiquibaseAutoConfiguration",
})
@Import({
        CacheAutoConfiguration.class,
        RedisBaseAutoConfiguration.class,
        MfaAutoConfiguration.class,
        MfaTenantSupport.class,
        LocalKeyManagementEngine.class,
        MfaCacheSyncManager.class,
        SecretKeyManager.class,
})
public class MfaIntegrationTestConfiguration {
}
