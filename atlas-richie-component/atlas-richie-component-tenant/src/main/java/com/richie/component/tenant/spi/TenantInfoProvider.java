package com.richie.component.tenant.spi;

import com.richie.component.tenant.model.TenantInfo;

/**
 * 租户信息提供者 SPI。
 *
 * <p>由各接入方实现（通常从 {@code sys_tenant} 表或缓存中读取），
 * 为策略层、拦截器层提供租户运行时信息。</p>
 *
 * <p>默认提供 {@code @ConditionalOnMissingBean} 的 NoOp 实现，
 * 接入方可通过 Spring Bean 覆盖。</p>
 *
 * @author richie696
 * @since 2.0
 */
public interface TenantInfoProvider {

    /**
     * 获取指定租户的运行时信息。
     *
     * @param tenantId 租户 ID（Long 类型）
     * @return 租户信息；如果租户不存在则返回 {@code null}
     */
    TenantInfo getTenantInfo(Long tenantId);

    /**
     * 判断指定租户是否存在。
     *
     * @param tenantId 租户 ID（Long 类型）
     * @return 存在返回 {@code true}
     */
    boolean exists(Long tenantId);
}
