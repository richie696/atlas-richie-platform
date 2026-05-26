package com.richie.component.cache.bloom;

import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 默认布隆过滤器
 *
 * @author richie696
 * @version 1.0
 * @since 2026-02-07 19:41:09
 */
@Component
public class DefaultBloomFilter implements BloomFilterFacade {

    /**
     * 是否包含KEY
     *
     * @param key key
     * @return boolean
     */
    @Override
    public boolean contains(String key) {
        return true;
    }

    /**
     * 添加KEY到布隆过滤器中
     *
     * @param key key
     */
    @Override
    public void add(String key) {
    }

    /**
     * 批量添加KEY
     *
     * @param keys 元素key列表
     */
    @Override
    public void addAll(Set<String> keys) {
    }

    /**
     * 判断布隆过滤器是否存在
     *
     * @return 返回布隆过滤器是否存在
     */
    @Override
    public boolean isExists() {
        return true;
    }
}
