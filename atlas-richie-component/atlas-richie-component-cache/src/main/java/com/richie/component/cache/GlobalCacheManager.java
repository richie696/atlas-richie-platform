package com.richie.component.cache;

import com.richie.component.cache.function.*;
import com.richie.component.cache.function.*;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.springframework.stereotype.Component;

/**
 * 缓存管理器
 *
 * @author richie696
 * @version 1.0.0
 * @since 2025-06-26 18:18:32
 */
@Component
@Getter
@Accessors(fluent = true)
public class GlobalCacheManager {

    /**
     * 构造函数，供 Spring 注入各功能管理器使用。
     *
     * @param string String 类型管理器
     * @param list List 类型管理器
     * @param set Set 类型管理器
     * @param hash Hash 类型管理器
     * @param zSet ZSet 类型管理器
     * @param geo Geo 类型管理器
     * @param stream Stream 类型管理器
     * @param hyperLog HyperLog 类型管理器
     * @param bitmap Bitmap 类型管理器
     * @param lua Lua 脚本管理器
     * @param limiter 限流管理器
     * @param event 事件管理器
     * @param notification 发布订阅管理器
     * @param lock 分布式锁管理器
     * @param key Key 管理器
     */
    public GlobalCacheManager(
            StringFunction string,
            ListFunction list,
            SetFunction set,
            HashFunction hash,
            ZSetFunction zSet,
            GeoFunction geo,
            StreamFunction stream,
            HyperLogFunction hyperLog,
            BitmapFunction bitmap,
            LuaFunction lua,
            LimiterFunction limiter,
            EventFunction event,
            NotificationFunction notification,
            LockFunction lock,
            KeyFunction key) {
        this.string = string;
        this.list = list;
        this.set = set;
        this.hash = hash;
        this.zSet = zSet;
        this.geo = geo;
        this.stream = stream;
        this.hyperLog = hyperLog;
        this.bitmap = bitmap;
        this.lua = lua;
        this.limiter = limiter;
        this.event = event;
        this.notification = notification;
        this.lock = lock;
        this.key = key;
    }

    /**
     * 获取 Redis String 类型相关操作的管理器。
     * <p>可用于字符串缓存、分布式锁等。
     */
    private final StringFunction string;

    /**
     * 获取 Redis List 类型相关操作的管理器。
     * <p>可用于队列、消息列表等。
     */
    private final ListFunction list;

    /**
     * 获取 Redis Set 类型相关操作的管理器。
     * <p>可用于集合去重、标签、关系等。
     */
    private final SetFunction set;

    /**
     * 获取 Redis Hash 类型相关操作的管理器。
     * <p>可用于对象缓存、属性映射等。
     */
    private final HashFunction hash;

    /**
     * 获取 Redis ZSet（有序集合）相关操作的管理器。
     * <p>可用于排行榜、分数排序等。
     */
    private final ZSetFunction zSet;

    /**
     * 获取 Redis 地理位置（Geo）相关操作的管理器。
     * <p>可用于地理位置的存储、查询、距离计算等。
     */
    private final GeoFunction geo;

    /**
     * 获取 Redis Stream 消息队列相关操作的管理器。
     * <p>可用于可靠消息队列、异步任务、日志收集等。
     */
    private final StreamFunction stream;

    /**
     * 获取 Redis HyperLogLog 相关操作的管理器。
     * <p>可用于基数统计、去重计数等。
     */
    private final HyperLogFunction hyperLog;

    /**
     * 获取 Redis 位图（Bitmap）相关操作的管理器。
     * <p>可用于布尔标记、签到、活跃统计等。
     */
    private final BitmapFunction bitmap;

    /**
     * 获取 Redis Lua 脚本相关操作的管理器。
     * <p>可用于原子操作、复杂事务等。
     */
    private final LuaFunction lua;

    /**
     * 获取 Redis 限流相关操作的管理器。
     * <p>可用于分布式限流、令牌桶等。
     */
    private final LimiterFunction limiter;

    /**
     * 获取 Redis 事件订阅相关操作的管理器。
     * <p>可用于 key 过期、删除等事件的监听与回调。
     */
    private final EventFunction event;

    /**
     * 获取 Redis 发布订阅（通知）相关操作的管理器。
     * <p>可用于事件通知、消息广播等。
     */
    private final NotificationFunction notification;

    /**
     * 获取 Redis 分布式锁相关操作的管理器。
     * <p>可用于分布式锁、互斥控制等。
     */
    private final LockFunction lock;

    /**
     * 获取 Redis Key 管理与元数据相关操作的管理器。
     * <p>可用于 key 的过期、重命名、类型判断、批量操作等。
     */
    private final KeyFunction key;

}
