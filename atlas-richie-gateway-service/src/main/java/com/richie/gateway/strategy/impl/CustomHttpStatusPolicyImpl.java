package com.richie.gateway.strategy.impl;

import com.richie.gateway.bean.RequestMetric;
import com.richie.gateway.config.CustomReturnConfig;
import com.richie.gateway.config.GatewayConfig;
import com.richie.gateway.strategy.SecurityPolicy;
import com.richie.gateway.utils.NetworkUtils;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 自定义返回错误信息策略实现类
 *
 * @author richie696
 * @version 1.0
 * @since 2023-08-02 17:09:32
 */
@Component("customHttpStatusPolicy")
public final class CustomHttpStatusPolicyImpl implements SecurityPolicy {

    @Override
    public Mono<Void> handleFailure(ServerHttpRequest request, ServerHttpResponse response, GatewayConfig config, RequestMetric requestMetric) {
        CustomReturnConfig customReturn = config.getSecurity().getCustomReturn();
        return NetworkUtils.returnError(response, customReturn.getStatus(), customReturn.getErrorMessage());
    }

}
