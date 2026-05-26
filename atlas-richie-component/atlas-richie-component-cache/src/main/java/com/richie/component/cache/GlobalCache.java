package com.richie.component.cache;

import com.richie.component.cache.commons.GeoPointResult;
import com.richie.component.cache.enums.KeyTypeEnum;
import com.richie.component.cache.function.StreamFunction;
import com.richie.component.cache.local.manage.LocalCache;
import com.richie.component.cache.redis.manage.CacheBatchLock;
import com.richie.component.cache.redis.manage.CacheLock;
import jakarta.annotation.Nonnull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.geo.Distance;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import static com.richie.component.cache.enums.KeyTypeEnum.*;
import static com.richie.component.cache.enums.L2CachingRegion.GLOBAL_CACHE;

/**
 * 全局缓存静态门面，委托 {@link GlobalCacheManager} 与各 {@code *Function} 访问 Redis。
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
 *
 * @author richie696
 * @version 1.3
 * @since 2021/12/05
 */

@Component
public class GlobalCache {

    private static final AtomicReference<GlobalCacheManager> DELEGATE = new AtomicReference<>();


    private GlobalCache() {
    }

    /**
     * 检查是否启用Redis二级缓存的方法
     *
     * @return 如果启用Redis二级缓存则返回true，否则返回false
     * @apiNote <p><b>时间复杂度</b>：O(1)（读配置/本地判定，无 Redis 或极轻量）。
     * <p><b>ToC 严禁</b>：高频轮询本方法替代业务缓存（应缓存配置结果）。
     * <p><b>可使用</b>：任意链路少量调用。
     * <p><b>注意</b>：结果依赖运行时配置与 Spring 注入完成时机。
     */
    public static boolean enableL2Caching() {
        return DELEGATE.get().key().enableL2Caching();
    }

    /**
     * 检查指定数据类型是否开启二级缓存的方法
     *
     * @param keyType 二级缓存数据类型
     * @return 返回是否启用二级缓存（true：启用，false：不启用）
     * @apiNote <p><b>时间复杂度</b>：O(1)（读配置/本地判定，无 Redis 或极轻量）。
     * <p><b>ToC 严禁</b>：高频轮询本方法替代业务缓存（应缓存配置结果）。
     * <p><b>可使用</b>：任意链路少量调用。
     * <p><b>注意</b>：结果依赖运行时配置与 Spring 注入完成时机。
     */
    public static boolean enableKeyTypeCache(KeyTypeEnum keyType) {
        return DELEGATE.get().key().enableKeyTypeCache(keyType);
    }

    /**
     * 获取指定缓存过期时间的方法
     *
     * @param key 缓存键
     * @return 返回缓存过期时间（单位：毫秒）
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     */
    public static Long getExpiredTime(String key) {
        return DELEGATE.get().key().getExpire(key);
    }

    /**
     * 存储整型数值到缓存的方法<b style="color: red">（缓存永不过期，慎用！）</b>
     *
     * @param key   缓存键
     * @param value 缓存值
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     */
    public static void addIntCache(String key, int value) {
        DELEGATE.get().string().addValue(key, value);
        // 同步更新本地缓存
        if (enableL2Caching() && enableKeyTypeCache(STRING)) {
            LocalCache.put(GLOBAL_CACHE, key, value);
        }
    }

    /**
     * 如果缓存中不存在则存储整型数值到缓存的方法<b style="color: red">（缓存永不过期，慎用！）</b>
     *
     * @param key   缓存键
     * @param value 缓存值
     * @return 返回是否添加成功
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     */
    public static boolean addIntCacheIfAbsent(String key, int value) {
        boolean result = DELEGATE.get().string().addValueIfAbsent(key, value);
        if (result && enableL2Caching() && enableKeyTypeCache(STRING)) {
            // 同步更新本地缓存
            LocalCache.put(GLOBAL_CACHE, key, value);
        }
        return result;
    }

    /**
     * 存储整型数值到缓存的方法（指定时长超时后对象将自动销毁）
     *
     * @param key     缓存键
     * @param value   缓存值
     * @param timeout 超时时长（单位：毫秒）
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     */
    public static void addIntCache(String key, int value, long timeout) {
        DELEGATE.get().string().addValue(key, value, timeout);
        if (enableL2Caching() && enableKeyTypeCache(STRING)) {
            // 同步更新本地缓存
            LocalCache.put(GLOBAL_CACHE, key, value);
            LocalCache.expiry(GLOBAL_CACHE, key, timeout, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * 如果缓存中不存在则存储整型数值到缓存的方法
     *
     * @param key     缓存键
     * @param value   缓存值
     * @param timeout 超时时长（单位：毫秒）
     * @return 返回是否添加成功
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     */
    public static boolean addIntCacheIfAbsent(String key, int value, long timeout) {
        var result = DELEGATE.get().string().addValueIfAbsent(key, value, timeout);
        if (result && enableL2Caching() && enableKeyTypeCache(STRING)) {
            // 同步更新本地缓存
            LocalCache.put(GLOBAL_CACHE, key, value);
            LocalCache.expiry(GLOBAL_CACHE, key, timeout, TimeUnit.MILLISECONDS);
        }
        return result;
    }

    /**
     * 存储长整型数值到缓存的方法<b style="color: red">（缓存永不过期，慎用！）</b>
     *
     * @param key   缓存键
     * @param value 缓存值
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     */
    public static void addLongCache(String key, long value) {
        DELEGATE.get().string().addValue(key, value);
        if (enableL2Caching() && enableKeyTypeCache(STRING)) {
            // 同步更新本地缓存
            LocalCache.put(GLOBAL_CACHE, key, value);
        }
    }

    /**
     * 如果缓存中不存在则存储长整型数值到缓存的方法<b style="color: red">（缓存永不过期，慎用！）</b>
     *
     * @param key   缓存键
     * @param value 缓存值
     * @return 返回是否添加成功
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     */
    public static boolean addLongCacheIfAbsent(String key, long value) {
        var result = DELEGATE.get().string().addValueIfAbsent(key, value);
        if (result && enableL2Caching() && enableKeyTypeCache(STRING)) {
            // 同步更新本地缓存
            LocalCache.put(GLOBAL_CACHE, key, value);
        }
        return result;
    }

    /**
     * 存储长整型数值到缓存的方法（指定时长超时后对象将自动销毁）
     *
     * @param key     缓存键
     * @param value   缓存值
     * @param timeout 超时时长（单位：毫秒）
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     */
    public static void addLongCache(String key, long value, long timeout) {
        DELEGATE.get().string().addValue(key, value, timeout);
        if (enableL2Caching() && enableKeyTypeCache(STRING)) {
            // 同步更新本地缓存
            LocalCache.put(GLOBAL_CACHE, key, value);
            LocalCache.expiry(GLOBAL_CACHE, key, timeout, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * 如果缓存中不存在则存储长整型数值到缓存的方法
     *
     * @param key     缓存键
     * @param value   缓存值
     * @param timeout 超时时长（单位：毫秒）
     * @return 返回是否添加成功
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     */
    public static boolean addLongCacheIfAbsent(String key, long value, long timeout) {
        var result = DELEGATE.get().string().addValueIfAbsent(key, value, timeout);
        if (result && enableL2Caching() && enableKeyTypeCache(STRING)) {
            // 同步更新本地缓存
            LocalCache.put(GLOBAL_CACHE, key, value);
            LocalCache.expiry(GLOBAL_CACHE, key, timeout, TimeUnit.MILLISECONDS);
        }
        return result;
    }

    /**
     * 存储浮点型数值到缓存的方法<b style="color: red">（缓存永不过期，慎用！）</b>
     *
     * @param key   缓存键
     * @param value 缓存值
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     */
    public static void addFloatCache(String key, float value) {
        DELEGATE.get().string().addValue(key, value);
        if (enableL2Caching() && enableKeyTypeCache(STRING)) {
            // 同步更新本地缓存
            LocalCache.put(GLOBAL_CACHE, key, value);
        }
    }

    /**
     * 如果缓存中不存在则存储浮点型数值到缓存的方法<b style="color: red">（缓存永不过期，慎用！）</b>
     *
     * @param key   缓存键
     * @param value 缓存值
     * @return 返回是否添加成功
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     */
    public static boolean addFloatCacheIfAbsent(String key, float value) {
        var result = DELEGATE.get().string().addValueIfAbsent(key, value);
        if (result && enableL2Caching() && enableKeyTypeCache(STRING)) {
            // 同步更新本地缓存
            LocalCache.put(GLOBAL_CACHE, key, value);
        }
        return result;
    }

    /**
     * 存储浮点型数值到缓存的方法（指定时长超时后对象将自动销毁）
     *
     * @param key     缓存键
     * @param value   缓存值
     * @param timeout 超时时长（单位：毫秒）
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     */
    public static void addFloatCache(String key, float value, long timeout) {
        DELEGATE.get().string().addValue(key, value, timeout);
        if (enableL2Caching() && enableKeyTypeCache(STRING)) {
            // 同步更新本地缓存
            LocalCache.put(GLOBAL_CACHE, key, value);
            LocalCache.expiry(GLOBAL_CACHE, key, timeout, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * 如果缓存中不存在则存储浮点型数值到缓存的方法
     *
     * @param key     缓存键
     * @param value   缓存值
     * @param timeout 超时时长（单位：毫秒）
     * @return 返回是否添加成功
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     */
    public static boolean addFloatCacheIfAbsent(String key, float value, long timeout) {
        var result = DELEGATE.get().string().addValueIfAbsent(key, value, timeout);
        if (result && enableL2Caching() && enableKeyTypeCache(STRING)) {
            // 同步更新本地缓存
            LocalCache.put(GLOBAL_CACHE, key, value);
            LocalCache.expiry(GLOBAL_CACHE, key, timeout, TimeUnit.MILLISECONDS);
        }
        return result;
    }

    /**
     * 存储双精度浮点型数值到缓存的方法<b style="color: red">（缓存永不过期，慎用！）</b>
     *
     * @param key   缓存键
     * @param value 缓存值
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     */
    public static void addDoubleCache(String key, double value) {
        DELEGATE.get().string().addValue(key, value);
        if (enableL2Caching() && enableKeyTypeCache(STRING)) {
            // 同步更新本地缓存
            LocalCache.put(GLOBAL_CACHE, key, value);
        }
    }

    /**
     * 如果缓存中不存在则存储双精度浮点型数值到缓存的方法<b style="color: red">（缓存永不过期，慎用！）</b>
     *
     * @param key   缓存键
     * @param value 缓存值
     * @return 是否成功写入（true 表示原先不存在且已写入）
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     */
    public static boolean addDoubleCacheIfAbsent(String key, double value) {
        var result = DELEGATE.get().string().addValueIfAbsent(key, value);
        if (result && enableL2Caching() && enableKeyTypeCache(STRING)) {
            // 同步更新本地缓存
            LocalCache.put(GLOBAL_CACHE, key, value);
        }
        return result;
    }

    /**
     * 存储双精度浮点型数值到缓存的方法（指定时长超时后对象将自动销毁）
     *
     * @param key     缓存键
     * @param value   缓存值
     * @param timeout 超时时长（单位：毫秒）
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     */
    public static void addDoubleCache(String key, double value, long timeout) {
        DELEGATE.get().string().addValue(key, value, timeout);
        if (enableL2Caching() && enableKeyTypeCache(STRING)) {
            // 同步更新本地缓存
            LocalCache.put(GLOBAL_CACHE, key, value);
            LocalCache.expiry(GLOBAL_CACHE, key, timeout, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * 如果缓存中不存在则存储双精度浮点型数值到缓存的方法
     *
     * @param key     缓存键
     * @param value   缓存值
     * @param timeout 超时时长（单位：毫秒）
     * @return 是否成功写入（true 表示原先不存在且已写入）
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     */
    public static boolean addDoubleCacheIfAbsent(String key, double value, long timeout) {
        var result = DELEGATE.get().string().addValueIfAbsent(key, value, timeout);
        if (result && enableL2Caching() && enableKeyTypeCache(STRING)) {
            // 同步更新本地缓存
            LocalCache.put(GLOBAL_CACHE, key, value);
            LocalCache.expiry(GLOBAL_CACHE, key, timeout, TimeUnit.MILLISECONDS);
        }
        return result;
    }

    /**
     * 存储布尔型变量到缓存的方法<b style="color: red">（缓存永不过期，慎用！）</b>
     *
     * @param key   缓存键
     * @param value 缓存值
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     */
    public static void addBooleanCache(String key, boolean value) {
        DELEGATE.get().string().addValue(key, value);
        if (enableL2Caching() && enableKeyTypeCache(STRING)) {
            // 同步更新本地缓存
            LocalCache.put(GLOBAL_CACHE, key, value);
        }
    }

    /**
     * 如果缓存中不存在则存储布尔型变量到缓存的方法<b style="color: red">（缓存永不过期，慎用！）</b>
     *
     * @param key   缓存键
     * @param value 缓存值
     * @return 是否成功写入（true 表示原先不存在且已写入）
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     */
    public static boolean addBooleanCacheIfAbsent(String key, boolean value) {
        var result = DELEGATE.get().string().addValueIfAbsent(key, value);
        if (result && enableL2Caching() && enableKeyTypeCache(STRING)) {
            // 同步更新本地缓存
            LocalCache.put(GLOBAL_CACHE, key, value);
        }
        return result;
    }

    /**
     * 存储布尔型变量到缓存的方法（指定时长超时后对象将自动销毁）
     *
     * @param key     缓存键
     * @param value   缓存值
     * @param timeout 超时时长（单位：毫秒）
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     */
    public static void addBooleanCache(String key, boolean value, long timeout) {
        DELEGATE.get().string().addValue(key, value, timeout);
        if (enableL2Caching() && enableKeyTypeCache(STRING)) {
            // 同步更新本地缓存
            LocalCache.put(GLOBAL_CACHE, key, value);
            LocalCache.expiry(GLOBAL_CACHE, key, timeout, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * 如果缓存中不存在则存储布尔型变量到缓存的方法
     *
     * @param key     缓存键
     * @param value   缓存值
     * @param timeout 超时时长（单位：毫秒）
     * @return 返回是否添加成功
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     */
    public static boolean addBooleanCacheIfAbsent(String key, boolean value, long timeout) {
        var result = DELEGATE.get().string().addValueIfAbsent(key, value, timeout);
        if (result && enableL2Caching() && enableKeyTypeCache(STRING)) {
            // 同步更新本地缓存
            LocalCache.put(GLOBAL_CACHE, key, value);
            LocalCache.expiry(GLOBAL_CACHE, key, timeout, TimeUnit.MILLISECONDS);
        }
        return result;
    }

    /**
     * 存储对象的到缓存的方法<b style="color: red">（缓存永不过期，慎用！）</b>
     *
     * @param key   缓存键
     * @param value 缓存值
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     */
    public static void addStringCache(String key, String value) {
        DELEGATE.get().string().addValue(key, value);
        if (enableL2Caching() && enableKeyTypeCache(STRING)) {
            // 同步更新本地缓存
            LocalCache.put(GLOBAL_CACHE, key, value);
        }
    }

    /**
     * 如果缓存中不存在则存储对象的到缓存的方法<b style="color: red">（缓存永不过期，慎用！）</b>
     *
     * @param key   缓存键
     * @param value 缓存值
     * @return 返回是否添加成功
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     */
    public static boolean addStringCacheIfAbsent(String key, String value) {
        var result = DELEGATE.get().string().addValueIfAbsent(key, value);
        if (enableL2Caching() && enableKeyTypeCache(STRING)) {
            // 同步更新本地缓存
            LocalCache.put(GLOBAL_CACHE, key, value);
        }
        return result;
    }

    /**
     * 存储字符串的到缓存的方法（指定时长超时后对象将自动销毁）
     *
     * @param key     缓存键
     * @param value   缓存值
     * @param timeout 超时时长（单位：毫秒）
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     */
    public static void addStringCache(String key, String value, long timeout) {
        DELEGATE.get().string().addValue(key, value, timeout);
        if (enableL2Caching() && enableKeyTypeCache(STRING)) {
            // 同步更新本地缓存
            LocalCache.put(GLOBAL_CACHE, key, value);
            LocalCache.expiry(GLOBAL_CACHE, key, timeout, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * 如果缓存中不存在则存储对象的到缓存的方法
     *
     * @param key     缓存键
     * @param value   缓存值
     * @param timeout 超时时长（单位：毫秒）
     * @return 是否成功写入（true 表示原先不存在且已写入）
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     */
    public static boolean addStringCacheIfAbsent(String key, String value, long timeout) {
        var result = DELEGATE.get().string().addValueIfAbsent(key, value, timeout);
        if (result && enableL2Caching() && enableKeyTypeCache(STRING)) {
            // 同步更新本地缓存
            LocalCache.put(GLOBAL_CACHE, key, value);
            LocalCache.expiry(GLOBAL_CACHE, key, timeout, TimeUnit.MILLISECONDS);
        }
        return result;
    }

    /**
     * 存储对象的到缓存的方法（指定时长超时后对象将自动销毁）
     *
     * @param key     缓存键
     * @param value   缓存值
     * @param timeout 超时时长（单位：毫秒）
     * @apiNote <p><b>时间复杂度</b>：写入对象展开为 Hash 时与字段数相关，通常为 O(k)，k 为字段数。
     * <p><b>ToC 严禁</b>：高并发下大批量 key、全量集合、无界扫描。
     * <p><b>可使用</b>：批量有上界、离线/管理端、明确 SLA 的工具任务。
     * <p><b>注意</b>：网络与反序列化随返回体量线性增长；警惕 BIGKEY。
     */
    public static void addObjectToHash(String key, Object value, long timeout) {
        DELEGATE.get().hash().addObject(key, value, timeout);
        if (enableL2Caching() && enableKeyTypeCache(HASH)) {
            registerType(key, value.getClass());
            // 同步更新本地缓存
            LocalCache.put(GLOBAL_CACHE, key, value);
            LocalCache.expiry(GLOBAL_CACHE, key, timeout, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * 刷新缓存数据的方法
     *
     * @param key  缓存键
     * @param func 更新缓存的操作
     * @param <T>  期望转换的目标对象类型（如果目标类型与实际存储的Json对象不匹配则会抛出转换异常）
     * @return 返回刷新后的缓存对象
     * @apiNote <p><b>时间复杂度</b>：与元素数或批量规模相关，通常为 O(n) 或 O(k)；ToC 高并发慎用全量。
     * <p><b>ToC 严禁</b>：高并发下大批量 key、全量集合、无界扫描。
     * <p><b>可使用</b>：批量有上界、离线/管理端、明确 SLA 的工具任务。
     * <p><b>注意</b>：网络与反序列化随返回体量线性增长；警惕 BIGKEY。
     */
    public static <T> T refreshObjectCache(String key, UnaryOperator<T> func) {
        return DELEGATE.get().hash().refreshObject(key, func);
    }

    /**
     * 添加缓存到Map数据结构中的方法
     *
     * @param key       缓存键
     * @param hashKey   Hash键
     * @param hashValue Hash值
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。 单 field 写入。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     */
    public static void addCache2Hash(String key, String hashKey, Object hashValue) {
        DELEGATE.get().hash().addHash(key, hashKey, hashValue);
        // 同步更新本地缓存
        if (enableL2Caching() && enableKeyTypeCache(HASH)) {
            Map<String, Object> map = LocalCache.get(GLOBAL_CACHE, key);
            if (map == null) {
                map = new HashMap<>();
            }
            map.put(hashKey, hashValue);
            LocalCache.put(GLOBAL_CACHE, key, map);
        }
    }

    /**
     * 将map中所有的数据缓存到Map数据结构得方法，整体失效。
     *
     * @param key     缓存键
     * @param map     缓存内容Hash键，Hash值
     * @param timeout 超时时长（单位：毫秒）
     * @apiNote <p><b>时间复杂度</b>：与元素数或批量规模相关，通常为 O(n) 或 O(k)；ToC 高并发慎用全量。
     * <p><b>ToC 严禁</b>：高并发下大批量 key、全量集合、无界扫描。
     * <p><b>可使用</b>：批量有上界、离线/管理端、明确 SLA 的工具任务。
     * <p><b>注意</b>：网络与反序列化随返回体量线性增长；警惕 BIGKEY。
     */
    public static void addCacheAllHash(String key, Map<String, ?> map, long timeout) {
        DELEGATE.get().hash().addHash(key, map);
        DELEGATE.get().key().setExpiredTime(key, timeout);
        // 同步更新本地缓存
        if (enableL2Caching() && enableKeyTypeCache(HASH)) {
            LocalCache.put(GLOBAL_CACHE, key, map);
            LocalCache.expiry(GLOBAL_CACHE, key, timeout, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * 添加元素到指定列表的方法（本方法将会添加重复值）
     *
     * @param key   列表名称
     * @param value 列表值
     * @apiNote <p><b>时间复杂度</b>：与元素数或批量规模相关，通常为 O(n) 或 O(k)；ToC 高并发慎用全量。
     * <p><b>ToC 严禁</b>：高并发下大批量 key、全量集合、无界扫描。
     * <p><b>可使用</b>：批量有上界、离线/管理端、明确 SLA 的工具任务。
     * <p><b>注意</b>：网络与反序列化随返回体量线性增长；警惕 BIGKEY。
     */
    public static void addListItem(String key, Object value) {
        DELEGATE.get().list().addListItem(key, value);
        if (enableL2Caching() && enableKeyTypeCache(LIST)) {
            // 同步更新本地缓存
            List<Object> list = LocalCache.get(GLOBAL_CACHE, key);
            if (list == null) {
                list = new ArrayList<>();
            }
            list.add(value);
            LocalCache.put(GLOBAL_CACHE, key, list);
        }
    }

    /**
     * 批量添加元素到指定列表的方法（本方法不会添加重复值）
     *
     * @param key     列表名称
     * @param set     列表值
     * @param timeout 过期时间（毫秒）
     * @apiNote <p><b>时间复杂度</b>：与元素数或批量规模相关，通常为 O(n) 或 O(k)；ToC 高并发慎用全量。
     * <p><b>ToC 严禁</b>：高并发下大批量 key、全量集合、无界扫描。
     * <p><b>可使用</b>：批量有上界、离线/管理端、明确 SLA 的工具任务。
     * <p><b>注意</b>：网络与反序列化随返回体量线性增长；警惕 BIGKEY。
     */
    public static void addSetCache(String key, Set<?> set, long timeout) {
        DELEGATE.get().set().addSet(key, set);
        DELEGATE.get().key().setExpiredTime(key, timeout);
        if (enableL2Caching() && enableKeyTypeCache(SET)) {
            // 同步更新本地缓存
            LocalCache.put(GLOBAL_CACHE, key, set);
            LocalCache.expiry(GLOBAL_CACHE, key, timeout, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * 添加元素到指定列表的方法（本方法不会添加重复值）
     *
     * @param key   列表名称
     * @param value 列表值
     * @apiNote <p><b>时间复杂度</b>：与元素数或批量规模相关，通常为 O(n) 或 O(k)；ToC 高并发慎用全量。
     * <p><b>ToC 严禁</b>：高并发下大批量 key、全量集合、无界扫描。
     * <p><b>可使用</b>：批量有上界、离线/管理端、明确 SLA 的工具任务。
     * <p><b>注意</b>：网络与反序列化随返回体量线性增长；警惕 BIGKEY。
     */
    public static void addSetItem(String key, Object value) {
        DELEGATE.get().set().addSetItem(key, value);
        if (enableL2Caching() && enableKeyTypeCache(SET)) {
            // 同步更新本地缓存
            Set<Object> set = LocalCache.get(GLOBAL_CACHE, key);
            if (set == null) {
                set = new HashSet<>();
            }
            set.add(value);
            LocalCache.put(GLOBAL_CACHE, key, set);
        }
    }

    /**
     * 获取缓存对象的方法
     *
     * @param key   缓存键
     * @param <T>   接收返回值使用的泛型类型
     * @param clazz 缓存对象类型
     * @return 返回缓存值（如果指定键不存在或已超时则返回null）
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     */
    public static <T> T getObjectFromHash(String key, Class<T> clazz) {
        registerType(key, clazz);
        return getWithLocalCache(key, HASH, () -> DELEGATE.get().hash().getObjectFromHash(key, clazz));
    }


    /**
     * 获取字符串类型缓存的方法
     *
     * @param key 缓存键
     * @return 返回缓存的字符串
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     */
    public static String getStringCache(String key) {
        registerType(key, String.class);
        return getWithLocalCache(key, STRING, () -> DELEGATE.get().string().getFromString(key, String.class));
    }

    /**
     * 获取整型数值类型缓存的方法
     *
     * @param key 缓存键
     * @return 返回缓存的字符串
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     */
    public static Integer getIntCache(String key) {
        registerType(key, Integer.class);
        return getWithLocalCache(key, STRING, () -> DELEGATE.get().string().getFromString(key, Integer.class));
    }

    /**
     * 获取长整型数值类型缓存的方法
     *
     * @param key 缓存键
     * @return 返回缓存的字符串
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     */
    public static Long getLongCache(String key) {
        registerType(key, Long.class);
        return getWithLocalCache(key, STRING, () -> DELEGATE.get().string().getFromString(key, Long.class));
    }

    /**
     * 获取浮点型数值类型缓存的方法
     *
     * @param key 缓存键
     * @return 返回缓存的字符串
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     */
    public static Float getFloatCache(String key) {
        registerType(key, Float.class);
        return getWithLocalCache(key, STRING, () -> DELEGATE.get().string().getFromString(key, Float.class));
    }

    /**
     * 获取双精度浮点型数值类型缓存的方法
     *
     * @param key 缓存键
     * @return 返回缓存的字符串
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     */
    public static Double getDoubleCache(String key) {
        registerType(key, Double.class);
        return getWithLocalCache(key, STRING, () -> DELEGATE.get().string().getFromString(key, Double.class));
    }

    /**
     * 获取布尔型缓存的方法
     *
     * @param key 缓存键
     * @return 返回缓存的字符串
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     */
    public static Boolean getBooleanCache(String key) {
        registerType(key, Boolean.class);
        return getWithLocalCache(key, STRING, () -> DELEGATE.get().string().getFromString(key, Boolean.class));
    }

    /**
     * 获取不重复的对象列表的方法
     *
     * @param key   缓存键
     * @param clazz 缓存对象类型
     * @param <T>   接收返回值使用的泛型类型
     * @return 返回不重复的对象列表
     * @apiNote <p><b>时间复杂度</b>：与元素数或批量规模相关，通常为 O(n) 或 O(k)；ToC 高并发慎用全量。
     * <p><b>ToC 严禁</b>：高并发下大批量 key、全量集合、无界扫描。
     * <p><b>可使用</b>：批量有上界、离线/管理端、明确 SLA 的工具任务。
     * <p><b>注意</b>：网络与反序列化随返回体量线性增长；警惕 BIGKEY。
     */
    public static <T> Set<T> getSetCache(String key, Class<T> clazz) {
        registerType(key, clazz);
        return getWithLocalCache(key, SET, () -> DELEGATE.get().set().getFromSet(key, clazz));
    }

    /**
     * 获取ZSet集合全部元素的的方法
     *
     * @param key       元素KEY
     * @param start     元素起始位置
     * @param end       元素结束位置
     * @param reference 内省对象
     * @param <T>       接收返回值使用的泛型类型
     * @return 返回全部的元素
     * @apiNote <p><b>时间复杂度</b>：O(log n) 量级（有序集合相关，含排名/区间，n 为元素规模）。
     * <p><b>ToC 严禁</b>：超大 ZSet 上全量范围、无界 rank 扫描。
     * <p><b>可使用</b>：排行榜、限范围查询；成员量与区间宽度需可控。
     * <p><b>注意</b>：结合 score 分布与分页；避免冷热混合导致延迟抖动。
     */
    public static <T> Map<Double, T> getZSetCache(String key, long start, long end, TypeReference<T> reference) {
        return DELEGATE.get().zSet().getZSetData(key, start, end, reference);
    }

    /**
     * 获取ZSet集合元素的排名的方法
     *
     * @param key   元素KEY
     * @param value 元素值
     * @return 返回元素的排名
     * @apiNote <p><b>时间复杂度</b>：O(log n) 量级（有序集合相关，含排名/区间，n 为元素规模）。
     * <p><b>ToC 严禁</b>：超大 ZSet 上全量范围、无界 rank 扫描。
     * <p><b>可使用</b>：排行榜、限范围查询；成员量与区间宽度需可控。
     * <p><b>注意</b>：结合 score 分布与分页；避免冷热混合导致延迟抖动。
     */
    public static Long getZSetRank(String key, Object value) {
        return DELEGATE.get().zSet().getZSetRank(key, value);
    }

    /**
     * 获取对象列表的方法
     * <p style='color:red'>（注：此方法有锁，可防止幻读和脏读f，但请注意和其它带锁方法的组合使用，控制不好会死锁。）
     *
     * @param key   缓存键
     * @param clazz 缓存对象类型
     * @param <T>   接收返回值使用的泛型类型
     * @return 返回对象列表
     * @apiNote <p><b>时间复杂度</b>：与元素数或批量规模相关，通常为 O(n) 或 O(k)；ToC 高并发慎用全量。
     * <p><b>ToC 严禁</b>：高并发下大批量 key、全量集合、无界扫描。
     * <p><b>可使用</b>：批量有上界、离线/管理端、明确 SLA 的工具任务。
     * <p><b>注意</b>：网络与反序列化随返回体量线性增长；警惕 BIGKEY。
     */
    public static <T> List<T> getListCache(String key, Class<T> clazz) {
        registerType(key, clazz);
        return getWithLocalCache(key, LIST, () -> DELEGATE.get().list().getFromList(key, -1, clazz));
    }

    /**
     * 获取对象列表首个对象的方法
     * <p style='color:red'>（注：此方法有锁，可防止幻读和脏读，但请注意和其它带锁方法的组合使用，控制不好会死锁。）
     *
     * @param key   缓存键
     * @param clazz 缓存对象类型
     * @param <T>   接收返回值使用的泛型类型
     * @return 返回对象列表
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     */
    public static <T> T getFirstFromList(String key, Class<T> clazz) {
        registerType(key, clazz);
        List<T> fromList = DELEGATE.get().list().getFromList(key, 0, clazz);
        return fromList.isEmpty() ? null : fromList.getFirst();
    }

    /**
     * 获取对象列表最后一个对象的方法
     * <p style='color:red'>（注：此方法有锁，可防止幻读和脏读，但请注意和其它带锁方法的组合使用，控制不好会死锁。）
     *
     * @param key   缓存键
     * @param clazz 缓存对象类型
     * @param <T>   接收返回值使用的泛型类型
     * @return 返回对象列表
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     */
    public static <T> T getLastFromList(String key, Class<T> clazz) {
        registerType(key, clazz);
        Long listSize = DELEGATE.get().list().getListSize(key);
        List<T> fromList = DELEGATE.get().list().getFromList(key, listSize - 1, clazz);
        return fromList.isEmpty() ? null : fromList.getFirst();
    }

    /**
     * 获取对象列表指定位置对象的方法
     * <p style='color:red'>（注：此方法有锁，可防止幻读和脏读，但请注意和其它带锁方法的组合使用，控制不好会死锁。）
     *
     * @param key   缓存键
     * @param index 指定位置（如果索引超出列表长度则返回null，从0开始。）
     * @param clazz 缓存对象类型
     * @param <T>   接收返回值使用的泛型类型
     * @return 返回对象列表
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     */
    public static <T> T getLastFromListIndex(String key, long index, Class<T> clazz) {
        registerType(key, clazz);
        long listSize = DELEGATE.get().list().getListSize(key);
        if (index < 0 || index >= listSize) {
            return null;
        }
        List<T> fromList = DELEGATE.get().list().getFromList(key, index, clazz);
        return fromList.isEmpty() ? null : fromList.getFirst();
    }

    /**
     * 获取缓存的Map对象的方法
     *
     * @param key   缓存键
     * @param clazz 缓存值类型
     * @param <T>   接收返回值使用的泛型类型
     * @return 返回缓存值（如果指定键不存在或已超时则返回null）
     * @apiNote <p><b>时间复杂度</b>：全量 Hash 映射时为 O(n)，n 为 field 数；可能触发 BIGKEY。
     * <p><b>ToC 严禁</b>：高并发下大批量 key、全量集合、无界扫描。
     * <p><b>可使用</b>：批量有上界、离线/管理端、明确 SLA 的工具任务。
     * <p><b>注意</b>：网络与反序列化随返回体量线性增长；警惕 BIGKEY。
     */
    public static <T> Map<String, T> getHashCache(String key, Class<T> clazz) {
        registerType(key, clazz);
        return getWithLocalCache(key, HASH, () -> DELEGATE.get().hash().getAllMapFromHash(key, clazz));
    }

    /**
     * 获取缓存的Hash值对象的方法
     *
     * @param key     缓存键
     * @param hashKey hash键
     * @param clazz   缓存值的类型
     * @param <T>     接收返回值使用的泛型类型
     * @return 返回缓存的值（可能为null或空）
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。 单 field 读取。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     */
    public static <T> T getHashCache(String key, String hashKey, Class<T> clazz) {
        if (enableL2Caching() && enableKeyTypeCache(HASH)) {
            registerType(key, clazz);
            Map<String, T> map = LocalCache.get(GLOBAL_CACHE, key);
            if (map != null) {
                T value = map.get(hashKey);
                if (value != null) {
                    return value;
                }
            }
        }
        return DELEGATE.get().hash().getFromHash(key, hashKey, clazz);
    }

    /**
     * 获取缓存的Hash值对象的方法
     *
     * @param key       缓存键
     * @param hashKey   hash键
     * @param reference 内省对象
     * @param <T>       接收返回值使用的泛型类型
     * @return 返回缓存的值（可能为null或空）
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。 单 field 读取。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     */
    public static <T> T getHashCache(String key, String hashKey, TypeReference<T> reference) {
        var valueType = getValueType(key);
        if (valueType != null && enableL2Caching() && enableKeyTypeCache(HASH)) {
            Map<String, T> map = LocalCache.get(GLOBAL_CACHE, key);
            if (map != null) {
                T value = map.get(hashKey);
                if (value != null) {
                    return value;
                }
            }
        }
        var result = DELEGATE.get().hash().getFromHash(key, hashKey, reference);
        if (result != null && enableL2Caching() && enableKeyTypeCache(HASH)) {
            registerType(key, result.getClass());
        }
        return result;
    }

    /**
     * 获取缓存的Hash值对象的方法
     *
     * @param key       缓存键
     * @param hashKeys  hash键列表（每次获取的hashKeys数量不要超过20个）
     * @param reference 缓存值的内省对象
     * @param <T>       接收返回值使用的泛型类型
     * @return 返回缓存的值（可能为null或空）
     * @apiNote <p><b>时间复杂度</b>：O(k)，k 为 field 个数（通常有上界）。
     * <p><b>ToC 严禁</b>：高并发下大批量 key、全量集合、无界扫描。
     * <p><b>可使用</b>：批量有上界、离线/管理端、明确 SLA 的工具任务。
     * <p><b>注意</b>：网络与反序列化随返回体量线性增长；警惕 BIGKEY。
     */
    public static <T> List<T> getHashCache(String key, Collection<String> hashKeys, TypeReference<T> reference) {
        var valueType = getValueType(key);
        if (valueType != null && enableL2Caching() && enableKeyTypeCache(HASH)) {
            Map<String, T> map = LocalCache.get(GLOBAL_CACHE, key);
            if (map != null) {
                List<T> resultList = new ArrayList<>();
                hashKeys.forEach(hashKey -> {
                    T value = map.get(hashKey);
                    if (value != null) {
                        resultList.add(value);
                    }
                });
                return resultList;
            }
        }
        var result = DELEGATE.get().hash().getFromHash(key, hashKeys, reference);
        if (!result.isEmpty()) {
            registerType(key, result.getFirst().getClass());
        }
        return result;
    }

    /**
     * 根据Key列表删除指定元素的方法
     * <p>（元素包含全部的Redis元素，比如：string, list, set, zset, hash, stream）
     *
     * @param keys KEY列表
     * @apiNote <p><b>时间复杂度</b>：O(k)，k 为待删除 key 数量。
     * <p><b>ToC 严禁</b>：高并发下大批量 key、全量集合、无界扫描。
     * <p><b>可使用</b>：批量有上界、离线/管理端、明确 SLA 的工具任务。
     * <p><b>注意</b>：网络与反序列化随返回体量线性增长；警惕 BIGKEY。
     */
    public static void removeCache(Collection<String> keys) {
        DELEGATE.get().key().removeCache(keys);
        // 同步删除本地缓存
        keys.forEach(key -> LocalCache.remove(GLOBAL_CACHE, key));
    }

    /**
     * 移除指定缓存的方法
     *
     * @param key 需要移除的缓存键
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。 单 key 删除。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     */
    public static void removeCache(String key) {
        DELEGATE.get().key().removeCache(key);
        // 同步删除本地缓存
        if (enableL2Caching()) {
            LocalCache.remove(GLOBAL_CACHE, key);
        }
    }

    /**
     * 移除指定Hash缓存的方法
     *
     * @param key     需要移除的缓存键
     * @param hashKey 需要移除的Hash键
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     */
    public static void removeHashItem(String key, String... hashKey) {
        DELEGATE.get().hash().removeHashItem(key, hashKey);
        // 同步删除本地缓存
        if (enableL2Caching()) {
            LocalCache.remove(GLOBAL_CACHE, key);
        }
    }

    /**
     * 移除指定List缓存的方法
     *
     * @param key   需要移除的缓存键
     * @param value 需要移除的值
     * @param count 相同值的移除的个数
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     */
    public static void removeListItem(String key, Object value, int count) {
        DELEGATE.get().list().removeListItem(key, value, count);
        // 同步删除本地缓存
        if (enableL2Caching()) {
            LocalCache.remove(GLOBAL_CACHE, key);
        }
    }

    /**
     * 批量删除Set集合元素的方法
     *
     * @param key    列表名称
     * @param values 要移除的值
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     */
    public static void removeSetItem(String key, Object... values) {
        DELEGATE.get().set().removeSetItem(key, values);
        // 同步删除本地缓存
        if (enableL2Caching()) {
            LocalCache.remove(GLOBAL_CACHE, key);
        }
    }

    /**
     * 缓存内计数器+1的方法
     * <p style="color:red">注意：在 redis 的 pipeline 和 transaction 当中使用本方法，结果永远返回0
     *
     * @param key 缓存键
     * @return 返回计数值
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     * @see <a href="https://redis.io/commands/incr">查看 Redis 文档：INCR</a>
     */
    public static long increment(String key) {
        return DELEGATE.get().string().increment(key, null);
    }

    /**
     * 缓存内计数器+1的方法
     * <p style="color:red">注意：在 redis 的 pipeline 和 transaction 当中使用本方法，结果永远返回0
     *
     * @param key     缓存键
     * @param timeout 超时时间（单位：毫秒）
     * @return 返回计数值
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     * @see <a href="https://redis.io/commands/incr">查看 Redis 文档：INCR</a>
     */
    public static long increment(String key, long timeout) {
        return DELEGATE.get().string().increment(key, timeout);
    }

    /**
     * 缓存内计数器+1的方法
     * <p style="color:red">注意：在 redis 的 pipeline 和 transaction 当中使用本方法，结果永远返回0
     *
     * @param key     缓存键
     * @param delta   增量
     * @param timeout 超时时间（单位：毫秒）
     * @return 返回计数值（当在时，该值返回null）
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     * @see <a href="https://redis.io/commands/incrby">查看 Redis 文档：INCRBY</a>
     */
    public static long increment(String key, long delta, long timeout) {
        return DELEGATE.get().string().increment(key, delta, timeout);
    }

    /**
     * 缓存内计数器+1的方法
     * <p style="color:red">注意：在pipeline和transaction当中使用本方法，结果永远返回0.0
     *
     * @param key     缓存键
     * @param delta   增量
     * @param timeout 超时时间（单位：毫秒）
     * @return 返回计数值
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     * @see <a href="https://redis.io/commands/incrbyfloat">查看 Redis 文档：INCRBYFLOAT</a>
     */
    public static double increment(String key, double delta, long timeout) {
        return DELEGATE.get().string().increment(key, delta, timeout);
    }

    /**
     * 缓存内计数器-1的方法
     * <p style="color:red">注意：在 redis 的 pipeline 和 transaction 当中使用本方法，结果永远返回0
     *
     * @param key 缓存键
     * @return 返回计数值
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     * @see <a href="https://redis.io/commands/decr">查看 Redis 文档：DECR</a>
     */
    public static long decrement(String key) {
        return DELEGATE.get().string().decrement(key, null);
    }

    /**
     * 缓存内计数器-1的方法
     * <p style="color:red">注意：在 redis 的 pipeline 和 transaction 当中使用本方法，结果永远返回0
     *
     * @param key     缓存键
     * @param timeout 超时时间（单位：毫秒）
     * @return 返回计数值
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     * @see <a href="https://redis.io/commands/decr">查看 Redis 文档：DECR</a>
     */
    public static long decrement(String key, long timeout) {
        return DELEGATE.get().string().decrement(key, timeout);
    }

    /**
     * 缓存内计数器-1的方法
     * <p style="color:red">注意：在 redis 的 pipeline 和 transaction 当中使用本方法，结果永远返回0
     *
     * @param key     缓存键
     * @param delta   增量
     * @param timeout 超时时间（单位：毫秒）
     * @return 返回计数值
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     * @see <a href="https://redis.io/commands/decrby">查看 Redis 文档：DECRBY</a>
     */
    public static long decrement(String key, long delta, long timeout) {
        return DELEGATE.get().string().decrement(key, delta, timeout);
    }

    /**
     * 添加并替换已有的列表缓存的方法
     *
     * @param key    缓存键
     * @param values 缓存值
     * @apiNote <p><b>时间复杂度</b>：与元素数或批量规模相关，通常为 O(n) 或 O(k)；ToC 高并发慎用全量。
     * <p><b>ToC 严禁</b>：高并发下大批量 key、全量集合、无界扫描。
     * <p><b>可使用</b>：批量有上界、离线/管理端、明确 SLA 的工具任务。
     * <p><b>注意</b>：网络与反序列化随返回体量线性增长；警惕 BIGKEY。
     */
    public static void replaceListCache(String key, List<?> values) {
        registerType(key, values.getFirst().getClass());
        DELEGATE.get().list().addAndReplaceList(key, values);
    }

    /**
     * 替换指定索引位的对象的方法
     *
     * @param key   列表名称
     * @param index 待更新的索引位
     * @param value 列表值
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     */
    public static void replaceListItem(String key, long index, Object value) {
        registerType(key, value.getClass());
        DELEGATE.get().list().updateListItem(key, index, value);
    }


    /**
     * 尝试获取分布式锁的方法（乐观锁，获取不到则直接返回失败）
     *
     * @param key 资源键
     * @return 返回获取结果（默认占锁 3 秒）
     * @apiNote <p><b>时间复杂度</b>：单次加锁尝试为 O(1) 量级；<b>阻塞/等待</b>使墙钟时间不可按 O(1) 理解。
     * <p><b>ToC 严禁</b>：长临界区、锁粒度覆盖热 key、批量锁顺序不一致导致死锁。
     * <p><b>可使用</b>：短临界区、防击穿/互斥更新；配合 try-with-resources 释放。
     * <p><b>注意</b>：续期锁对 Redis 与线程调度有额外开销。
     */
    public static CacheLock optimisticLock(String key) {
        return optimisticLock(key, 3L); // 默认3秒
    }

    /**
     * 尝试获取分布式锁的方法（乐观锁，获取不到则直接返回失败，可指定过期时间，单位：秒）
     *
     * @param key     资源键
     * @param seconds 锁过期时间（秒）
     * @return 返回获取结果
     * @apiNote <p><b>时间复杂度</b>：单次加锁尝试为 O(1) 量级；<b>阻塞/等待</b>使墙钟时间不可按 O(1) 理解。
     * <p><b>ToC 严禁</b>：长临界区、锁粒度覆盖热 key、批量锁顺序不一致导致死锁。
     * <p><b>可使用</b>：短临界区、防击穿/互斥更新；配合 try-with-resources 释放。
     * <p><b>注意</b>：续期锁对 Redis 与线程调度有额外开销。
     */
    public static CacheLock optimisticLock(String key, long seconds) {
        return DELEGATE.get().lock().optimisticLock(key, seconds);
    }

    /**
     * 尝试获取分布式锁的方法（乐观锁，带锁续期能力）
     *
     * @param key     资源键
     * @param seconds 锁过期时间（秒）支持自动续期的锁过期时间不能小于3秒
     * @return 返回获取结果
     * @apiNote <p><b>时间复杂度</b>：单次加锁尝试为 O(1) 量级；<b>阻塞/等待</b>使墙钟时间不可按 O(1) 理解。
     * <p><b>ToC 严禁</b>：长临界区、锁粒度覆盖热 key、批量锁顺序不一致导致死锁。
     * <p><b>可使用</b>：短临界区、防击穿/互斥更新；配合 try-with-resources 释放。
     * <p><b>注意</b>：续期锁对 Redis 与线程调度有额外开销。
     */
    public static CacheLock optimisticLockWithRenewal(String key, long seconds) {
        return DELEGATE.get().lock().lockWithRenewal(key, seconds, true);
    }

    /**
     * 尝试获取分布式锁的方法（悲观锁，获取不到则一直等待直到获取或线程被中断）
     *
     * @param key 资源键
     * @return 返回获取结果（默认占锁 3 秒）
     * @apiNote <p><b>时间复杂度</b>：单次加锁尝试为 O(1) 量级；<b>阻塞/等待</b>使墙钟时间不可按 O(1) 理解。
     * <p><b>ToC 严禁</b>：长临界区、锁粒度覆盖热 key、批量锁顺序不一致导致死锁。
     * <p><b>可使用</b>：短临界区、防击穿/互斥更新；配合 try-with-resources 释放。
     * <p><b>注意</b>：续期锁对 Redis 与线程调度有额外开销。
     */
    public static CacheLock pessimisticLock(String key) {
        return pessimisticLock(key, 3L);
    }

    /**
     * 尝试获取分布式锁的方法（悲观锁，可指定占锁时间，单位：秒；获取不到则一直等待直到获取或线程被中断）
     *
     * @param key     资源键
     * @param seconds 占锁时间（秒），-1 为不设过期（若底层支持）
     * @return 返回获取结果
     * @apiNote <p><b>时间复杂度</b>：单次加锁尝试为 O(1) 量级；<b>阻塞/等待</b>使墙钟时间不可按 O(1) 理解。
     * <p><b>ToC 严禁</b>：长临界区、锁粒度覆盖热 key、批量锁顺序不一致导致死锁。
     * <p><b>可使用</b>：短临界区、防击穿/互斥更新；配合 try-with-resources 释放。
     * <p><b>注意</b>：续期锁对 Redis 与线程调度有额外开销。
     */
    public static CacheLock pessimisticLock(String key, long seconds) {
        return DELEGATE.get().lock().pessimisticLock(key, seconds);
    }

    /**
     * 尝试获取分布式锁的方法（悲观锁，带锁续期能力；获取阶段一直等待直到获取或线程被中断）
     *
     * @param key     资源键
     * @param seconds 锁过期时间（秒），支持自动续期的锁过期时间不能小于3秒
     * @return 返回获取结果
     * @apiNote <p><b>时间复杂度</b>：单次加锁尝试为 O(1) 量级；<b>阻塞/等待</b>使墙钟时间不可按 O(1) 理解。
     * <p><b>ToC 严禁</b>：长临界区、锁粒度覆盖热 key、批量锁顺序不一致导致死锁。
     * <p><b>可使用</b>：短临界区、防击穿/互斥更新；配合 try-with-resources 释放。
     * <p><b>注意</b>：续期锁对 Redis 与线程调度有额外开销。
     */
    public static CacheLock pessimisticLockWithRenewal(String key, long seconds) {
        return DELEGATE.get().lock().lockWithRenewal(key, seconds, false);
    }

    /**
     * 尝试批量获取分布式锁的方法（对每个 key 乐观获取，任失败则释放已获锁并返回空）
     *
     * @param keys    需要上锁的资源键列表
     * @param timeout 批量锁超时时长
     * @param unit    超时时长单位
     * @return 返回获取结果
     * @apiNote <p><b>时间复杂度</b>：单次加锁尝试为 O(1) 量级；<b>阻塞/等待</b>使墙钟时间不可按 O(1) 理解。
     * <p><b>ToC 严禁</b>：长临界区、锁粒度覆盖热 key、批量锁顺序不一致导致死锁。
     * <p><b>可使用</b>：短临界区、防击穿/互斥更新；配合 try-with-resources 释放。
     * <p><b>注意</b>：续期锁对 Redis 与线程调度有额外开销。
     */
    public static CacheBatchLock batchLock(Collection<String> keys, long timeout, TimeUnit unit) {
        long seconds = unit.toSeconds(timeout);
        Set<CacheLock> locks = new HashSet<>(keys.size());
        for (String key : keys) {
            CacheLock lock = DELEGATE.get().lock().optimisticLock(key, seconds);
            if (lock.isSuccess()) {
                locks.add(lock);
            } else {
                locks.forEach(CacheLock::close);
                return new CacheBatchLock(Set.of());
            }
        }
        return new CacheBatchLock(locks);
    }

    /**
     * 获取匹配的 KEY 对应值的方法
     * <p style="color: red">（注：此方法可能会破坏分布式锁对值的锁定，慎用！）
     *
     * @param keys      匹配的 KEY 集合
     * @param reference 内省对象
     * @param <T>       期望转换的目标对象类型（如果目标类型与实际存储的Json对象不匹配则会抛出转换异常）
     * @return 返回 KEY 对应的值，如果某个值不存在则返回null
     * @apiNote <p><b>时间复杂度</b>：与元素数或批量规模相关，通常为 O(n) 或 O(k)；ToC 高并发慎用全量。
     * <p><b>ToC 严禁</b>：高并发下大批量 key、全量集合、无界扫描。
     * <p><b>可使用</b>：批量有上界、离线/管理端、明确 SLA 的工具任务。
     * <p><b>注意</b>：网络与反序列化随返回体量线性增长；警惕 BIGKEY。
     */
    public static <T> Map<String, T> getValueMap(List<String> keys, TypeReference<T> reference) {
        return DELEGATE.get().string().getValueMap(keys, reference);
    }

    /**
     * 批量更新缓存对象的方法
     *
     * @param batchUpdate 批量更新的数据
     * @param timeout     超时时间
     * @apiNote <p><b>时间复杂度</b>：与元素数或批量规模相关，通常为 O(n) 或 O(k)；ToC 高并发慎用全量。
     * <p><b>ToC 严禁</b>：高并发下大批量 key、全量集合、无界扫描。
     * <p><b>可使用</b>：批量有上界、离线/管理端、明确 SLA 的工具任务。
     * <p><b>注意</b>：网络与反序列化随返回体量线性增长；警惕 BIGKEY。
     */
    public static void batchUpdateIfAbsent(Map<String, ?> batchUpdate, Long timeout) {
        DELEGATE.get().string().batchUpdateIfAbsent(batchUpdate, timeout);
    }

    /**
     * 批量更新缓存对象的方法
     *
     * @param batchUpdate 批量更新的数据
     * @apiNote <p><b>时间复杂度</b>：与元素数或批量规模相关，通常为 O(n) 或 O(k)；ToC 高并发慎用全量。
     * <p><b>ToC 严禁</b>：高并发下大批量 key、全量集合、无界扫描。
     * <p><b>可使用</b>：批量有上界、离线/管理端、明确 SLA 的工具任务。
     * <p><b>注意</b>：网络与反序列化随返回体量线性增长；警惕 BIGKEY。
     */
    public static void batchUpdateIfAbsent(Map<String, ?> batchUpdate) {
        DELEGATE.get().string().batchUpdateIfAbsent(batchUpdate, null);
    }

    /**
     * 检查指定的Key是否存在的方法
     *
     * @param key 待检查的Key
     * @return 返回检查结果（true：存在，false：不存在）
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     */
    public static boolean hasKey(String key) {
        return DELEGATE.get().key().hasKey(key);
    }

    /**
     * 设置对应缓存过期时间的方法
     *
     * @param key     缓存键
     * @param timeout 超时时间
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     */
    public static void setExpiredTime(String key, long timeout) {
        DELEGATE.get().key().setExpiredTime(key, timeout);
    }

    /**
     * 将指定缓存队列中的第一个元素从队列中弹出的方法
     *
     * @param key   缓存键
     * @param clazz 缓存值
     * @param <T>   目标对象类型
     * @return 返回添加结果
     * @apiNote <p><b>时间复杂度</b>：与元素数或批量规模相关，通常为 O(n) 或 O(k)；ToC 高并发慎用全量。
     * <p><b>ToC 严禁</b>：高并发下大批量 key、全量集合、无界扫描。
     * <p><b>可使用</b>：批量有上界、离线/管理端、明确 SLA 的工具任务。
     * <p><b>注意</b>：网络与反序列化随返回体量线性增长；警惕 BIGKEY。
     */
    public static <T> T leftPopListElement(String key, Class<T> clazz) {
        return DELEGATE.get().list().leftPopListElement(key, clazz);
    }

    /**
     * 从列表队首弹出元素的方法
     *
     * @param key   元素KEY
     * @param count 弹出元素的数量
     * @param clazz 目标缓存类型
     * @param <T>   接收返回值使用的泛型类型
     * @return 返回执行结果
     * @apiNote <p><b>时间复杂度</b>：与元素数或批量规模相关，通常为 O(n) 或 O(k)；ToC 高并发慎用全量。
     * <p><b>ToC 严禁</b>：高并发下大批量 key、全量集合、无界扫描。
     * <p><b>可使用</b>：批量有上界、离线/管理端、明确 SLA 的工具任务。
     * <p><b>注意</b>：网络与反序列化随返回体量线性增长；警惕 BIGKEY。
     */
    public static <T> List<T> leftPopListElement(String key, long count, Class<T> clazz) {
        return DELEGATE.get().list().leftPopListElement(key, count, clazz);
    }

    /**
     * 将指定缓存队列中的最后一个元素从队列中弹出的方法
     *
     * @param key   缓存键
     * @param clazz 缓存值类型
     * @param <T>   目标对象类型
     * @return 返回添加结果
     * @apiNote <p><b>时间复杂度</b>：与元素数或批量规模相关，通常为 O(n) 或 O(k)；ToC 高并发慎用全量。
     * <p><b>ToC 严禁</b>：高并发下大批量 key、全量集合、无界扫描。
     * <p><b>可使用</b>：批量有上界、离线/管理端、明确 SLA 的工具任务。
     * <p><b>注意</b>：网络与反序列化随返回体量线性增长；警惕 BIGKEY。
     */
    public static <T> T rightPopListElement(String key, Class<T> clazz) {
        return DELEGATE.get().list().rightPopListElement(key, clazz);
    }

    /**
     * 从列表队首弹出元素的方法
     *
     * @param key   元素KEY
     * @param count 弹出元素的数量
     * @param clazz 目标缓存类型
     * @param <T>   接收返回值使用的泛型类型
     * @return 返回执行结果
     * @apiNote <p><b>时间复杂度</b>：与元素数或批量规模相关，通常为 O(n) 或 O(k)；ToC 高并发慎用全量。
     * <p><b>ToC 严禁</b>：高并发下大批量 key、全量集合、无界扫描。
     * <p><b>可使用</b>：批量有上界、离线/管理端、明确 SLA 的工具任务。
     * <p><b>注意</b>：网络与反序列化随返回体量线性增长；警惕 BIGKEY。
     */
    public static <T> List<T> rightPopListElement(String key, long count, Class<T> clazz) {
        return DELEGATE.get().list().rightPopListElement(key, count, clazz);
    }

    /**
     * 将指定元素添加到队列头部的方法
     *
     * @param key   缓存键
     * @param value 缓存值
     * @return 返回添加结果
     * @apiNote <p><b>时间复杂度</b>：与元素数或批量规模相关，通常为 O(n) 或 O(k)；ToC 高并发慎用全量。
     * <p><b>ToC 严禁</b>：高并发下大批量 key、全量集合、无界扫描。
     * <p><b>可使用</b>：批量有上界、离线/管理端、明确 SLA 的工具任务。
     * <p><b>注意</b>：网络与反序列化随返回体量线性增长；警惕 BIGKEY。
     */
    public static Long leftPushListElement(String key, Object value) {
        return DELEGATE.get().list().leftPushListElement(key, value);
    }

    /**
     * 将指定元素添加到队列尾部的方法
     *
     * @param key   缓存键
     * @param value 缓存值
     * @return 返回添加结果
     * @apiNote <p><b>时间复杂度</b>：与元素数或批量规模相关，通常为 O(n) 或 O(k)；ToC 高并发慎用全量。
     * <p><b>ToC 严禁</b>：高并发下大批量 key、全量集合、无界扫描。
     * <p><b>可使用</b>：批量有上界、离线/管理端、明确 SLA 的工具任务。
     * <p><b>注意</b>：网络与反序列化随返回体量线性增长；警惕 BIGKEY。
     */
    public static Long rightPushListElement(String key, Object value) {
        return DELEGATE.get().list().rightPushListElement(key, value);
    }

    /**
     * 获取列表长度
     *
     * @param key 列表的KEY
     * @return 返回列表长度
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     */
    public static Long getListSize(String key) {
        return DELEGATE.get().list().getListSize(key);
    }

    /**
     * 获取不重复列表长度
     *
     * @param key 列表的KEY
     * @return 返回不重复列表长度
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     */
    public static Long getSetSize(String key) {
        return DELEGATE.get().set().getSetSize(key);
    }

    /**
     * 获取有序不重复列表长度
     *
     * @param key 列表的KEY
     * @return 返回有序不重复列表长度
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     */
    public static Long getZSetSize(String key) {
        return DELEGATE.get().zSet().getZSetSize(key);
    }

    /**
     * 获取Hash列表长度
     *
     * @param key 列表的KEY
     * @return 返回Hash列表长度
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     */
    public static Long getHashSize(String key) {
        return DELEGATE.get().hash().getHashSize(key);
    }

    /**
     * 获取Redis连接信息的方法
     *
     * @return 返回Redis连接信息
     * @apiNote <p><b>时间复杂度</b>：O(1)（读配置/本地判定，无 Redis 或极轻量）。
     * <p><b>ToC 严禁</b>：高频轮询本方法替代业务缓存（应缓存配置结果）。
     * <p><b>可使用</b>：任意链路少量调用。
     * <p><b>注意</b>：结果依赖运行时配置与 Spring 注入完成时机。
     */
    public static String getConnectionString() {
        return DELEGATE.get().key().getConnectionString();
    }

    /**
     * 检查指定的Key是否存在的方法
     *
     * @param key   资源键
     * @param value 资源值
     * @return 返回检查结果（true：存在，false：不存在）
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     */
    public static boolean existsInSet(String key, Object value) {
        return DELEGATE.get().set().existsInSet(key, value);
    }

    /**
     * 检查指定的Key是否存在的方法
     *
     * @param key     资源键
     * @param hashKey HASH资源键
     * @return 返回检查结果（true：存在，false：不存在）
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     */
    public static boolean existsInHash(String key, String hashKey) {
        return DELEGATE.get().hash().existsInHash(key, hashKey);
    }


    /**
     * 根据HASH资源键获取资源HASH_KEY集合的方法
     *
     * @param key 资源键
     * @return 返回资源值集合
     * @apiNote <p><b>时间复杂度</b>：与元素数或批量规模相关，通常为 O(n) 或 O(k)；ToC 高并发慎用全量。
     * <p><b>ToC 严禁</b>：高并发下大批量 key、全量集合、无界扫描。
     * <p><b>可使用</b>：批量有上界、离线/管理端、明确 SLA 的工具任务。
     * <p><b>注意</b>：网络与反序列化随返回体量线性增长；警惕 BIGKEY。
     */
    public static Set<String> getHashKeyList(String key) {
        return DELEGATE.get().hash().getHashKeyList(key);
    }

    /**
     * 批量添加元素到指定列表的方法（本方法不会添加重复值）
     *
     * @param key      列表名称
     * @param orderSet 列表值
     * @apiNote <p><b>时间复杂度</b>：O(log n) 量级（有序集合相关，含排名/区间，n 为元素规模）。
     * <p><b>ToC 严禁</b>：超大 ZSet 上全量范围、无界 rank 扫描。
     * <p><b>可使用</b>：排行榜、限范围查询；成员量与区间宽度需可控。
     * <p><b>注意</b>：结合 score 分布与分页；避免冷热混合导致延迟抖动。
     */
    public static void addZSet(String key, TreeSet<?> orderSet) {
        DELEGATE.get().zSet().addZSet(key, orderSet);
    }

    /**
     * 批量添加元素到指定列表的方法（本方法不会添加重复值）
     *
     * @param key   列表名称
     * @param value 列表值
     * @param score 列表排序号
     * @apiNote <p><b>时间复杂度</b>：O(log n) 量级（有序集合相关，含排名/区间，n 为元素规模）。
     * <p><b>ToC 严禁</b>：超大 ZSet 上全量范围、无界 rank 扫描。
     * <p><b>可使用</b>：排行榜、限范围查询；成员量与区间宽度需可控。
     * <p><b>注意</b>：结合 score 分布与分页；避免冷热混合导致延迟抖动。
     */
    public static void addZSetItem(String key, Object value, double score) {
        DELEGATE.get().zSet().addZSetItem(key, value, score);
    }

    /**
     * 批量删除Set集合元素的方法
     *
     * @param key    列表名称
     * @param values 要移除的值
     * @apiNote <p><b>时间复杂度</b>：O(log n) 量级（有序集合相关，含排名/区间，n 为元素规模）。
     * <p><b>ToC 严禁</b>：超大 ZSet 上全量范围、无界 rank 扫描。
     * <p><b>可使用</b>：排行榜、限范围查询；成员量与区间宽度需可控。
     * <p><b>注意</b>：结合 score 分布与分页；避免冷热混合导致延迟抖动。
     */
    public static void removeZSetItem(String key, Object... values) {
        DELEGATE.get().zSet().removeZSetItem(key, values);
    }

    /**
     * 删除指定范围的元素
     *
     * @param key   列表名称
     * @param start 要移除的元素起始位置
     * @param end   要移除的元素结束位置
     * @apiNote <p><b>时间复杂度</b>：O(log n) 量级（有序集合相关，含排名/区间，n 为元素规模）。
     * <p><b>ToC 严禁</b>：超大 ZSet 上全量范围、无界 rank 扫描。
     * <p><b>可使用</b>：排行榜、限范围查询；成员量与区间宽度需可控。
     * <p><b>注意</b>：结合 score 分布与分页；避免冷热混合导致延迟抖动。
     */
    public static void removeZSetItem(String key, long start, long end) {
        DELEGATE.get().zSet().removeZSetItem(key, start, end);
    }

    /**
     * 根据元素排序分数删除指定范围元素的方法
     *
     * @param key 缓存KEY
     * @param min 最小分数
     * @param max 最大分数
     * @apiNote <p><b>时间复杂度</b>：O(log n) 量级（有序集合相关，含排名/区间，n 为元素规模）。
     * <p><b>ToC 严禁</b>：超大 ZSet 上全量范围、无界 rank 扫描。
     * <p><b>可使用</b>：排行榜、限范围查询；成员量与区间宽度需可控。
     * <p><b>注意</b>：结合 score 分布与分页；避免冷热混合导致延迟抖动。
     */
    public static void removeZSetItem(String key, double min, double max) {
        DELEGATE.get().zSet().removeZSetItem(key, min, max);
    }

    /**
     * 根据元素排序分数删除指定元素的方法
     *
     * @param key   缓存KEY
     * @param score 需要删除的元素
     * @apiNote <p><b>时间复杂度</b>：O(log n) 量级（有序集合相关，含排名/区间，n 为元素规模）。
     * <p><b>ToC 严禁</b>：超大 ZSet 上全量范围、无界 rank 扫描。
     * <p><b>可使用</b>：排行榜、限范围查询；成员量与区间宽度需可控。
     * <p><b>注意</b>：结合 score 分布与分页；避免冷热混合导致延迟抖动。
     */
    public static void removeZSetItem(String key, double score) {
        DELEGATE.get().zSet().removeZSetItem(key, score, score);
    }

    /**
     * 批量添加缓存到Redis Hash的方法
     * <p style="color: red">（注：此方法非原子性操作，有可能出现并发安全问题。）
     *
     * @param map 批量添加的缓存数据
     * @apiNote <p><b>时间复杂度</b>：与元素数或批量规模相关，通常为 O(n) 或 O(k)；ToC 高并发慎用全量。
     * <p><b>ToC 严禁</b>：高并发下大批量 key、全量集合、无界扫描。
     * <p><b>可使用</b>：批量有上界、离线/管理端、明确 SLA 的工具任务。
     * <p><b>注意</b>：网络与反序列化随返回体量线性增长；警惕 BIGKEY。
     */
    public static void batchAddToHash(Map<String, Map<String, ?>> map) {
        DELEGATE.get().hash().batchAddToHash(map);
    }

    /**
     * 批量添加缓存到Redis Set的方法
     * <p style="color: red">（注：此方法非原子性操作，有可能出现并发安全问题。）
     *
     * @param map 批量添加的缓存数据
     * @apiNote <p><b>时间复杂度</b>：O(log n) 量级（有序集合相关，含排名/区间，n 为元素规模）。
     * <p><b>ToC 严禁</b>：超大 ZSet 上全量范围、无界 rank 扫描。
     * <p><b>可使用</b>：排行榜、限范围查询；成员量与区间宽度需可控。
     * <p><b>注意</b>：结合 score 分布与分页；避免冷热混合导致延迟抖动。
     */
    public static void batchAddToZSet(Map<String, TreeSet<?>> map) {
        DELEGATE.get().zSet().batchAddToZSet(map);
    }

    /**
     * 批量添加缓存到Redis Set的方法
     * <p style="color: red">（注：此方法非原子性操作，有可能出现并发安全问题。）
     *
     * @param map 批量添加的缓存数据
     * @apiNote <p><b>时间复杂度</b>：与元素数或批量规模相关，通常为 O(n) 或 O(k)；ToC 高并发慎用全量。
     * <p><b>ToC 严禁</b>：高并发下大批量 key、全量集合、无界扫描。
     * <p><b>可使用</b>：批量有上界、离线/管理端、明确 SLA 的工具任务。
     * <p><b>注意</b>：网络与反序列化随返回体量线性增长；警惕 BIGKEY。
     */
    public static void batchAddToSet(Map<String, Set<?>> map) {
        DELEGATE.get().set().batchAddToSet(map);
    }

    /**
     * 批量添加缓存到Redis List的方法
     * <p style="color: red">（注：此方法非原子性操作，有可能出现并发安全问题。）
     *
     * @param map 批量添加的缓存数据
     * @apiNote <p><b>时间复杂度</b>：与元素数或批量规模相关，通常为 O(n) 或 O(k)；ToC 高并发慎用全量。
     * <p><b>ToC 严禁</b>：高并发下大批量 key、全量集合、无界扫描。
     * <p><b>可使用</b>：批量有上界、离线/管理端、明确 SLA 的工具任务。
     * <p><b>注意</b>：网络与反序列化随返回体量线性增长；警惕 BIGKEY。
     */
    public static void batchAddToList(Map<String, List<?>> map) {
        DELEGATE.get().list().batchAddToList(map);
    }

    /**
     * 批量添加缓存到Redis String中的方法
     * <p style="color: red">（注：此方法非原子性操作，有可能出现并发安全问题。）
     *
     * @param map 批量添加的缓存数据
     * @apiNote <p><b>时间复杂度</b>：与元素数或批量规模相关，通常为 O(n) 或 O(k)；ToC 高并发慎用全量。
     * <p><b>ToC 严禁</b>：高并发下大批量 key、全量集合、无界扫描。
     * <p><b>可使用</b>：批量有上界、离线/管理端、明确 SLA 的工具任务。
     * <p><b>注意</b>：网络与反序列化随返回体量线性增长；警惕 BIGKEY。
     */
    public static void batchAddToString(Map<String, ?> map) {
        DELEGATE.get().string().batchAddToString(map);
    }

    /**
     * 批量添加缓存到Redis String中的方法
     * <p style="color: red">（注：此方法非原子性操作，有可能出现并发安全问题。）
     *
     * @param map     批量添加的缓存数据
     * @param timeout 超时时间（单位：毫秒）
     * @apiNote <p><b>时间复杂度</b>：与元素数或批量规模相关，通常为 O(n) 或 O(k)；ToC 高并发慎用全量。
     * <p><b>ToC 严禁</b>：高并发下大批量 key、全量集合、无界扫描。
     * <p><b>可使用</b>：批量有上界、离线/管理端、明确 SLA 的工具任务。
     * <p><b>注意</b>：网络与反序列化随返回体量线性增长；警惕 BIGKEY。
     */
    public static void batchAddToString(Map<String, ?> map, long timeout) {
        DELEGATE.get().string().batchAddToString(map, timeout);
    }

    /**
     * 弹出ZSet队首元素的方法
     *
     * @param key       列表名称
     * @param reference 内省对象
     * @param <T>       接收返回值使用的泛型类型
     * @return 队首元素，无元素时为 null
     * @apiNote <p><b>时间复杂度</b>：O(log n) 量级（有序集合相关，含排名/区间，n 为元素规模）。
     * <p><b>ToC 严禁</b>：超大 ZSet 上全量范围、无界 rank 扫描。
     * <p><b>可使用</b>：排行榜、限范围查询；成员量与区间宽度需可控。
     * <p><b>注意</b>：结合 score 分布与分页；避免冷热混合导致延迟抖动。
     */
    public static <T> T popMinFromZSet(String key, TypeReference<T> reference) {
        return DELEGATE.get().zSet().popMinFromZSet(key, reference);
    }

    /**
     * 弹出ZSet队首元素的方法
     *
     * @param key       列表名称
     * @param count     弹出元素的数量
     * @param reference 内省对象
     * @param <T>       接收返回值使用的泛型类型
     * @return 弹出的元素集合
     * @apiNote <p><b>时间复杂度</b>：O(log n) 量级（有序集合相关，含排名/区间，n 为元素规模）。
     * <p><b>ToC 严禁</b>：超大 ZSet 上全量范围、无界 rank 扫描。
     * <p><b>可使用</b>：排行榜、限范围查询；成员量与区间宽度需可控。
     * <p><b>注意</b>：结合 score 分布与分页；避免冷热混合导致延迟抖动。
     */
    public static <T> Set<T> popMinFromZSet(String key, long count, TypeReference<T> reference) {
        return DELEGATE.get().zSet().popMinFromZSet(key, count, reference);
    }

    /**
     * 合并两个数据集的方法
     *
     * @param sourceKey 源数据集
     * @param targetKey 目标数据集
     * @param replace   是否替换目标数据集
     * @return 返回合并结果
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     */
    public static boolean copy(String sourceKey, String targetKey, boolean replace) {
        return DELEGATE.get().key().copy(sourceKey, targetKey, replace);
    }

    /**
     * 移动指定的Key到指定的数据库的方法
     *
     * @param key     需要移动的KEY
     * @param dbIndex 目标数据库索引
     * @return 返回移动结果
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     */
    public static boolean move(String key, int dbIndex) {
        return DELEGATE.get().key().move(key, dbIndex);
    }

    /**
     * 仅当目标 KEY 不存在时才将指定 KEY 重命名为目标 KEY 的方法
     *
     * @param oldKey 旧 KEY
     * @param newKey 新 KEY
     * @return 返回是否重命名结果（true：重命名，false：未重命名）
     * @throws DataAccessException 当访问的 oldKey 不存在时抛出此异常
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     */
    public static boolean renameIfAbsent(String oldKey, String newKey) throws DataAccessException {
        return DELEGATE.get().key().renameIfAbsent(oldKey, newKey);
    }

    /**
     * 重命名 KEY 的方法
     *
     * @param oldKey 旧 KEY
     * @param newKey 新 KEY
     * @throws DataAccessException 当访问的 oldKey 不存在时抛出此异常
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     */
    public static void rename(String oldKey, String newKey) throws DataAccessException {
        DELEGATE.get().key().rename(oldKey, newKey);
    }

    /**
     * 序列化 KEY 的方法
     *
     * @param key 待序列化的 KEY
     * @return 返回序列化后的 KEY
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     */
    public static byte[] dump(String key) {
        return DELEGATE.get().key().dump(key);
    }

    /**
     * 移除指定 key 的过期时间（执行后 KEY 将不再过期）
     *
     * @param key 待移除过期时间的 KEY
     * @return 返回移除结果
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     */
    public static boolean persist(String key) {
        return DELEGATE.get().key().persist(key);
    }

    /**
     * 指定时间点设置过期时间的方法
     *
     * @param key  待设置过期时间的 KEY
     * @param date 过期的时间点
     * @return 返回设置结果
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     */
    @SuppressWarnings("UnusedReturnValue")
    public static boolean expireAt(String key, final Date date) {
        return DELEGATE.get().key().expireAt(key, date);
    }

    /**
     * 获取匹配的 KEY 数量的方法
     *
     * @param keys KEY 集合
     * @return 返回匹配的 KEY 的个数
     * @apiNote <p><b>时间复杂度</b>：与元素数或批量规模相关，通常为 O(n) 或 O(k)；ToC 高并发慎用全量。
     * <p><b>ToC 严禁</b>：高并发下大批量 key、全量集合、无界扫描。
     * <p><b>可使用</b>：批量有上界、离线/管理端、明确 SLA 的工具任务。
     * <p><b>注意</b>：网络与反序列化随返回体量线性增长；警惕 BIGKEY。
     */
    public static Long countExistingKeys(Collection<String> keys) {
        return DELEGATE.get().key().countExistingKeys(keys);
    }

    /**
     * 获取匹配的 KEY 对应值的方法
     * <p style="color: red">（注：此方法可能会破坏分布式锁对值的锁定，每次获取的hashKeys数量不要超过20个，慎用！）
     *
     * @param keys      匹配的 KEY 集合
     * @param reference 目标类型引用
     * @param <T>       返回值元素泛型类型
     * @return 返回 KEY 对应的值，如果某个值不存在则返回null
     * @apiNote <p><b>时间复杂度</b>：与元素数或批量规模相关，通常为 O(n) 或 O(k)；ToC 高并发慎用全量。
     * <p><b>ToC 严禁</b>：高并发下大批量 key、全量集合、无界扫描。
     * <p><b>可使用</b>：批量有上界、离线/管理端、明确 SLA 的工具任务。
     * <p><b>注意</b>：网络与反序列化随返回体量线性增长；警惕 BIGKEY。
     */
    public static <T> List<T> getObjects(@Nonnull Collection<String> keys, TypeReference<T> reference) {
        return DELEGATE.get().string().getObjects(keys, reference);
    }

    /**
     * 从Set中弹出一个元素的方法
     *
     * @param key   资源键
     * @param clazz 目标缓存类型
     * @param <T>   接收返回值使用的泛型类型
     * @return 返回资源值集合
     * @apiNote <p><b>时间复杂度</b>：与元素数或批量规模相关，通常为 O(n) 或 O(k)；ToC 高并发慎用全量。
     * <p><b>ToC 严禁</b>：高并发下大批量 key、全量集合、无界扫描。
     * <p><b>可使用</b>：批量有上界、离线/管理端、明确 SLA 的工具任务。
     * <p><b>注意</b>：网络与反序列化随返回体量线性增长；警惕 BIGKEY。
     */
    public static <T> T popDataFromSet(String key, Class<T> clazz) {
        return DELEGATE.get().set().popDataFromSet(key, clazz);
    }

    /**
     * 从Set中弹出指定数量元素的方法
     * <p style="color: red">（注：每次获取的hashKeys数量不要超过20个，否则可能造成性能问题，慎用！）
     *
     * @param key   资源键
     * @param count 弹出元素的数量
     * @param clazz 目标缓存类型
     * @param <T>   接收返回值使用的泛型类型
     * @return 返回资源值集合
     * @apiNote <p><b>时间复杂度</b>：与元素数或批量规模相关，通常为 O(n) 或 O(k)；ToC 高并发慎用全量。
     * <p><b>ToC 严禁</b>：高并发下大批量 key、全量集合、无界扫描。
     * <p><b>可使用</b>：批量有上界、离线/管理端、明确 SLA 的工具任务。
     * <p><b>注意</b>：网络与反序列化随返回体量线性增长；警惕 BIGKEY。
     */
    public static <T> Set<T> popMembersFromSet(String key, long count, Class<T> clazz) {
        return DELEGATE.get().set().popMembersFromSet(key, count, clazz);
    }

    /**
     * 增加ZSet集合元素的分数的方法
     *
     * @param key   元素KEY
     * @param value 元素值
     * @param delta 增量
     * @return 返回增加后的分数
     * @apiNote <p><b>时间复杂度</b>：O(log n) 量级（有序集合相关，含排名/区间，n 为元素规模）。
     * <p><b>ToC 严禁</b>：超大 ZSet 上全量范围、无界 rank 扫描。
     * <p><b>可使用</b>：排行榜、限范围查询；成员量与区间宽度需可控。
     * <p><b>注意</b>：结合 score 分布与分页；避免冷热混合导致延迟抖动。
     */
    public static Double incrementScore(String key, Object value, double delta) {
        return DELEGATE.get().zSet().incrementScore(key, value, delta);
    }

    /**
     * 获取ZSet集合元素在反转排序后排名的方法
     *
     * @param key   元素KEY
     * @param value 元素值
     * @return 返回元素的排名
     * @apiNote <p><b>时间复杂度</b>：O(log n) 量级（有序集合相关，含排名/区间，n 为元素规模）。
     * <p><b>ToC 严禁</b>：超大 ZSet 上全量范围、无界 rank 扫描。
     * <p><b>可使用</b>：排行榜、限范围查询；成员量与区间宽度需可控。
     * <p><b>注意</b>：结合 score 分布与分页；避免冷热混合导致延迟抖动。
     */
    public static Long getZSetReverseRank(String key, Object value) {
        return DELEGATE.get().zSet().getZSetReverseRank(key, value);
    }

    /**
     * 以Score值降序排列获取指定Rank范围元素的方法
     * <p style="color: red">（注：startRank和endRank之间的跨度不要过大，否则可能造成性能问题，慎用！）
     *
     * @param key       资源KEY
     * @param startRank 起始索引位置
     * @param endRank   结束索引位置
     * @param reference 目标元素类型
     * @param <T>       接收返回值使用的泛型类型
     * @return 返回指定范围内的元素列表
     * @apiNote <p><b>时间复杂度</b>：O(log n) 量级（有序集合相关，含排名/区间，n 为元素规模）。
     * <p><b>ToC 严禁</b>：超大 ZSet 上全量范围、无界 rank 扫描。
     * <p><b>可使用</b>：排行榜、限范围查询；成员量与区间宽度需可控。
     * <p><b>注意</b>：结合 score 分布与分页；避免冷热混合导致延迟抖动。
     */
    public static <T> Set<T> reverseScoreRangeFromZSet(String key, long startRank, long endRank, TypeReference<T> reference) {
        return DELEGATE.get().zSet().reverseRangeWithScores(key, startRank, endRank, reference);
    }

    /**
     * 以Score值降序排列获取指定范围元素的方法
     * <p style="color: red">（注：minScore和maxScore之间的跨度不要过大，否则可能造成性能问题，慎用！）
     *
     * @param key       资源KEY
     * @param minScore  最小排序值
     * @param maxScore  最大排序值
     * @param reference 目标类型引用
     * @param <T>       接收返回值使用的泛型类型
     * @return 返回指定范围内的元素列表
     * @apiNote <p><b>时间复杂度</b>：O(log n) 量级（有序集合相关，含排名/区间，n 为元素规模）。
     * <p><b>ToC 严禁</b>：超大 ZSet 上全量范围、无界 rank 扫描。
     * <p><b>可使用</b>：排行榜、限范围查询；成员量与区间宽度需可控。
     * <p><b>注意</b>：结合 score 分布与分页；避免冷热混合导致延迟抖动。
     */
    public static <T> Set<T> reverseScoreRangeFromZSet(String key, double minScore, double maxScore, TypeReference<T> reference) {
        return DELEGATE.get().zSet().reverseRangeByScore(key, minScore, maxScore, reference);
    }

    /**
     * 发布消息到指定频道的方法
     *
     * @param topic   发布消息的主题
     * @param message 消息内容
     * @return 返回接收到消息的订阅者数量（当Redis处于管道或事务环境中时，返回null）
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     */
    public static Long publishNotification(String topic, Object message) {
        return DELEGATE.get().notification().publishNotify(topic, message);
    }

    /**
     * 异步消费Stream消息的方法（事件订阅机制）
     * 使用全局消费者管理器，避免重复创建线程池
     *
     * @return Stream 操作接口
     * @apiNote <p><b>时间复杂度</b>：依具体 Stream API 而定（常为 O(1)~O(n)，见 StreamFunction 文档）。
     * <p><b>ToC 严禁</b>：大范围 XREAD 阻塞、大 consumer pending 堆积不处理。
     * <p><b>可使用</b>：消息队列、异步解耦；需监控 lag 与长度。
     * <p><b>注意</b>：返回底层门面，调用方须自行控制范围与背压。
     */
    public static StreamFunction stream() {
        return DELEGATE.get().stream();
    }

    /**
     * 获取指定Key的类型的方法
     *
     * @param key 缓存键
     * @return 返回Key的类型
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     */
    public static KeyTypeEnum getKeyType(String key) {
        return DELEGATE.get().key().getKeyType(key);
    }

    /**
     * 获取指定Key的值的类型的方法
     *
     * @param key 缓存键
     * @return 缓存值类型
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     */
    public static Class<?> getValueType(String key) {
        return DELEGATE.get().key().getValueType(key);
    }

    /**
     * 注册缓存键的类型的方法
     *
     * @param key   缓存键
     * @param clazz 缓存值类型
     */
    private static void registerType(String key, Class<?> clazz) {
        DELEGATE.get().key().registerType(key, clazz);
    }

    /**
     * 从本地缓存获取值，如果未命中则调用 Redis 加载器获取并回写本地缓存
     * 统一处理本地缓存的读取和写入逻辑，减少代码重复
     *
     * @param key         缓存键
     * @param keyType     缓存类型
     * @param redisLoader Redis 数据加载器
     * @param <T>         返回值类型
     * @return 缓存值，如果不存在则返回 null
     */
    private static <T> T getWithLocalCache(String key, KeyTypeEnum keyType, Supplier<T> redisLoader) {
        // 1. 先检查本地缓存
        if (enableL2Caching() && enableKeyTypeCache(keyType)) {
            T cached = LocalCache.get(GLOBAL_CACHE, key);
            if (cached != null) {
                return cached;
            }
        }
        // 2. 本地缓存未命中，从 Redis 加载
        T result = redisLoader.get();
        // 3. 如果从 Redis 获取到数据，回写本地缓存
        if (result != null && enableL2Caching() && enableKeyTypeCache(keyType)) {
            LocalCache.put(GLOBAL_CACHE, key, result);
        }
        return result;
    }

    /**
     * 从本地缓存获取值，如果未命中则调用带锁的 Redis 加载器获取并回写本地缓存
     * 统一处理 *WithLock 方法的本地缓存逻辑
     *
     * @param key         缓存键
     * @param keyType     缓存类型
     * @param redisLoader 带锁的 Redis 数据加载器（不处理本地缓存）
     * @param <T>         返回值类型
     * @return 缓存值，如果不存在则返回 null
     */
    private static <T> T getWithLocalCacheAndLock(String key, KeyTypeEnum keyType, Supplier<T> redisLoader) {
        // 1. 先检查本地缓存
        if (enableL2Caching() && enableKeyTypeCache(keyType)) {
            T cached = LocalCache.get(GLOBAL_CACHE, key);
            if (cached != null) {
                return cached;
            }
        }
        // 2. 本地缓存未命中，调用带锁的加载器（内部会处理布隆过滤器、Redis、加锁、回源DB等）
        T result = redisLoader.get();
        // 3. 如果获取到数据，回写本地缓存
        if (result != null && enableL2Caching() && enableKeyTypeCache(keyType)) {
            LocalCache.put(GLOBAL_CACHE, key, result);
        }
        return result;
    }

    /**
     * 初始化全局缓存工具类的方法（该接口由Spring调用）
     *
     * @param redisCacheManager Redis缓存管理器
     * @apiNote <p><b>时间复杂度</b>：O(1)（仅设置静态委托引用，无 Redis 访问）。
     * <p><b>ToC 严禁</b>：业务代码手动重复调用该方法。
     * <p><b>可使用</b>：由 Spring 容器在启动阶段注入一次。
     * <p><b>注意</b>：双检锁保证 DELEGATE 只初始化一次；若未注入则静态方法调用将 NPE。
     */
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

    /**
     * 获取String缓存对象的方法（防缓存击穿）
     * <p style="color: red">（注：此方法会在获取不到缓存时自动加锁，防止缓存击穿）
     *
     * @param key      缓存键
     * @param timeout  超时时间（单位：毫秒）
     * @param dbLoader 缓存值加载器
     * @return 返回结果
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     */
    public static String getStringCacheWithLock(String key, long timeout, Supplier<String> dbLoader) {
        return getWithLocalCacheAndLock(key, STRING,
                () -> DELEGATE.get().string().getFromStringWithLock(key, dbLoader, timeout));
    }

    /**
     * 获取Object对象缓存的方法（防缓存击穿）
     * <p style="color: red">（注：此方法会在获取不到缓存时自动加锁，防止缓存击穿）
     *
     * @param key      缓存键
     * @param clazz    缓存值的类型
     * @param timeout  超时时间（单位：毫秒）
     * @param dbLoader 缓存值加载器
     * @param <T>      返回值类型
     * @return 返回结果
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     */
    public static <T> T getObjectFromHashWithLock(String key, Class<T> clazz, long timeout, Supplier<T> dbLoader) {
        return getWithLocalCacheAndLock(key, HASH,
                () -> DELEGATE.get().hash().getObjectFromHashWithLock(key, clazz, dbLoader, timeout));
    }

    /**
     * 获取Object对象缓存的方法（防缓存击穿）
     * <p style="color: red">（注：此方法会在获取不到缓存时自动加锁，防止缓存击穿）
     *
     * @param key       缓存键
     * @param reference 缓存值的类型引用
     * @param timeout   超时时间（单位：毫秒）
     * @param dbLoader  缓存值加载器
     * @param <T>       返回值类型
     * @return 返回结果
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     */
    public static <T> T getObjectFromHashWithLock(String key, TypeReference<T> reference, long timeout, Supplier<T> dbLoader) {
        return getWithLocalCacheAndLock(key, HASH,
                () -> DELEGATE.get().hash().getObjectFromHashWithLock(key, reference, dbLoader, timeout));
    }

    /**
     * 获取Hash缓存对象的方法（防缓存击穿）
     * <p style="color: red">（注：此方法会在获取不到缓存时自动加锁，防止缓存击穿）
     *
     * @param key      缓存键
     * @param hashKey  Hash键
     * @param clazz    缓存值的类型
     * @param timeout  超时时间（单位：毫秒）
     * @param dbLoader 缓存值加载器
     * @param <T>      返回值类型
     * @return 返回结果
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     */
    public static <T> T getHashCacheWithLock(String key, String hashKey, Class<T> clazz, long timeout, Supplier<T> dbLoader) {
        // Hash 子项使用特殊的 key 格式，需要特殊处理
        String cacheKey = key + ":" + hashKey;
        return getWithLocalCacheAndLock(cacheKey, HASH,
                () -> DELEGATE.get().hash().getFromHashWithLock(key, hashKey, clazz, dbLoader, timeout));
    }

    /**
     * 获取Hash缓存对象的方法（防缓存击穿）
     * <p style="color: red">（注：此方法会在获取不到缓存时自动加锁，防止缓存击穿）
     *
     * @param key       缓存键
     * @param hashKey   Hash键
     * @param reference 缓存值的类型引用
     * @param timeout   超时时间（单位：毫秒）
     * @param dbLoader  缓存值加载器
     * @param <T>       返回值类型
     * @return 返回结果
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     */
    public static <T> T getHashCacheWithLock(String key, String hashKey, TypeReference<T> reference, long timeout, Supplier<T> dbLoader) {
        // Hash 子项使用特殊的 key 格式，需要特殊处理
        String cacheKey = key + ":" + hashKey;
        return getWithLocalCacheAndLock(cacheKey, HASH,
                () -> DELEGATE.get().hash().getFromHashWithLock(key, hashKey, reference, dbLoader, timeout));
    }

    /**
     * 获取List缓存对象的方法（防缓存击穿）
     * <p style="color: red">（注：此方法会在获取不到缓存时自动加锁，防止缓存击穿）
     *
     * @param key      缓存键
     * @param index    索引位置
     * @param clazz    缓存值的类型
     * @param timeout  超时时间（单位：毫秒）
     * @param dbLoader 缓存值加载器
     * @param <T>      返回值类型
     * @return 返回结果
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     */
    public static <T> List<T> getListCacheWithLock(String key, long index, Class<T> clazz, long timeout, Supplier<List<T>> dbLoader) {
        return getWithLocalCacheAndLock(key, LIST,
                () -> DELEGATE.get().list().getFromListWithLock(key, index, clazz, dbLoader, timeout));
    }

    /**
     * 获取Set缓存对象的方法（防缓存击穿）
     * <p style="color: red">（注：此方法会在获取不到缓存时自动加锁，防止缓存击穿）
     *
     * @param key      缓存键
     * @param clazz    缓存值的类型
     * @param timeout  超时时间（单位：毫秒）
     * @param dbLoader 缓存值加载器
     * @param <T>      返回值类型
     * @return 返回结果
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     */
    public static <T> Set<T> getSetCacheWithLock(String key, Class<T> clazz, long timeout, Supplier<Set<T>> dbLoader) {
        return getWithLocalCacheAndLock(key, SET,
                () -> DELEGATE.get().set().getFromSetWithLock(key, clazz, dbLoader, timeout));
    }

    /**
     * 向 HyperLogLog 数据结构中添加元素的方法
     *
     * @param key    HyperLogLog 的键
     * @param values 要添加的元素
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     */
    public static void pfAdd(String key, Object... values) {
        DELEGATE.get().hyperLog().pfAdd(key, values);
    }

    /**
     * 获取 HyperLogLog 数据结构中唯一元素的数量的方法
     *
     * @param key HyperLogLog的键
     * @return 基数估算值（long类型）
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     */
    public static long pfCount(String key) {
        return DELEGATE.get().hyperLog().pfCount(key);
    }

    /**
     * 设置位图的指定偏移量的值的方法
     *
     * @param key    缓存键
     * @param offset 位图偏移量
     * @param value  位图值（true表示1，false表示0）
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     */
    public static void setBit(String key, long offset, boolean value) {
        DELEGATE.get().bitmap().setBit(key, offset, value);
    }

    /**
     * 获取位图的指定偏移量的值的方法
     *
     * @param key    缓存键
     * @param offset 位图偏移量
     * @return 返回位图值（true表示1，false表示0）
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     */
    public static boolean getBit(String key, long offset) {
        return DELEGATE.get().bitmap().getBit(key, offset);
    }

    /**
     * 执行Lua脚本的方法
     *
     * @param script     Lua脚本内容
     * @param keys       需要传入的键列表
     * @param args       需要传入的参数列表
     * @param resultType 返回结果的类型
     * @param <T>        返回值类型
     * @return 返回执行结果
     * @apiNote <p><b>时间复杂度</b>：由脚本内 Redis 命令决定（可能为 O(n) 或更差），封装层标为未知/脚本级。
     * <p><b>ToC 严禁</b>：脚本内 KEYS、大循环、全表扫描。
     * <p><b>可使用</b>：原子组合少量 O(1) 命令；需评审脚本。
     * <p><b>注意</b>：长脚本阻塞单线程 Redis；上线前必须压测。
     */
    public static <T> T evalLua(String script, List<String> keys, List<String> args, Class<T> resultType) {
        return DELEGATE.get().lua().evalLua(script, keys, args, resultType);
    }

    /**
     * 执行Lua脚本的方法（单键版本）
     * <p>
     * 适用于只需要一个 Redis 键的 Lua 脚本场景，提供更简洁的调用方式。
     *
     * @param script     Lua脚本内容
     * @param key        需要传入的单个键
     * @param args       需要传入的参数列表
     * @param resultType 返回结果的类型
     * @param <T>        返回值类型
     * @return 返回执行结果
     * @apiNote <p><b>时间复杂度</b>：由脚本内 Redis 命令决定（可能为 O(n) 或更差），封装层标为未知/脚本级。
     * <p><b>ToC 严禁</b>：脚本内 KEYS、大循环、全表扫描。
     * <p><b>可使用</b>：原子组合少量 O(1) 命令；需评审脚本。
     * <p><b>注意</b>：长脚本阻塞单线程 Redis；上线前必须压测。
     */
    public static <T> T evalLua(String script, String key, List<String> args, Class<T> resultType) {
        return DELEGATE.get().lua().evalLua(script, List.of(key), args, resultType);
    }

    /**
     * 尝试获取滑动窗口限流令牌的方法
     * <p style="color: red">（注：此方法会在获取不到令牌时直接返回失败）
     *
     * @param key           限流键
     * @param maxCount      最大请求数
     * @param windowSeconds 窗口时间（单位：秒）
     * @return 返回获取结果（true：获取成功，false：获取失败）
     * @apiNote <p><b>时间复杂度</b>：O(1)（单次 Redis 操作）。
     * <p><b>ToC 严禁</b>：热 key 上无节制写；value 极大导致序列化/网络瓶颈；永不过期且无淘汰策略的 key 堆积。
     * <p><b>可使用</b>：核心链路基读/写；配合 TTL 与 key 命名规范。
     * <p><b>注意</b>：禁止在循环中将本方法放大为 O(n) 次往返；L2 与 Redis 一致性由框架与业务共同保证。
     */
    public static boolean tryAcquire(String key, int maxCount, int windowSeconds) {
        return DELEGATE.get().limiter().tryAcquire(key, maxCount, windowSeconds);
    }

    /**
     * 添加地理位置数据到Redis GEO集合。
     *
     * @param key       GEO集合的键
     * @param longitude 经度
     * @param latitude  纬度
     * @param member    成员名称
     * @apiNote <p><b>时间复杂度</b>：GEO 查询通常为 O(N+log(M)) 等（N 为范围内点数），非纯 O(1)。
     * <p><b>ToC 严禁</b>：超大半径、全集合 GEOSEARCH 无限制。
     * <p><b>可使用</b>：LBS 附近检索；限制半径与数量。
     * <p><b>注意</b>：地球曲率与编码误差；热点城市需分片。
     */
    public static void addGeo(String key, double longitude, double latitude, String member) {
        DELEGATE.get().geo().addGeo(key, longitude, latitude, member);
    }

    /**
     * 计算两个成员之间的地理距离。
     *
     * @param key     GEO集合的键
     * @param member1 成员1名称
     * @param member2 成员2名称
     * @return 两成员之间的距离对象，单位默认为米
     * @apiNote <p><b>时间复杂度</b>：GEO 查询通常为 O(N+log(M)) 等（N 为范围内点数），非纯 O(1)。
     * <p><b>ToC 严禁</b>：超大半径、全集合 GEOSEARCH 无限制。
     * <p><b>可使用</b>：LBS 附近检索；限制半径与数量。
     * <p><b>注意</b>：地球曲率与编码误差；热点城市需分片。
     */
    public static Distance geoDist(String key, String member1, String member2) {
        return DELEGATE.get().geo().geoDist(key, member1, member2);
    }

    /**
     * 查询指定经纬度为圆心、指定半径范围内的所有成员。
     *
     * @param key       GEO集合的键
     * @param longitude 圆心经度
     * @param latitude  圆心纬度
     * @param radius    查询半径（单位：公里）
     * @return 范围内的成员及其地理信息列表
     * @apiNote <p><b>时间复杂度</b>：GEO 查询通常为 O(N+log(M)) 等（N 为范围内点数），非纯 O(1)。
     * <p><b>ToC 严禁</b>：超大半径、全集合 GEOSEARCH 无限制。
     * <p><b>可使用</b>：LBS 附近检索；限制半径与数量。
     * <p><b>注意</b>：地球曲率与编码误差；热点城市需分片。
     */
    public static List<GeoPointResult> geoRadius(String key, double longitude, double latitude, double radius) {
        return DELEGATE.get().geo().geoRadius(key, longitude, latitude, radius);
    }

}
