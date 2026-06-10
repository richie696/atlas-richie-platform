package com.richie.contract.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * 租户主体信息
 *
 * <p>独立于 {@code LoginUserPrincipal}，tenant 与 user 是正交概念。
 * 通过 {@code TenantContextHolder} 在线程间传递，不侵入用户认证上下文。</p>
 *
 * @author richie696
 * @since 1.0
 */
@Data
@Accessors(chain = true)
public class TenantPrincipal implements Serializable {

    /**
     * 租户 ID（数据库主键，Long 类型）
     */
    private Long tenantId;

    /**
     * 租户展示名称（用于日志、审计展示）
     */
    private String tenantName;

    /**
     * 租户过期时间
     */
    private OffsetDateTime expiredTime;
}
