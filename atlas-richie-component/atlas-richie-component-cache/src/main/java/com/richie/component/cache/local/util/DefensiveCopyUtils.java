package com.richie.component.cache.local.util;

import jakarta.annotation.PreDestroy;
import org.apache.fury.Fury;
import org.apache.fury.config.Language;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 防御性拷贝工具类
 * 使用 Fury 实现高性能深拷贝，防止外部代码修改影响缓存数据
 * <p>
 * 使用对象池管理 Fury 实例，兼容虚拟线程（Virtual Threads）：
 * - 避免 ThreadLocal 在虚拟线程中的内存泄漏问题
 * - 复用 Fury 实例，提升性能
 * - 控制内存占用（固定大小的对象池）
 *
 * @author richie696
 * @version 1.1
 * @since 2025-11-21
 */
@Slf4j
public class DefensiveCopyUtils {

    /**
     * Fury 对象池大小（根据 CPU 核心数动态调整）
     * 虚拟线程场景下，使用对象池比 ThreadLocal 更合适
     */
    private static final int POOL_SIZE = Math.max(4, Runtime.getRuntime().availableProcessors() * 2);

    /**
     * Fury 对象池
     * 使用 BlockingQueue 实现线程安全的对象池
     */
    private static final BlockingQueue<Fury> FURY_POOL = new ArrayBlockingQueue<>(POOL_SIZE);

    /**
     * 对象池初始化标志
     */
    private static final AtomicInteger POOL_INITIALIZED = new AtomicInteger(0);

    /*
      初始化 Fury 对象池
     */
    static {
        initializePool();
    }

    /**
     * 初始化对象池，预创建 Fury 实例
     */
    private static void initializePool() {
        if (POOL_INITIALIZED.compareAndSet(0, 1)) {
            for (int i = 0; i < POOL_SIZE; i++) {
                FURY_POOL.add(createFury());
            }
            log.info("Fury 对象池初始化完成，池大小: {}", POOL_SIZE);
        }
    }

    /**
     * 创建新的 Fury 实例
     *
     * @return 新 Fury 实例
     */
    private static Fury createFury() {
        return Fury.builder()
                .withLanguage(Language.JAVA)
                // 启用引用跟踪，处理循环引用
                .withRefTracking(true)
                // 启用字符串压缩（性能优化）
                .withStringCompressed(true)
                // 启用类注册（性能优化，但需要注册类）
                .requireClassRegistration(false)
                .build();
    }

    /**
     * 从对象池获取 Fury 实例
     * 如果池为空，创建新实例（临时使用，不归还池）
     *
     * @return Fury 实例
     */
    private static Fury borrowFury() {
        Fury fury = FURY_POOL.poll();
        if (fury == null) {
            // 池为空时创建临时实例（高并发场景下的降级策略）
            log.debug("Fury 对象池为空，创建临时实例");
            return createFury();
        }
        return fury;
    }

    /**
     * 归还 Fury 实例到对象池
     * 如果池已满，丢弃实例（避免内存泄漏）
     *
     * @param fury 待归还的 Fury 实例
     */
    private static void returnFury(Fury fury) {
        if (fury != null && !FURY_POOL.offer(fury)) {
            // 池已满，丢弃实例（避免内存泄漏）
            log.debug("Fury 对象池已满，丢弃实例");
        }
    }

    /**
     * 不可变类型集合（无需拷贝）
     */
    private static final Set<Class<?>> IMMUTABLE_TYPES = Set.of(
        String.class, Integer.class, Long.class, Double.class, Float.class,
        Boolean.class, Byte.class, Short.class, Character.class,
        java.math.BigDecimal.class, java.math.BigInteger.class
    );

    /**
     * 智能深拷贝
     *
     * @param original 原始对象
     * @param <T> 对象类型
     * @return 拷贝后的对象，如果 original 为 null 则返回 null
     */
    @SuppressWarnings("unchecked")
    public static <T> T deepCopy(T original) {
        if (original == null) {
            return null;
        }

        // 1. 不可变对象直接返回（性能优化）
        if (isImmutable(original)) {
            return original;
        }

        // 2. 集合类型需要特殊处理（Fury 可以处理，但我们可以优化）
        return switch (original) {
            case Map<?, ?> map -> (T) deepCopyMap(map);
            case List<?> list -> (T) deepCopyList(list);
            case Set<?> set -> (T) deepCopySet(set);
            default -> deepCopyByFury(original);
        };

    }

    /**
     * 判断是否为不可变对象
     *
     * @param obj 待判断的对象
     * @return 如果是不可变对象返回true，否则返回false
     */
    private static boolean isImmutable(Object obj) {
        if (obj == null) {
            return true;
        }
        Class<?> clazz = obj.getClass();

        // 基本类型包装类
        if (IMMUTABLE_TYPES.contains(clazz)) {
            return true;
        }

        // 枚举类型
        return clazz.isEnum();
    }

    /**
     * 深拷贝 Map
     *
     * @param original 原始 Map 对象
     * @param <K> Key 类型
     * @param <V> Value 类型
     * @return 拷贝后的 Map 对象
     */
    @SuppressWarnings("unchecked")
    private static <K, V> Map<K, V> deepCopyMap(Map<?, ?> original) {
        Map<K, V> copy = new HashMap<>(original.size());
        for (Map.Entry<?, ?> entry : original.entrySet()) {
            K key = deepCopy((K) entry.getKey());
            V value = deepCopy((V) entry.getValue());
            copy.put(key, value);
        }
        return copy;
    }

    /**
     * 深拷贝 List
     *
     * @param original 原始 List 对象
     * @param <T> 元素类型
     * @return 拷贝后的 List 对象
     */
    @SuppressWarnings("unchecked")
    private static <T> List<T> deepCopyList(List<?> original) {
        List<T> copy = new ArrayList<>(original.size());
        for (Object item : original) {
            copy.add(deepCopy((T) item));
        }
        return copy;
    }

    /**
     * 深拷贝 Set
     *
     * @param original 原始 Set 对象
     * @param <T> 元素类型
     * @return 拷贝后的 Set 对象
     */
    @SuppressWarnings("unchecked")
    private static <T> Set<T> deepCopySet(Set<?> original) {
        Set<T> copy = new HashSet<>(original.size());
        for (Object item : original) {
            copy.add(deepCopy((T) item));
        }
        return copy;
    }

    /**
     * 使用 Fury 进行深拷贝
     * 从对象池获取 Fury 实例，使用后归还
     *
     * @param original 原始对象
     * @param <T> 对象类型
     * @return 拷贝后的对象
     */
    @SuppressWarnings("unchecked")
    private static <T> T deepCopyByFury(T original) {
        Fury fury = borrowFury();
        try {
            // Fury 序列化和反序列化
            byte[] bytes = fury.serialize(original);
            return (T) fury.deserialize(bytes);
        } catch (Exception e) {
            log.error("Fury 深拷贝失败: {}, 对象类型: {}", e.getMessage(), original.getClass().getName(), e);
            // 降级策略：返回原对象（需要业务层保证不修改）
            // 或者抛出异常，让调用方处理
            throw new RuntimeException("深拷贝失败: %s".formatted(e.getMessage()), e);
        } finally {
            // 确保归还实例到对象池
            returnFury(fury);
        }
    }

    /**
     * 清理对象池（可选，在应用关闭时调用）
     */
    @PreDestroy
    public static void cleanup() {
        FURY_POOL.clear();
        log.info("Fury 对象池已清理");
    }

    /**
     * 获取对象池状态信息（用于监控）
     *
     * @return 对象池状态信息
     */
    public static String getPoolStatus() {
        return "Fury 对象池状态: 池大小=%d, 可用实例=%d, 已使用=%d".formatted(
                POOL_SIZE, FURY_POOL.size(), POOL_SIZE - FURY_POOL.size());
    }
}

