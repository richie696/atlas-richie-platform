package com.richie.component.statemachine.config.properties;

/**
 * 存储类型枚举
 */
public enum StorageType {
    /**
     * Redis 存储模式
     * <p>
     * 使用 Redis 作为状态存储，需要 Redis 支持。
     * 支持多实例部署、分布式消费组、Redis Stream 异步持久化等特性。
     * 
     */
    REDIS,

    /**
     * 异步线程存储模式
     * <p>
     * 直接使用异步线程池持久化到数据库，不依赖 Redis。
     * 适合无法使用 Redis 或 Redis 版本过低的场景。
     * 单实例内批量处理，不支持多实例部署。
     * 
     */
    ASYNC_THREAD
}
