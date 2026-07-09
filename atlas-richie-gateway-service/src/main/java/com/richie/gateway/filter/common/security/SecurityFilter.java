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

import com.richie.context.common.api.SpringContextHolder;
import com.richie.gateway.config.GatewayConfig;
import com.richie.component.cache.GlobalCache;
import com.richie.component.i18n.resolver.I18nResolver;
import com.richie.gateway.bean.RequestMetric;
import com.richie.gateway.filter.AbstractBaseFilter;
import com.richie.gateway.filter.FilterOrder;
import com.richie.gateway.strategy.SecurityPolicy;
import com.richie.gateway.utils.NetworkUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Objects;

/**
 * 网关安全过滤器
 *
 * @author richie696
 * @version 1.0
 * @since 2021/06/29
 */
@Slf4j
@Component
public class SecurityFilter extends AbstractBaseFilter {

    /**
     * 构造函数
     *
     * @param config 网关配置
     */
    public SecurityFilter(GatewayConfig config, I18nResolver i18n) {
        super(config, i18n);
    }

    /**
     * 过滤器队列序号
     *
     * @return 返回当前过滤器的队列序号
     */
    public int getOrder() {
        return FilterOrder.SECURITY_FILTER.getOrder();
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
        ServerHttpResponse response = exchange.getResponse();

        // 获取客户端 IP
        String ip = NetworkUtils.getIP(request);

        RequestMetric requestMetric = GlobalCache.struct().get(config.getVisitRecordPath() + ip, RequestMetric.class);

        // 如果是第一次访问则创建安全记录
        if (Objects.isNull(requestMetric)) {
            GlobalCache.struct().set(
                    config.getVisitRecordPath() + ip,
                    new RequestMetric()
                            .setIp(ip)
                            .setCount(1)
                            .setTime(System.currentTimeMillis()),
                    config.getSecurity().getSecurityTimeInterval()
            );
            return chain.filter(exchange);
        }
        // 如果当前请求次数超过阈值则拒绝服务
        if (requestMetric.addCount() >= config.getSecurity().getSecurityThreshold()) {
            // 根据策略名称获取策略对象
            SecurityPolicy policy = SpringContextHolder.getBean(config.getSecurity().getRule().getPolicyName());
            // 执行超限后的策略
            return policy.handleFailure(request, response, config, requestMetric);
        }
        // 检查是否超过时间间隔，如果超过则重置访问次数
        requestMetric.resetTime(config.getSecurity());
        // 刷新缓存
        GlobalCache.struct().set(config.getVisitRecordPath() + ip, requestMetric,
                config.getSecurity().getSecurityTimeInterval());
        log.info("当前IP：{}，访问次数：{}", ip, requestMetric.getCount());
        // 执行下一环
        return chain.filter(exchange);
    }

    protected boolean enableVerifyFilter(ServerWebExchange exchange) {
        String ip = NetworkUtils.getIP(exchange.getRequest());
        boolean whitelistAddress = config.getSecurity().getWhitelistAddress().contains(ip);
        return config.getSecurity().isEnable() && !whitelistAddress;
    }

}
