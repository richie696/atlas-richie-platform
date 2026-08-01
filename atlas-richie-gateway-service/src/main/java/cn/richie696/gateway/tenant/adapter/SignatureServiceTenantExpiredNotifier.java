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
package cn.richie696.gateway.tenant.adapter;

import cn.richie696.contract.model.ApiResult;
import cn.richie696.gateway.service.SignatureService;
import cn.richie696.component.tenant.gateway.spi.TenantExpiredNotifier;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 将 Gateway 现有 SignatureService 适配为租户通知端口。
 *
 * <p>该类是 Gateway 的组合层适配器，租户拦截器本身不直接依赖
 * {@link SignatureService}。</p>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnMissingBean(TenantExpiredNotifier.class)
public class SignatureServiceTenantExpiredNotifier implements TenantExpiredNotifier {

    private final SignatureService signatureService;

    @Override
    public Mono<Boolean> notifyExpired(String tenantId) {
        return Mono.fromCallable(() -> {
                    ApiResult<Void> result = signatureService.notifyTenantExpired(tenantId);
                    return result != null && result.isSuccess();
                })
                .subscribeOn(Schedulers.boundedElastic());
    }
}
