package com.richie.component.cache;

import com.richie.component.cache.ops.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.stereotype.Component;

/**
 * 缓存管理器
 *
 * @author richie696
 * @version 2.0.0
 * @since 2025-06-26 18:18:32
 */
@Component
@RequiredArgsConstructor
@Getter
@Accessors(fluent = true)
public class GlobalCacheManager {

    // ── Ops 访问器 ──

    /** Lua 脚本管理器 */
    private final ScriptOps script;

    /** 限流管理器 */
    private final LimiterOps limiter;

    /** Key 管理器 */
    private final KeyOps key;

    /** 有界队列管理器 */
    private final BoundedQueueOps queue;

    /** 有界栈管理器 */
    private final BoundedStackOps stack;

    /** KV 缓存 + 计数器操作 */
    private final ValueOps value;

    /** 结构化对象缓存操作 */
    private final StructOps struct;

    /** Hash 字段级存取操作 */
    private final FieldOps field;

    /** 无序集合操作 */
    private final CollectionOps collection;

    /** 有序集合/排行榜操作 */
    private final RankingOps ranking;

    /** 分布式锁操作 */
    private final LockOps lock;

    /** 位图操作 */
    private final BitmapOps bitmap;

    /** 基数统计操作 */
    private final HyperLogOps hyperLog;

    /** 地理位置操作 */
    private final GeoOps geo;

    /** 获取发布订阅通知操作 */
    private final NotificationOps notification;

}
