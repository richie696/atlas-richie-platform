package com.richie.component.cache.operations;

import java.util.Objects;

/**
 * 有界 List 结构（队列 / 栈）容量治理常量。
 * <p>
 * 与 README「大 Key 阈值参考 — List 推荐业务上限 5,000 元素」对齐；
 * {@link #BOUNDED_MAX_LEN_CEILING} 为 maxLen 绝对封顶（严格低于 BIGKEY 红线）。
 *
 * @author richie696
 * @since 2026-06-04
 */
public final class BoundedListCapacityLimits {

    /**
     * README 定义的 List 推荐业务上限（元素个数）。
     */
    public static final long LIST_BIGKEY_RECOMMENDED_MAX_ELEMENTS = 5_000L;

    /**
     * 有界队列 / 栈的 maxLen 绝对上限（= 推荐业务上限 − 1）。
     */
    public static final long BOUNDED_MAX_LEN_CEILING = LIST_BIGKEY_RECOMMENDED_MAX_ELEMENTS - 1L;

    public static final long MIN_MAX_LEN = 1L;

    public static final int MAX_BATCH_COUNT = 20;

    public static final String META_KEY_SUFFIX = ":meta";

    private BoundedListCapacityLimits() {
    }

    public static String metaKey(String key) {
        Objects.requireNonNull(key, "key");
        if (key.endsWith(META_KEY_SUFFIX)) {
            throw new IllegalArgumentException(
                    "key must not already end with '%s': %s".formatted(META_KEY_SUFFIX, key));
        }
        return key + META_KEY_SUFFIX;
    }

    public static void validateMaxLen(long maxLen) {
        if (maxLen < MIN_MAX_LEN || maxLen > BOUNDED_MAX_LEN_CEILING) {
            throw new IllegalArgumentException(
                    "maxLen must be in [%d, %d] (List BIGKEY ceiling), got %d"
                            .formatted(MIN_MAX_LEN, BOUNDED_MAX_LEN_CEILING, maxLen));
        }
    }

    public static void validateBatchCount(int count) {
        if (count < 1 || count > MAX_BATCH_COUNT) {
            throw new IllegalArgumentException(
                    "count must be in [%d, %d], got %d"
                            .formatted(1, MAX_BATCH_COUNT, count));
        }
    }

    public static void assertMaxLenMatches(String key, long requested, long existing) {
        if (requested != existing) {
            throw new IllegalArgumentException(
                    "Bounded structure '%s' already exists with maxLen=%d, requested maxLen=%d"
                            .formatted(key, existing, requested));
        }
    }

    /**
     * 从 meta 字符串解析并校验 maxLen。
     */
    public static long parseMetaMaxLen(String logicalKey, Object raw) {
        Objects.requireNonNull(logicalKey, "logicalKey");
        if (raw == null) {
            throw new IllegalStateException("Bounded meta missing for key: " + logicalKey);
        }
        try {
            long maxLen = Long.parseLong(raw.toString().trim());
            validateMaxLen(maxLen);
            return maxLen;
        } catch (NumberFormatException ex) {
            throw new IllegalStateException(
                    "Invalid bounded meta for key: %s, raw=%s".formatted(logicalKey, raw), ex);
        }
    }

    /**
     * 计算翻倍后的容量（已校验的 current 前提下，结果不超过 {@link #BOUNDED_MAX_LEN_CEILING}）。
     */
    public static long computeDoubledCapacity(long current) {
        validateMaxLen(current);
        if (current >= BOUNDED_MAX_LEN_CEILING) {
            return BOUNDED_MAX_LEN_CEILING;
        }
        long doubled = Math.multiplyExact(current, 2L);
        return Math.min(doubled, BOUNDED_MAX_LEN_CEILING);
    }

    public static boolean canGrow(long currentMaxLen) {
        validateMaxLen(currentMaxLen);
        return currentMaxLen < BOUNDED_MAX_LEN_CEILING;
    }
}
