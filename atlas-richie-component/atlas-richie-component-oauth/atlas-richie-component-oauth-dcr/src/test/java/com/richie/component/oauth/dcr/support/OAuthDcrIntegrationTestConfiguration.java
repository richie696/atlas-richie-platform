/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.oauth.dcr.support;

import com.richie.component.cache.config.CacheAutoConfiguration;
import com.richie.component.cache.redis.config.base.RedisBaseAutoConfiguration;
import com.richie.component.oauth.dcr.config.OAuth2DCRAutoConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * OAuth DCR 模块集成测试装配。
 *
 * <p>仅作为 {@code @Import} 容器，不定义任何业务 Bean。所有 OAuth 相关 Bean
 * （{@code TokenStore} / {@code ClientIdMetadataDocumentResolver} /
 * {@code SSRFProtection} / {@code DynamicClientRegistrationEndpoint}）由
 * {@code OAuth2DCRAutoConfiguration} 统一装配，且业务方可通过
 * {@code @ConditionalOnMissingBean} 自行覆盖默认实现。</p>
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@Import({
        CacheAutoConfiguration.class,
        RedisBaseAutoConfiguration.class,
        OAuth2DCRAutoConfiguration.class,
})
public class OAuthDcrIntegrationTestConfiguration {
}
