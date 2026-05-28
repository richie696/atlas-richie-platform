package com.richie.component.statemachine.config.properties;

/**
 * 数据库持久化模式
 *
 * @author richie696
 * @since 1.0.0
 */
public enum DbPersistenceMode {
    /**
     * 异步持久化（默认）
     */
    ASYNC,
    /**
     * 同步持久化
     */
    SYNC
}

