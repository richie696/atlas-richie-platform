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
package com.richie.gateway.filter.internal.business;

import com.richie.contract.constant.GlobalConstants;
import com.richie.contract.model.ApiResult;
import com.richie.gateway.config.GatewayConfig;
import com.richie.context.utils.spring.JwtUtils;
import com.richie.component.i18n.resolver.I18nResolver;
import com.richie.gateway.filter.AbstractBaseFilter;
import com.richie.gateway.filter.FilterOrder;
import com.richie.gateway.service.SignatureService;
import com.richie.gateway.utils.NetworkUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 租户信息验证过滤器
 *
 * @author richie696
 * @version 1.0
 * @since 2023-07-19 11:30:46
 */
@Slf4j
@Component
public class TenantFilter extends AbstractBaseFilter {

    private final SignatureService signatureService;

    /**
     * 构造函数
     *
     * @param config 网关配置
     */
    public TenantFilter(GatewayConfig config, I18nResolver i18n, SignatureService signatureService) {
        super(config, i18n);
        this.signatureService = signatureService;
    }

    /**
     * 过滤器队列序号
     *
     * @return 返回当前过滤器的队列序号
     */
    public int getOrder() {
        return FilterOrder.TENANT_FILTER.getOrder();
    }

    protected Mono<Void> doFilter(ServerWebExchange exchange, GatewayFilterChain chain) {

        ServerHttpResponse response = exchange.getResponse();

        // 获取header中的Token信息
        String token = exchange.getRequest().getHeaders().getFirst(JwtUtils.X_ACCESS_TOKEN);
        if (StringUtils.isBlank(token) || "null".equalsIgnoreCase(token) || "undefined".equalsIgnoreCase(token)) {
            return NetworkUtils.returnError(response, HttpStatus.UNAUTHORIZED, i18n.get("MSG_GATEWAY_TIP_2"));
        }
        // 检查JWT中的 tenantEnabled 标志：仅当为 true 时才执行租户校验
        String enabledStr = JwtUtils.getArgument(token, "tenantEnabled");
        if (!"true".equals(enabledStr)) {
            return chain.filter(exchange);
        }
        String tenantIdStr = JwtUtils.getArgument(token, "tenantId");
        if (StringUtils.isBlank(tenantIdStr)) {
            return NetworkUtils.returnError(response, HttpStatus.UNAUTHORIZED, i18n.get("MSG_GATEWAY_TIP_6"));
        }
        String tenantExpiredStr = JwtUtils.getArgument(token, "tenantExpiredTime");
        if (StringUtils.isBlank(tenantExpiredStr)) {
            return NetworkUtils.returnError(response, HttpStatus.UNAUTHORIZED, i18n.get("MSG_GATEWAY_TIP_2"));
        }
        boolean isExpired;
        try {
            isExpired = System.currentTimeMillis() > Long.parseLong(tenantExpiredStr);
        } catch (NumberFormatException e) {
            log.warn("无效的租户过期时间: token={}, tenantExpiredStr={}", token, tenantExpiredStr);
            return NetworkUtils.returnError(response, HttpStatus.UNAUTHORIZED, i18n.get("MSG_GATEWAY_TIP_2"));
        }
        // 租户已过期
        if (isExpired) {
            // 发送 feign 接口通知 portal 服务作废当前账号相关的数据
            ApiResult<Void> result = signatureService.notifyTenantExpired(tenantIdStr);
            if (!result.isSuccess()) {
                log.error(result.getMsg());
                return NetworkUtils.returnError(response, HttpStatus.UNAUTHORIZED, i18n.get("MSG_GATEWAY_TIP_4"));
            }
            // 将当前令牌作废
            signatureService.invalidToken(token);
            return NetworkUtils.returnError(response, HttpStatus.UNAUTHORIZED, i18n.get("MSG_GATEWAY_TIP_4"));
        }

        // 将租户ID写入请求头，传递给下游微服务
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .headers(headers -> headers.set(GlobalConstants.X_TENANT_ID, tenantIdStr))
                .build();
        // 租户未过期放通请求
        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    protected boolean enableVerifyFilter(ServerWebExchange exchange) {
        return config.getTenant().isEnable();
    }

}
