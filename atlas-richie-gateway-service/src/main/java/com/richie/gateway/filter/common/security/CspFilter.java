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
package com.richie.gateway.filter.common.security;

import com.richie.gateway.config.CspFilterConfig;
import com.richie.gateway.config.GatewayConfig;
import com.richie.gateway.filter.AbstractBaseFilter;
import com.richie.gateway.filter.FilterOrder;
import com.richie.component.i18n.resolver.I18nResolver;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * CSP（Content-Security-Policy）安全头过滤器
 * <p>
 * 给所有通过网关转发的响应注入 Content-Security-Policy 头，防御 XSS 和数据注入攻击。
 * 必须在其他安全/认证过滤器之前执行，确保即使请求被拒绝，响应也包含 CSP 头。
 *
 * @author richie696
 * @version 1.0
 * @since 2026-07
 */
@Slf4j
@Component
public class CspFilter extends AbstractBaseFilter {

    /**
     * 构造函数
     *
     * @param config 网关配置
     * @param i18n   国际化解析器
     */
    public CspFilter(GatewayConfig config, I18nResolver i18n) {
        super(config, i18n);
    }

    /**
     * 启动自检：CSP 开启但上游代理未确认配置时发出告警
     */
    @PostConstruct
    public void checkProxyCspConfig() {
        CspFilterConfig csp = config.getCsp();
        if (csp.isEnable() && !csp.isProxyCspConfigured()) {
            log.warn("CSP 过滤器已启用 (platform.gateway.csp.enable=true)，但 proxyCspConfigured=false。"
                + " SPA HTML 页面由 Nginx/ALB 直接响应（不经过网关），需要在其配置中额外设置"
                + " Content-Security-Policy 响应头。确认配置后请设置"
                + " platform.gateway.csp.proxy-csp-configured=true 以关闭此警告。");
        }
    }

    /**
     * 过滤器队列序号
     *
     * @return 返回当前过滤器的队列序号
     */
    public int getOrder() {
        return FilterOrder.CSP_FILTER.getOrder();
    }

    /**
     * CSP 过滤器：注入 Content-Security-Policy 响应头
     *
     * @param exchange 当前服务器的交换机对象
     * @param chain    过滤器链路下一环执行的触发对象
     * @return 返回过滤结果
     */
    @Override
    protected Mono<Void> doFilter(ServerWebExchange exchange, GatewayFilterChain chain) {
        CspFilterConfig csp = config.getCsp();
        if (csp.getPolicy() != null && !csp.getPolicy().isBlank()) {
            exchange.getResponse().getHeaders().set("Content-Security-Policy", csp.getPolicy());
            if (log.isDebugEnabled()) {
                log.debug("CSP header injected: {}", csp.getPolicy());
            }
        }
        return chain.filter(exchange);
    }

    /**
     * 是否启用 CSP 过滤器
     *
     * @param exchange 请求交换机
     * @return true 表示启用该过滤器
     */
    @Override
    protected boolean enableVerifyFilter(ServerWebExchange exchange) {
        return config.getCsp().isEnable();
    }
}
