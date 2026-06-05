package com.richie.component.cache.ops;

/**
 * 位图操作接口。
 * <p>对应底层 Bitmap 数据结构，提供偏移量级的位读写能力。</p>
 *
 * @author richie696
 * @version 1.0.0
 * @since 2025-06-05
 */
public interface BitmapOps {

    void set(String key, long offset, boolean value);

    boolean get(String key, long offset);
}
