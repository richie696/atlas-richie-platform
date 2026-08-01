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
package cn.richie696.component.tenant.gateway.config;

import cn.richie696.component.tenant.config.MultiTenancyProperties;
import cn.richie696.component.tenant.gateway.adapter.MessageKeyTenantErrorResponder;
import cn.richie696.component.tenant.gateway.filter.TenantFilter;
import cn.richie696.component.tenant.gateway.spi.AccessTokenRevoker;
import cn.richie696.component.tenant.gateway.spi.TenantErrorResponder;
import cn.richie696.component.tenant.gateway.spi.TenantExpiredNotifier;
import cn.richie696.contract.model.TenantFeature;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import jakarta.annotation.PostConstruct;

/**
 * Gateway 租户拦截器自动配置。
 *
 * <p>仅在 {@code platform.tenant.enable=true} 且 Gateway classpath 存在时创建过滤器。
 * Gateway 业务工程只需引入本模块，并提供过期通知、Token 作废等端口实现。</p>
 */
@AutoConfiguration
@ConditionalOnClass(GlobalFilter.class)
@ConditionalOnProperty(prefix = MultiTenancyProperties.PREFIX, name = "enable", havingValue = "true")
@EnableConfigurationProperties(MultiTenancyProperties.class)
public class TenantGatewayAutoConfiguration {

    /**
     * 将统一租户开关同步到 JWT 生成使用的全局特性标志。
     */
    @PostConstruct
    public void initializeTenantFeature() {
        TenantFeature.setEnabled(true);
    }

    @Bean
    @ConditionalOnMissingBean(TenantErrorResponder.class)
    public TenantErrorResponder tenantErrorResponder() {
        return new MessageKeyTenantErrorResponder();
    }

    /**
     * 默认不执行外部通知；业务工程可通过同一 SPI 替换为 Feign、RestClient、gRPC 或 Redis 实现。
     */
    @Bean
    @ConditionalOnMissingBean(TenantExpiredNotifier.class)
    public TenantExpiredNotifier tenantExpiredNotifier() {
        return tenantId -> reactor.core.publisher.Mono.just(false);
    }

    /**
     * 默认不执行 Token 作废；业务工程可提供自己的黑名单或缓存适配器。
     */
    @Bean
    @ConditionalOnMissingBean(AccessTokenRevoker.class)
    public AccessTokenRevoker accessTokenRevoker() {
        return token -> reactor.core.publisher.Mono.empty();
    }

    @Bean
    @ConditionalOnMissingBean(TenantFilter.class)
    public TenantFilter tenantFilter(MultiTenancyProperties properties,
                                     TenantErrorResponder errorResponder,
                                     TenantExpiredNotifier tenantExpiredNotifier,
                                     AccessTokenRevoker accessTokenRevoker) {
        return new TenantFilter(properties, errorResponder, tenantExpiredNotifier, accessTokenRevoker);
    }
}
