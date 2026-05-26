package com.richie.context.common.api.domain;

/**
 * 租户感知接口
 *
 * <p>实现此接口表示实体支持多租户数据隔离。
 * MyBatis-Plus 多租户插件通过此接口识别需要添加租户条件过滤的实体。</p>
 *
 * @author richie696
 * @since 1.0
 */
public interface TenantAware {

    /**
     * 获取租户 ID
     */
    Long getTenantId();

    /**
     * 设置租户 ID
     */
    void setTenantId(Long tenantId);

}
