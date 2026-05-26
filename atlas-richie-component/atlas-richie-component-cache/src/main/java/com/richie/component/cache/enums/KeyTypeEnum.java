package com.richie.component.cache.enums;

/**
 * 缓存键类型枚举
 *
 * @author richie696
 * @version 1.0
 * @since 2025-06-16 02:19:41
 */
public enum KeyTypeEnum {

    /**
     * Redis String 类型，适用于存储简单的字符串值
     */
    STRING,

    /**
     * Redis Hash 类型，适用于存储键值对集合的场景
     */
    HASH,

    /**
     * Redis List 类型，适用于需要有序列表的场景
     */
    LIST,

    /**
     * Redis Set 类型，适用于需要唯一性和集合操作的场景
     */
    SET

}
