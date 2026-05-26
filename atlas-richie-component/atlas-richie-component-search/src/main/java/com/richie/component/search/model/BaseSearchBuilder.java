package com.richie.component.search.model;

import java.util.Collection;

/**
 * 基础搜索构建器接口
 *
 * <p>定义所有搜索构建器的基础方法，包括基础条件查询、排序和分页。
 *
 * @param <T> 实体类型
 * @author richie696
 * @version 1.0
 * @since 2025-08-12
 */
public interface BaseSearchBuilder<T> {

    // ==================== 基础条件查询方法组 ====================

    /**
     * 等于条件
     */
    BaseSearchBuilder<T> eq(CFunction<T, ?> column, Object value);

    /**
     * 不等于条件
     */
    BaseSearchBuilder<T> ne(CFunction<T, ?> column, Object value);

    /**
     * IN 条件
     */
    BaseSearchBuilder<T> in(CFunction<T, ?> column, Collection<?> values);

    /**
     * NOT IN 条件
     */
    BaseSearchBuilder<T> notIn(CFunction<T, ?> column, Collection<?> values);

    /**
     * 大于条件
     */
    BaseSearchBuilder<T> gt(CFunction<T, ?> column, Object value);

    /**
     * 大于等于条件
     */
    BaseSearchBuilder<T> ge(CFunction<T, ?> column, Object value);

    /**
     * 小于条件
     */
    BaseSearchBuilder<T> lt(CFunction<T, ?> column, Object value);

    /**
     * 小于等于条件
     */
    BaseSearchBuilder<T> le(CFunction<T, ?> column, Object value);

    /**
     * 范围条件
     */
    BaseSearchBuilder<T> between(CFunction<T, ?> column, Object from, Object to);

    /**
     * 模糊查询条件
     */
    BaseSearchBuilder<T> like(CFunction<T, ?> column, String value);

    /**
     * 存在条件
     */
    BaseSearchBuilder<T> exists(CFunction<T, ?> column);

    /**
     * 不存在条件
     */
    BaseSearchBuilder<T> notExists(CFunction<T, ?> column);

    // ==================== 排序和分页方法组 ====================

    /**
     * 升序排序
     */
    BaseSearchBuilder<T> orderByAsc(CFunction<T, ?> column);

    /**
     * 降序排序
     */
    BaseSearchBuilder<T> orderByDesc(CFunction<T, ?> column);

    /**
     * 分页设置
     */
    BaseSearchBuilder<T> page(int page, int size);

    BaseSearchBuilder<T> highlight(String... fields);

    BaseSearchBuilder<T> highlight(CFunction<T, ?>... columns);

    BaseSearchBuilder<T> suggest(CFunction<T, ?> column, String keyword);

    /**
     * 建议查询设置
     */
    BaseSearchBuilder<T> suggest(String fieldName, String keyword);

    /**
     * 添加实体条件
     * @param entity 实体对象
     */
    void addEntityConditions(T entity);

    /**
     * 构建查询对象
     */
    SearchQueryWrapper<T> build();

}
