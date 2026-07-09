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
package com.richie.gateway.filter;

import com.richie.gateway.config.GatewayConfig;
import com.richie.component.i18n.resolver.I18nResolver;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 过滤器基类
 *
 * @author richie696
 * @version 1.0
 * @since 2023-08-02 17:04:49
 */
public abstract class AbstractBaseFilter implements GlobalFilter, Ordered {

    /**
     * 网关配置
     */
    protected final GatewayConfig config;
    protected final I18nResolver i18n;

    protected AbstractBaseFilter(GatewayConfig config, I18nResolver i18n) {
        this.config = config;
        this.i18n = i18n;
    }


    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 如果禁用验证则直接放行请求进入下一个过滤器
        if (!enableVerifyFilter(exchange)) {
            return chain.filter(exchange);
        }
        // 否则执行过滤器的验证方法
        return doFilter(exchange, chain);
    }

    /**
     * 过滤器执行器方法
     *
     * @param exchange 交换机对象
     * @param chain    请求链对象
     * @return 返回过滤结果
     */
    protected abstract Mono<Void> doFilter(ServerWebExchange exchange, GatewayFilterChain chain);

    /**
     * 是否启用验证
     *
     * @param exchange 交换机对象
     * @return 返回值（true：启用，false：禁用）
     */
    protected abstract boolean enableVerifyFilter(ServerWebExchange exchange);

}
