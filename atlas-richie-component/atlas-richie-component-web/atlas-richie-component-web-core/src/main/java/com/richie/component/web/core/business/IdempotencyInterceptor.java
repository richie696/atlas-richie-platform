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
package com.richie.component.web.core.business;

import com.richie.component.web.core.config.business.BusinessIntegrationProperties;
import com.richie.component.web.core.spi.WebInterceptor;
import com.richie.component.web.core.spi.WebInterceptorChain;
import com.richie.component.web.core.spi.WebRequestContext;
import com.richie.contract.model.ApiResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;

/**
 * 业务能力集成（§4.9）：幂等防御。
 * <p>
 * 从配置 header（默认 {@code X-Idempotency-Key}）读取业务方传入的幂等指纹，
 * 在 TTL 窗口（默认 60 秒）内重复提交 → 短路 409 + HookBus publish idempotent_replay。
 *
 * <h2>无 header 行为</h2>
 * <p>业务方不传幂等 key → 放行（视为普通请求，不强制要求）。
 *
 * <h2>Order</h2>
 * <p>{@link #ORDER} = 270：在 Tenant(260) 之后，RateLimit/CB 之前。
 *
 * @author richie696
 * @since 2026-07
 */
@Slf4j
public class IdempotencyInterceptor implements WebInterceptor, Ordered {

    /** 决策事件 attribute key。 */
    public static final String DECISION_ATTRIBUTE = "platform.web.idempotency-decision";

    /** 拦截器在链中的位置。 */
    public static final int ORDER = 270;

    private final String headerName;
    private final IdempotencyCache cache;
    private final int denyStatus;
    private final String denyCode;
    private final String denyMsg;

    public IdempotencyInterceptor(BusinessIntegrationProperties.Idempotency config,
                                  IdempotencyCache cache) {
        this.headerName = config.getHeaderName();
        this.cache = cache;
        this.denyStatus = config.getDenyStatus();
        this.denyCode = config.getDenyCode();
        this.denyMsg = config.getDenyMsg();
    }

    @Override
    public void intercept(WebRequestContext ctx, WebInterceptorChain chain) throws Exception {
        String key = ctx.header(headerName);
        if (key == null || key.isBlank()) {
            chain.proceed(ctx);
            return;
        }
        String trimmed = key.trim();
        if (!cache.putIfAbsent(trimmed)) {
            String msg = denyMsg.replace("{reason}", "idempotent_replay");
            String body = ApiResult.error(denyCode, msg).toJson();
            ctx.setAttribute(DECISION_ATTRIBUTE,
                    java.util.Map.of("type", "idempotency", "reason", "idempotent_replay", "key", trimmed));
            ctx.markShortCircuit(denyStatus, body);
            log.info("Idempotency replay: key={} path={}", trimmed, ctx.path());
            return;
        }
        chain.proceed(ctx);
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}