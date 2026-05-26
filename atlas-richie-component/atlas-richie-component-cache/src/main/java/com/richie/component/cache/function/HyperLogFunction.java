package com.richie.component.cache.function;

/**
 * HyperLogLog相关API管理器，封装了Redis中HyperLogLog数据结构的常用操作。
 * <p>
 * 主要用于大规模基数统计（如UV、去重计数）等场景，具有极低内存消耗和可接受误差。
 *
 * @author richie696
 * @version 1.0
 * @since 2025-06-25 17:47:07
 */
public interface HyperLogFunction extends CacheFunction {

    /**
     * 向HyperLogLog添加元素。
     *
     * @param key    HyperLogLog的键
     * @param values 要添加的元素，可变参数
     */
    void pfAdd(String key, Object... values);

    /**
     * 获取HyperLogLog的基数估算值。
     *
     * @param key HyperLogLog的键
     * @return 基数估算值（long类型）
     */
    long pfCount(String key);
}
