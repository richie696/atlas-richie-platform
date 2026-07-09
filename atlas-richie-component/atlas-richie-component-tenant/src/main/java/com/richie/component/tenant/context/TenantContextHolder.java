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
package com.richie.component.tenant.context;

import com.richie.contract.model.TenantPrincipal;

import java.util.function.Supplier;

/**
 * 租户上下文持有器接口。
 *
 * <p>定义租户上下文的绑定、读取和清理操作。有两种实现：
 * <ul>
 *   <li>{@link ScopedValueHolder} — 基于 JDK {@code ScopedValue}，虚拟线程友好（首选）</li>
 *   <li>{@link ThreadLocalHolder} — 基于 plain ThreadLocal + micrometer context-propagation，通用降级方案</li>
 * </ul>
 *
 * <p>由 {@link TenantContext} 静态门面统一调用，外部代码不直接依赖此接口。</p>
 *
 * @author richie696
 * @since 2.0
 */
public interface TenantContextHolder {

    /**
     * 在指定租户上下文中执行任务（无返回值）。
     *
     * <p>ScopedValue 实现会将整个 {@code task} 包裹在 {@code ScopedValue.where().run()} 中；
     * ThreadLocal 实现会在执行前 set、执行后 restore 到先前值。</p>
     *
     * @param principal 租户主体信息
     * @param task      要在租户上下文中执行的任务
     */
    void runWithTenant(TenantPrincipal principal, Runnable task);

    /**
     * 在指定租户上下文中执行任务（有返回值）。
     *
     * @param principal 租户主体信息
     * @param task      要在租户上下文中执行的任务
     * @param <T>       返回值类型
     * @return 任务执行结果
     */
    <T> T runWithTenant(TenantPrincipal principal, Supplier<T> task);

    /**
     * 获取当前线程的租户信息。
     *
     * @return 租户信息；如果未绑定则返回 {@code null}
     */
    TenantPrincipal get();

    /**
     * 清理当前线程的租户上下文。
     *
     * <p>ScopedValue 实现无需清理（作用域结束自动失效），此方法为空实现；
     * ThreadLocal 实现需要调用 {@code remove()} 防止泄漏。</p>
     */
    void clear();
}
