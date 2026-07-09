/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.cache;

import com.richie.component.cache.ops.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 全局缓存静态门面，委托 {@link GlobalCacheManager} 访问 Redis。
 * <p><b>复杂度总序（从优到劣）</b>：{@code O(1) > O(log n) > O(n) > O(n log n) > O(n²) > O(2ⁿ) > …}
 * <p><b>ToC 策略</b>：
 * <ul>
 *   <li><b>O(1)</b>：最优，<b>核心链路强制推荐</b>；文档中标注为常数时间的方法默认适用于高并发读/写。</li>
 *   <li><b>O(log n)</b>：有序集合（如 ZSet）相关，<b>可控范围</b>内可用（需限制成员量、区间宽度）。</li>
 *   <li><b>O(n)</b>：与元素数或扫描范围相关，<b>ToC 高并发在线链路严格禁用</b>；仅允许离线、管理端、低频任务等明确场景。</li>
 *   <li><b>更差（O(n log n)、O(n²)、指数级等）</b>：<b>严禁</b>在 ToC 核心路径使用。</li>
 * </ul>
 * <p>下列各 {@code public static} 方法注释中的复杂度指<b>底层 Redis 命令的主导项</b>；业务层若在循环中重复调用，整体代价为 O(次数 × 单次复杂度)。
 * <p>L2 本地缓存开启时，读路径可能命中 JVM 本地，实际耗时以观测为准，但<b> Redis 侧复杂度不变</b>。
 * <p><b>类型化数据操作</b>请通过 Ops 访问器调用：
 * <pre>{@code
 *   GlobalCache.value().set("key", value);        // KV / 计数器
 *   GlobalCache.struct().get("key", MyClass.class); // 结构化对象
 *   GlobalCache.field().set("key", "field", val);  // Hash 字段
 *   GlobalCache.collection().add("key", item);     // Set
 *   GlobalCache.ranking().incrementScore(...);     // ZSet
 *   GlobalCache.lock().optimistic("key");       // 分布式锁
 *   GlobalCache.bitmap().set("key", 1, true);   // 位图
 *   GlobalCache.hyperLog().add("key", val);     // 基数统计
 *   GlobalCache.geo().add("key", lng, lat, m);  // 地理位置
 * }</pre>
 *
 * @author richie696
 * @version 1.0.0
 * @since 2026/06/04
 */
@Component
public class GlobalCache {

    private static final AtomicReference<GlobalCacheManager> DELEGATE = new AtomicReference<>();

    private GlobalCache() {
    }

    // ========================================================================
    //  Ops 访问器 — 类型化数据操作
    // ========================================================================

    /** KV 缓存 + 计数器操作 */
    public static ValueOps value() {
        return DELEGATE.get().value();
    }

    /** 结构化对象缓存操作（整体存取 JavaBean） */
    public static StructOps struct() {
        return DELEGATE.get().struct();
    }

    /** Hash 字段级存取操作 */
    public static FieldOps field() {
        return DELEGATE.get().field();
    }

    /** 无序集合操作 */
    public static CollectionOps collection() {
        return DELEGATE.get().collection();
    }

    /** 有序集合 / 排行榜操作 */
    public static RankingOps ranking() {
        return DELEGATE.get().ranking();
    }

    /** 分布式锁操作 */
    public static LockOps lock() {
        return DELEGATE.get().lock();
    }

    /** 位图操作 */
    public static BitmapOps bitmap() {
        return DELEGATE.get().bitmap();
    }

    /** 基数统计操作 */
    public static HyperLogOps hyperLog() {
        return DELEGATE.get().hyperLog();
    }

    /** 地理位置操作 */
    public static GeoOps geo() {
        return DELEGATE.get().geo();
    }

    /**
     * 有界队列（FIFO，满时淘汰队首；主动拉消费，削峰缓冲，非 Stream MQ）。
     */
    public static BoundedQueueOps queue() {
        return DELEGATE.get().queue();
    }

    /** 有界栈（LIFO，满时拒绝压入；主动拉，非消息队列） */
    public static BoundedStackOps stack() {
        return DELEGATE.get().stack();
    }

    /** Key 元操作（过期时间、存在判断、删除、重命名等） */
    public static KeyOps key() {
        return DELEGATE.get().key();
    }

    /**
     * 脚本原子操作API管理器接口。
     *
     * @return 返回Lua脚本管理器
     */
    public static ScriptOps script() {
        return DELEGATE.get().script();
    }

    /**
     * 分布式限流API管理器，封装了滑动窗口限流算法
     *
     * @return 返回分布式限流管理器
     */
    public static LimiterOps limiter() {
        return DELEGATE.get().limiter();
    }

    /**
     * 发布订阅（Pub/Sub）通知操作
     *
     * @return 返回发布订阅（Pub/Sub）通知操作管理器
     */
    public static NotificationOps notification() {
        return DELEGATE.get().notification();
    }

    /**
     * Redis Key 空间事件订阅（过期、删除等）。
     * <p>轻量 Pub/Sub 发布请用 {@link #notification()}；可靠消息队列请用独立模块 {@code atlas-richie-component-redis-streammq} 的 {@code StreamMQ}。</p>
     */
    public static EventOps event() {
        return DELEGATE.get().event();
    }

    // ========================================================================
    //  Spring 注入
    // ========================================================================

    @Autowired
    public void setRedisCacheManager(GlobalCacheManager redisCacheManager) {
        if (GlobalCache.DELEGATE.get() == null) {
            synchronized (GlobalCache.class) {
                if (GlobalCache.DELEGATE.get() == null) {
                    GlobalCache.DELEGATE.set(redisCacheManager);
                }
            }
        }
    }
}
