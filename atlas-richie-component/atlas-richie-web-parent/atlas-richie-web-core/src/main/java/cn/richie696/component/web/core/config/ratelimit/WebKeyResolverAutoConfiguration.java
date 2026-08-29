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
package cn.richie696.component.web.core.config.ratelimit;

import cn.richie696.component.web.core.spi.KeyResolver;
import cn.richie696.component.web.core.spi.support.HeaderBasedKeyResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * KeyResolver 默认装配（README.md §4.1）。
 * <p>
 * 可选的 {@code atlas-richie-web-rate-limiter} /
 * {@link cn.richie696.component.web.core.interceptor.CircuitBreakerInterceptor} 都依赖
 * {@link KeyResolver} bean 解析 clientKey；用户未显式提供时，本类按 {@code WebFilterProperties#keyResolverHeader}
 * 配置（默认 {@code X-Client-Id}）注册 {@link HeaderBasedKeyResolver}。
 *
 * <h2>配置驱动（铁律）</h2>
 * <p>用户可通过以下任一方式覆盖默认 KeyResolver：
 * <ul>
 *   <li>显式声明 {@code @Bean KeyResolver custom(...)}（{@link ConditionalOnMissingBean} 跳过本类）</li>
 *   <li>设置 {@code platform.component.web.key-resolver.enabled=false}（关闭默认）—— 若此时
 *       业务限流会以 {@code client_unidentified} 安全拒绝，熔断仍会依据自身配置运行。</li>
 * </ul>
 *
 * @author richie696
 * @since 2026-07
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(KeyResolver.class)
public class WebKeyResolverAutoConfiguration {

    /**
     * 注册 {@link HeaderBasedKeyResolver}，header 名取自
     * {@link cn.richie696.component.web.core.config.ratelimit.WebFilterProperties} 的 {@code keyHeader}
     * （默认 {@code X-Client-Id}）。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "platform.component.web.key-resolver", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public KeyResolver keyResolver(WebFilterProperties properties) {
        String header = properties.getKeyHeader();
        log.info("WebKeyResolverAutoConfiguration: registering default KeyResolver (header={})", header);
        return new HeaderBasedKeyResolver(header);
    }
}
