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
package com.richie.component.tenant.context;

import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ThreadLocalAccessor;
import com.richie.component.tenant.exception.TenantSwitchInTransactionException;
import jakarta.annotation.Nonnull;

/**
 * 事务租户持有器。
 *
 * <p>在 {@code @Transactional} 方法开始时冻结当前租户 ID，事务结束前禁止切换。
 * 通过 Spring {@code TransactionSynchronization} 在事务完成后自动清理。</p>
 *
 * <p>冻结检测与 {@link TenantContext#runWithTenant} 联动：
 * 若已冻结 tenantA，此时 runWithTenant(tenantB) 会触发
 * {@link TenantSwitchInTransactionException}。</p>
 *
 * @author richie696
 * @since 2.0
 */
public final class TransactionTenantHolder {

    private static final String CONTEXT_KEY = "tenant-tx";
    private static final ThreadLocal<Long> TX_TENANT = new ThreadLocal<>();

    static {
        ContextRegistry.getInstance().registerThreadLocalAccessor(new ThreadLocalAccessor<Long>() {
            @Nonnull @Override public Object key() { return CONTEXT_KEY; }
            @Override public Long getValue() { return TX_TENANT.get(); }
            @Override public void setValue(@Nonnull Long value) { TX_TENANT.set(value); }
            @Override public void setValue() { TX_TENANT.remove(); }
        });
    }

    private TransactionTenantHolder() {
    }

    /**
     * 冻结当前事务的租户 ID。
     *
     * @param tenantId 租户 ID
     */
    public static void freeze(Long tenantId) {
        TX_TENANT.set(tenantId);
    }

    /**
     * 获取当前事务冻结的租户 ID。
     *
     * @return 冻结的租户 ID；非事务环境返回 {@code null}
     */
    public static Long get() {
        return TX_TENANT.get();
    }

    /**
     * 清理事务租户冻结状态（事务完成后调用）。
     */
    public static void clear() {
        TX_TENANT.remove();
    }

    /**
     * 检测目标租户是否与事务冻结的租户一致。
     *
     * @param targetTenantId 目标租户 ID
     * @throws TenantSwitchInTransactionException 事务内切换租户时
     */
    public static void checkSwitch(Long targetTenantId) {
        Long frozenTenant = TX_TENANT.get();
        if (frozenTenant != null && !frozenTenant.equals(targetTenantId)) {
            throw new TenantSwitchInTransactionException(frozenTenant, targetTenantId);
        }
    }
}
