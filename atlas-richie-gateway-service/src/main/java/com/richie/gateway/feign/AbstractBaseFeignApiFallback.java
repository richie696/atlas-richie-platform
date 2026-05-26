package com.richie.gateway.feign;

import com.richie.contract.model.ApiResult;
import com.richie.component.i18n.resolver.I18nResolver;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public abstract class AbstractBaseFeignApiFallback implements BaseFeignApi {

    private final I18nResolver i18n;

    @Override
    public Mono<ApiResult<Void>> notifyTenantExpired(String tenantCode) {
        return Mono.just(ApiResult.error(i18n.get("MSG_GATEWAY_TIP_5")));
    }

    @Override
    public Mono<ApiResult<Void>> notifyRenewToken(String token) {
        return Mono.just(ApiResult.error(i18n.get("MSG_GATEWAY_TIP_5")));
    }
}
