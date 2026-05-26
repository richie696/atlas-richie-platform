package com.richie.gateway.feign;

import com.richie.contract.model.ApiResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import reactor.core.publisher.Mono;

public interface BaseFeignApi {

    /**
     * 通知租户过期的方法
     *
     * @param tenantCode 过期的租户编码
     * @return 返回通知结果
     */
    @GetMapping("/expired/{tenantCode}")
    Mono<ApiResult<Void>> notifyTenantExpired(@PathVariable String tenantCode);

    @GetMapping("/renew/{token}")
    Mono<ApiResult<Void>> notifyRenewToken(@PathVariable String token);

}
