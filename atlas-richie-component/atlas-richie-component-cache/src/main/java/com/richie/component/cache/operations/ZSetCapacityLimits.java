/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.cache.operations;

/**
 * ZSet（有序集合）结构容量治理常量。
 * <p>
 * 与 README「大 Key 阈值参考 — ZSet 推荐业务上限 5,000 元素」对齐；
 * 超过 {@link #ZSET_RECOMMENDED_MAX_ELEMENTS} 时输出 WARN 日志，
 * 达到 {@link #ZSET_HARD_MAX_ELEMENTS} 时直接拒绝写入。
 *
 * @author richie696
 * @since 2026-06-04
 */
public final class ZSetCapacityLimits {

    /**
     * README 定义的 ZSet 推荐业务上限（元素个数）。
     */
    public static final long ZSET_RECOMMENDED_MAX_ELEMENTS = 5_000L;

    /**
     * ZSet 硬性上限（= 推荐上限 × 2），超过则直接拒绝写入。
     * <p>预留弹性空间，避免在推荐上限附近因并发添加而频繁拒绝。
     */
    public static final long ZSET_HARD_MAX_ELEMENTS = ZSET_RECOMMENDED_MAX_ELEMENTS * 2L;

    private ZSetCapacityLimits() {
    }

    /**
     * 当前元素数是否已超过推荐上限（WARN 级别）。
     */
    public static boolean exceedsRecommended(long currentSize) {
        return currentSize >= ZSET_RECOMMENDED_MAX_ELEMENTS;
    }

    /**
     * 当前元素数是否已超过硬性上限（拒绝写入）。
     */
    public static boolean exceedsHardLimit(long currentSize) {
        return currentSize >= ZSET_HARD_MAX_ELEMENTS;
    }
}
