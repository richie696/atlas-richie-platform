package com.richie.gateway.filter.internal.business;

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
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;

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
        String tenantCode = JwtUtils.getTenantCode(token);
        if (StringUtils.isBlank(tenantCode)) {
            return NetworkUtils.returnError(response, HttpStatus.UNAUTHORIZED, i18n.get("MSG_GATEWAY_TIP_6"));
        }
        OffsetDateTime tenantExpiredTime = JwtUtils.getTenantExpiredTime(token);
        if (tenantExpiredTime == null) {
            return NetworkUtils.returnError(response, HttpStatus.UNAUTHORIZED, i18n.get("MSG_GATEWAY_TIP_2"));
        }
        boolean isExpired = OffsetDateTime.now(tenantExpiredTime.getOffset()).isAfter(tenantExpiredTime);
        // 租户已过期
        if (isExpired) {
            // 发送 feign 接口通知 portal 服务作废当前账号相关的数据
            ApiResult<Void> result = signatureService.notifyTenantExpired(tenantCode);
            if (!result.isSuccess()) {
                log.error(result.getMsg());
                return NetworkUtils.returnError(response, HttpStatus.UNAUTHORIZED, i18n.get("MSG_GATEWAY_TIP_4"));
            }
            // 将当前令牌作废
            signatureService.invalidToken(token);
            return NetworkUtils.returnError(response, HttpStatus.UNAUTHORIZED, i18n.get("MSG_GATEWAY_TIP_4"));
        }
        // 租户未过期放通请求
        return chain.filter(exchange);
    }

    protected boolean enableVerifyFilter(ServerWebExchange exchange) {
        return config.getTenant().isEnable();
    }

}
