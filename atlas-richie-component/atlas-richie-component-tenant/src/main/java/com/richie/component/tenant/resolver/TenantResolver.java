package com.richie.component.tenant.resolver;

import com.richie.contract.model.TenantPrincipal;

/**
 * 租户解析器 SPI
 *
 * <p>由各接入方（Gateway、Auth Service）实现，负责从不同来源
 * （JWT、Header、Session 等）解析当前请求的租户信息。</p>
 *
 * <p>默认实现为 {@code TenantJwtResolver}（Phase 2 提供），
 * 也可通过 Spring {@code @Primary} 或 {@code @ConditionalOnMissingBean} 替换。</p>
 *
 * @author richie696
 * @since 1.0
 */
@FunctionalInterface
public interface TenantResolver {

    /**
     * 解析当前请求/线程的租户信息
     *
     * @return 租户主体信息；如果无法解析或无租户上下文则返回 {@code null}
     */
    TenantPrincipal resolve();
}
