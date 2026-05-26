package com.richie.component.cache.bloom;

import java.util.Set;

/**
 * 布隆过滤器接口
 *
 * @author richie696
 * @version 1.0
 * @since 2025-06-25 14:35:09
 */
public interface BloomFilterFacade {
    /**
     * 判断元素是否存在于布隆过滤器中
     * @param key 元素key
     * @return 是否存在
     */
    boolean contains(String key);

    /**
     * 添加元素到布隆过滤器
     * @param key 元素key
     */
    void add(String key);

    /**
     * 批量添加元素
     * @param keys 元素key列表
     */
    void addAll(Set<String> keys);

    /**
     * 是否已存在过滤器中
     * @return 是否存在
     */
    boolean isExists();
}
