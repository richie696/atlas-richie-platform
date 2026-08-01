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
package cn.richie696.component.tenant.gateway.filter;

import cn.richie696.component.tenant.config.MultiTenancyProperties;
import cn.richie696.contract.constant.GlobalConstants;
import cn.richie696.context.utils.spring.JwtUtils;
import cn.richie696.context.utils.spring.TenantIdentityAssertionUtils;
import cn.richie696.component.tenant.gateway.spi.AccessTokenRevoker;
import cn.richie696.component.tenant.gateway.spi.TenantErrorResponder;
import cn.richie696.component.tenant.gateway.spi.TenantExpiredNotifier;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
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
public class TenantFilter implements GlobalFilter, Ordered {

    private final MultiTenancyProperties properties;
    private final TenantErrorResponder errorResponder;
    private final TenantExpiredNotifier tenantExpiredNotifier;
    private final AccessTokenRevoker accessTokenRevoker;

    /**
     * 构造函数
     *
     * @param properties 租户统一配置
     */
    public TenantFilter(MultiTenancyProperties properties,
                        TenantErrorResponder errorResponder,
                        TenantExpiredNotifier tenantExpiredNotifier,
                        AccessTokenRevoker accessTokenRevoker) {
        this.properties = properties;
        this.errorResponder = errorResponder;
        this.tenantExpiredNotifier = tenantExpiredNotifier;
        this.accessTokenRevoker = accessTokenRevoker;
    }

    /**
     * 过滤器队列序号
     *
     * @return 返回当前过滤器的队列序号
     */
    @Override
    public int getOrder() {
        return 300;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!properties.isEnabled()) {
            return chain.filter(exchange);
        }
        return doFilter(exchange, chain);
    }

    private Mono<Void> doFilter(ServerWebExchange exchange, GatewayFilterChain chain) {

        // 获取header中的Token信息
        String token = exchange.getRequest().getHeaders().getFirst(JwtUtils.X_ACCESS_TOKEN);
        if (StringUtils.isBlank(token) || "null".equalsIgnoreCase(token) || "undefined".equalsIgnoreCase(token)) {
            return errorResponder.unauthorized(exchange, "MSG_GATEWAY_TIP_2");
        }
        // 检查JWT中的 tenantEnabled 标志：仅当为 true 时才执行租户校验
        String enabledStr = JwtUtils.getArgument(token, "tenantEnabled");
        if (!"true".equals(enabledStr)) {
            return chain.filter(stripTenantHeaders(exchange));
        }
        String tenantIdStr = JwtUtils.getArgument(token, "tenantId");
        if (StringUtils.isBlank(tenantIdStr)) {
            return errorResponder.unauthorized(exchange, "MSG_GATEWAY_TIP_6");
        }
        String tenantExpiredStr = JwtUtils.getArgument(token, "tenantExpiredTime");
        if (StringUtils.isBlank(tenantExpiredStr)) {
            return errorResponder.unauthorized(exchange, "MSG_GATEWAY_TIP_2");
        }
        long tenantExpiredAt;
        try {
            // JwtUtils 写入的是 epoch seconds，而请求/JWT exp 使用 epoch millis。
            tenantExpiredAt = Math.multiplyExact(Long.parseLong(tenantExpiredStr), 1000L);
        } catch (NumberFormatException e) {
            log.warn("无效的租户过期时间: token={}, tenantExpiredStr={}", token, tenantExpiredStr);
            return errorResponder.unauthorized(exchange, "MSG_GATEWAY_TIP_2");
        } catch (ArithmeticException e) {
            log.warn("租户过期时间溢出: tenantExpiredStr={}", tenantExpiredStr);
            return errorResponder.unauthorized(exchange, "MSG_GATEWAY_TIP_2");
        }
        // 租户已过期
        if (System.currentTimeMillis() > tenantExpiredAt) {
            // 通过最小通知端口通知业务系统，具体通信方式由 Gateway 组合层决定。
            return tenantExpiredNotifier.notifyExpired(tenantIdStr)
                    .onErrorResume(error -> {
                        log.error("租户过期通知异常: tenantId={}", tenantIdStr, error);
                        return Mono.just(false);
                    })
                    .flatMap(notified -> {
                        if (!Boolean.TRUE.equals(notified)) {
                            log.error("租户过期通知失败: tenantId={}", tenantIdStr);
                            return errorResponder.unauthorized(exchange, "MSG_GATEWAY_TIP_4");
                        }
                        // 将当前令牌作废；作废失败也不能放行已过期租户请求。
                        return accessTokenRevoker.revoke(token)
                                .onErrorResume(error -> {
                                    log.error("租户过期 Token 作废异常: tenantId={}", tenantIdStr, error);
                                    return Mono.empty();
                                })
                                .then(errorResponder.unauthorized(exchange, "MSG_GATEWAY_TIP_4"));
                    });
        }

        MultiTenancyProperties.GatewayConfig gateway = properties.getGateway();
        String assertionSecret = gateway == null ? null : gateway.getIdentityAssertionSecret();
        if (StringUtils.isBlank(assertionSecret)) {
            // 允许下游采用 jwt-secret 独立验签模式；此时不生成未签名的身份断言。
            log.debug("未配置 platform.tenant.gateway.identity-assertion-secret，"
                    + "下游必须使用 jwt-secret 独立验证 JWT");
        }
        String assertion = null;
        if (StringUtils.isNotBlank(assertionSecret)) {
            long assertionTtlMillis = Math.max(1L, gateway.getIdentityAssertionTtlSeconds()) * 1000L;
            long assertionExpiry = Math.min(JwtUtils.getExpiredTime(token).getTime(),
                    System.currentTimeMillis() + assertionTtlMillis);
            assertion = TenantIdentityAssertionUtils.create(Long.valueOf(tenantIdStr), assertionExpiry,
                    assertionSecret);
        }
        final String internalAssertion = assertion;

        // 清理客户端伪造的租户头，再写入网关签发的租户 ID 和内部断言。
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(GlobalConstants.X_TENANT_ID);
                    headers.remove("X-Tenant-ID");
                    headers.remove(GlobalConstants.X_TENANT_ASSERTION);
                    headers.set(GlobalConstants.X_TENANT_ID, tenantIdStr);
                    if (internalAssertion != null) {
                        headers.set(GlobalConstants.X_TENANT_ASSERTION, internalAssertion);
                    }
                })
                .build();
        // 租户未过期放通请求
        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    private ServerWebExchange stripTenantHeaders(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(GlobalConstants.X_TENANT_ID);
                    headers.remove("X-Tenant-ID");
                    headers.remove(GlobalConstants.X_TENANT_ASSERTION);
                })
                .build();
        return exchange.mutate().request(request).build();
    }

}
