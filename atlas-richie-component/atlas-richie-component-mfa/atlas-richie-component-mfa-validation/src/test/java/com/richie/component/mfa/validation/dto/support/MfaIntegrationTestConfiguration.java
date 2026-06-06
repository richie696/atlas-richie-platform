package com.richie.component.mfa.validation.dto.support;

import com.richie.component.cache.config.CacheAutoConfiguration;
import com.richie.component.cache.redis.config.base.RedisBaseAutoConfiguration;
import com.richie.component.mfa.core.config.MfaAutoConfiguration;
import com.richie.component.mfa.core.crypto.provider.LocalKeyManagementEngine;
import com.richie.component.mfa.core.support.MfaTenantSupport;
import com.richie.component.mfa.validation.config.MfaValidationAutoConfiguration;
import com.richie.component.mfa.validation.service.impl.MfaValidationServiceImpl;
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
        MfaValidationAutoConfiguration.class,
        MfaTenantSupport.class,
        LocalKeyManagementEngine.class,
        MfaValidationServiceImpl.class,
})
public class MfaIntegrationTestConfiguration {
}
