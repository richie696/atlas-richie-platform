/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.tenant.reactive;

import com.richie.contract.model.TenantPrincipal;
import reactor.util.context.Context;

/**
 * Reactor {@link Context} 的租户上下文 Key 常量。
 *
 * <p>所有跨 Reactive 算子链的租户上下文传递均通过此 Key 存储在
 * Reactor {@link Context} 中，避免依赖 ThreadLocal / ScopedValue
 * 在响应式环境下不可靠的问题。</p>
 *
 * <h2>用法</h2>
 * <pre>{@code
 * // 写入（通常在 TenantWebFilter 或入口拦截器）
 * Mono.deferContextual(ctx -> {
 *     // 已有 ctx.get(TenantContextKeys.TENANT_KEY)
 *     ...
 * });
 *
 * // 读取（业务层统一通过 ReactorTenantContext 门面）
 * ReactorTenantContext.getTenantId()
 *     .flatMap(tenantId -> orderService.list(tenantId));
 * }</pre>
 *
 * @author richie696
 * @since 1.0.0
 */
public final class TenantContextKeys {

    private TenantContextKeys() {
    }

    /**
     * Reactor {@link Context} 中存储 {@link TenantPrincipal} 的 Key。
     */
    public static final Class<TenantPrincipal> TENANT_KEY = TenantPrincipal.class;

}
