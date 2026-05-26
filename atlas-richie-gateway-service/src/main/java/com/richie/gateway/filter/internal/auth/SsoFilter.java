package com.richie.gateway.filter.internal.auth;

import com.richie.contract.constant.GlobalConstants;
import com.richie.gateway.config.GatewayConfig;
import com.richie.component.i18n.resolver.I18nResolver;
import com.richie.gateway.filter.AbstractBaseFilter;
import com.richie.gateway.filter.FilterOrder;
import com.richie.gateway.utils.NetworkUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 单点登录过滤器
 *
 * @author richie696
 * @version 1.0
 * @since 2024-04-28 16:51:47
 */
@Component
public class SsoFilter extends AbstractBaseFilter {


    protected SsoFilter(GatewayConfig config, I18nResolver i18n) {
        super(config, i18n);
    }

    protected Mono<Void> doFilter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!config.getSso().getPortal().isEnable()) {
            return chain.filter(exchange);
        }

        String url = config.getSso().getPortal().getCheckTokenUrl();
        WebClient webClient = WebClient.create(url);
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        String code;
        // 从请求地址或者请求头中获取code
        if (config.getToken().getLoginUriList().stream().anyMatch(path::matches)) {
            code = request.getQueryParams().getFirst("code");
        } else {
            code = request.getHeaders().getFirst(GlobalConstants.X_RD_REQUEST_SSO);
        }
        // 如果code为空，则返回错误
        if (StringUtils.isBlank(code)) {
            return NetworkUtils.returnError(exchange.getResponse(), HttpStatus.UNAUTHORIZED, i18n.get("MSG_GATEWAY_TIP_7"));
        }
        // 否则，调用SSO服务验证code
        return webClient.post()
                .bodyValue(Map.of("token", code))
                .retrieve()
                .bodyToMono(Map.class)
                .flatMap(result -> {
                    // 处理结果
                    Object active = result.get("active");
                    if (active instanceof Boolean && !(Boolean) active) {
                        return NetworkUtils.returnError(exchange.getResponse(), HttpStatus.UNAUTHORIZED, i18n.get("MSG_GATEWAY_TIP_2"));
                    }
                    request.getHeaders().set(GlobalConstants.X_RD_REQUEST_SSO, code);
                    // 然后继续处理链
                    return chain.filter(exchange);
                });
    }

    protected boolean enableVerifyFilter(ServerWebExchange exchange) {
        return config.getSso().isEnable();
    }

    public int getOrder() {
        return FilterOrder.SSO_FILTER.getOrder();
    }
}
