package com.richie.component.cache.ops;

/**
 * 基数统计操作接口。
 * <p>对应底层 HyperLogLog 数据结构，提供近似去重计数能力（误差约 0.81%）。</p>
 *
 * @author richie696
 * @version 1.0.0
 * @since 2025-06-05
 */
public interface HyperLogOps {

    void add(String key, Object... values);

    long count(String key);
}
