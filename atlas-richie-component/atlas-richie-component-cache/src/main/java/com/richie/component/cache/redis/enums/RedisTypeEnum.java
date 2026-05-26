package com.richie.component.cache.redis.enums;

/**
 * Redis服务器类型枚举
 *
 * @author richie696
 * @version 1.0
 * @since 2022-10-01 21:42:59
 */
public enum RedisTypeEnum {

    /**
     * 单机模式
     */
    STANDALONE,
    /**
     * 哨兵模式
     */
    SENTINEL,
    /**
     * 集群模式
     */
    CLUSTER

}
