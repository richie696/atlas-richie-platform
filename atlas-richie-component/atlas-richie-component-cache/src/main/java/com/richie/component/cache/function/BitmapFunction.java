package com.richie.component.cache.function;

/**
 * Bitmap相关API管理器接口，封装了Redis中Bitmap位操作的常用方法。
 * <p>
 * 主要用于高效地进行布尔标记、用户签到、唯一性统计等场景。
 * 支持设置和获取指定key的某个位（bit）值。
 * <p>
 * 推荐配合布隆过滤器等高性能场景使用。
 *
 * @author richie696
 * @version 1.0
 * @since 2025-06-25 17:38:43
 */
public interface BitmapFunction extends CacheFunction {
    /**
     * 设置指定key的某个位（bit）值。
     *
     * @param key    Redis键
     * @param offset 位偏移（从0开始）
     * @param value  位值（true/false）
     */
    void setBit(String key, long offset, boolean value);

    /**
     * 获取指定key的某个位（bit）值。
     *
     * @param key    Redis键
     * @param offset 位偏移（从0开始）
     * @return 指定位的布尔值，true表示1，false表示0
     */
    boolean getBit(String key, long offset);
}
