package com.richie.context.common.api.domain;

/**
 * 软删除标识接口
 *
 * <p>实现此接口表示实体支持逻辑删除。
 * 配合 MyBatis-Plus {@code @TableLogic} 注解使用，
 * 查询时自动追加 {@code deleted = 0} 条件过滤。</p>
 *
 * @author richie696
 * @since 1.0
 */
public interface SoftDeletable {

    /**
     * 获取逻辑删除标识
     *
     * @return {@code true} 已删除；{@code false} 未删除
     */
    Boolean getDeleted();

    /**
     * 设置逻辑删除标识
     */
    void setDeleted(Boolean deleted);

}
