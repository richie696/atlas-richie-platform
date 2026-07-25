/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.richie696.component.mfa.validation.dto.support;

import cn.richie696.component.cache.config.CacheAutoConfiguration;
import cn.richie696.component.cache.redis.config.base.RedisBaseAutoConfiguration;
import cn.richie696.component.mfa.core.config.MfaAutoConfiguration;
import cn.richie696.component.mfa.core.crypto.provider.LocalKeyManagementEngine;
import cn.richie696.component.mfa.core.support.MfaTenantSupport;
import cn.richie696.component.mfa.validation.config.MfaValidationAutoConfiguration;
import cn.richie696.component.mfa.validation.service.impl.MfaValidationServiceImpl;
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
