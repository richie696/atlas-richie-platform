package com.richie.component.mfa.core.crypto.support;

import com.richie.component.cache.config.CacheAutoConfiguration;
import com.richie.component.cache.redis.config.base.RedisBaseAutoConfiguration;
import com.richie.component.mfa.core.config.MfaAutoConfiguration;
import com.richie.component.mfa.core.crypto.provider.LocalKeyManagementEngine;
import com.richie.component.mfa.core.support.MfaTenantSupport;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;

@SpringBootConfiguration
@EnableAutoConfiguration(excludeName = {
        "org.springframework.cloud.vault.config.VaultAutoConfiguration",
        "org.springframework.cloud.configuration.CompatibilityVerifierAutoConfiguration"
})
@Import({
        CacheAutoConfiguration.class,
        RedisBaseAutoConfiguration.class,
        MfaAutoConfiguration.class,
        MfaTenantSupport.class,
        LocalKeyManagementEngine.class,
})
public class MfaIntegrationTestConfiguration {
}
