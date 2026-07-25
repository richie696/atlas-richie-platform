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
package cn.richie696.component.mfa.management.dto.support;

import cn.richie696.component.cache.config.CacheAutoConfiguration;
import cn.richie696.component.cache.redis.config.base.RedisBaseAutoConfiguration;
import cn.richie696.component.mfa.core.config.MfaAutoConfiguration;
import cn.richie696.component.mfa.core.crypto.provider.LocalKeyManagementEngine;
import cn.richie696.component.mfa.core.support.MfaTenantSupport;
import cn.richie696.component.mfa.management.manager.MfaCacheSyncManager;
import cn.richie696.component.mfa.management.manager.SecretKeyManager;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;

@SpringBootConfiguration
@EnableAutoConfiguration(excludeName = {
        "org.springframework.cloud.vault.config.VaultAutoConfiguration",
        "org.springframework.cloud.configuration.CompatibilityVerifierAutoConfiguration",
        "cn.richie696.component.mfa.management.config.MfaManagementAutoConfiguration",
        "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration",
        "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "cn.richie696.component.dao.config.DaoAutoConfiguration",
        "cn.richie696.component.liquibase.config.LiquibaseAutoConfiguration",
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
