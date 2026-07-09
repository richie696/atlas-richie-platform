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
package com.richie.gateway.filter.common.infrastructure;

import com.richie.contract.constant.GlobalConstants;
import com.richie.gateway.config.GatewayConfig;
import com.richie.component.i18n.config.I18nProperties;
import com.richie.component.i18n.resolver.I18nResolver;
import com.richie.gateway.filter.AbstractBaseFilter;
import com.richie.gateway.filter.FilterOrder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Locale;

/**
 * 网关安全过滤器
 *
 * @author richie696
 * @version 1.0
 * @since 2021/06/29
 */
@Slf4j
@Component
public class I18nFilter extends AbstractBaseFilter {

    private final I18nProperties properties;

    /**
     * 构造函数
     *
     * @param config 网关配置
     */
    public I18nFilter(GatewayConfig config, I18nResolver i18n, I18nProperties properties) {
        super(config, i18n);
        this.properties = properties;
    }

    /**
     * 过滤器队列序号
     *
     * @return 返回当前过滤器的队列序号
     */
    public int getOrder() {
        return FilterOrder.I18N_FILTER.getOrder();
    }

    /**
     * 网关安全过滤器过滤方法
     *
     * @param exchange 当前服务器的交换机对象
     * @param chain    过滤器链路下一环执行的触发对象
     * @return 返回过滤结果
     */
    protected Mono<Void> doFilter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        Locale locale;
        String language = request.getHeaders().getFirst(GlobalConstants.X_RD_REQUEST_LANGUAGE);
        if (StringUtils.isBlank(language)) {
            locale = properties.getDefaultLocale();
        } else {
            locale = Locale.forLanguageTag(language);
        }
        LocaleContextHolder.setLocale(locale);
        return chain.filter(exchange);
    }

    protected boolean enableVerifyFilter(ServerWebExchange exchange) {
        return true;
    }

}
