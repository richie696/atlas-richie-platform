package com.richie.component.mongodb.cache;

/**
 * 对象集合枚举
 *
 * @author richie696
 * @version 1.0
 * @since 2025-01-15 09:44:57
 */
public interface CollectionKey {

    /**
     * 返回集合键名（用于 ObjectCache 的集合标识）。
     *
     * @return 集合键字符串
     */
    String getKey();

}
