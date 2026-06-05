package com.richie.component.cache.operations;

/**
 * Set 结构容量治理常量。
 * <p>
 * 与 README「大 Key 阈值参考 — Set 推荐业务上限 5,000 元素」对齐；
 * 超过 {@link #SET_RECOMMENDED_MAX_ELEMENTS} 时 {@code CollectionOps.add} 将输出 WARN 日志，
 * 达到 {@link #SET_HARD_MAX_ELEMENTS} 时直接拒绝写入。
 *
 * @author richie696
 * @since 2026-06-04
 */
public final class SetCapacityLimits {

    /**
     * README 定义的 Set 推荐业务上限（元素个数）。
     */
    public static final long SET_RECOMMENDED_MAX_ELEMENTS = 5_000L;

    /**
     * Set 硬性上限（= 推荐上限 × 2），超过则直接拒绝写入。
     * <p>预留弹性空间，避免在推荐上限附近因并发添加而频繁拒绝。
     */
    public static final long SET_HARD_MAX_ELEMENTS = SET_RECOMMENDED_MAX_ELEMENTS * 2L;

    private SetCapacityLimits() {
    }

    /**
     * 当前元素数是否已超过推荐上限（WARN 级别）。
     */
    public static boolean exceedsRecommended(long currentSize) {
        return currentSize >= SET_RECOMMENDED_MAX_ELEMENTS;
    }

    /**
     * 当前元素数是否已超过硬性上限（拒绝写入）。
     */
    public static boolean exceedsHardLimit(long currentSize) {
        return currentSize >= SET_HARD_MAX_ELEMENTS;
    }
}
