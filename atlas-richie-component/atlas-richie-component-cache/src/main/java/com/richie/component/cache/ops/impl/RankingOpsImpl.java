package com.richie.component.cache.ops.impl;

import com.richie.component.cache.function.ZSetFunction;
import com.richie.component.cache.ops.RankingOps;
import com.richie.component.cache.operations.ZSetCapacityLimits;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@Slf4j
@Component
@RequiredArgsConstructor
public class RankingOpsImpl implements RankingOps {

    private final ZSetFunction fn;

    @Override
    public void set(String key, Object value, double score) {
        checkCapacity(key);
        fn.addZSetItem(key, value, score);
    }

    @Override
    public void setAll(String key, TreeSet<?> orderSet) {
        fn.addZSet(key, orderSet);
    }

    @Override
    public void batchSet(Map<String, TreeSet<?>> map) {
        fn.batchAddToZSet(map);
    }

    @Override
    public long size(String key) {
        return fn.getZSetSize(key);
    }

    @Override
    public void remove(String key, Object... values) {
        fn.removeZSetItem(key, values);
    }

    @Override
    public void removeByRank(String key, long start, long end) {
        fn.removeZSetItem(key, start, end);
    }

    @Override
    public void removeByScore(String key, double min, double max) {
        fn.removeZSetItem(key, min, max);
    }

    @Override
    public double incrementScore(String key, Object value, double delta) {
        checkCapacity(key);
        return fn.incrementScore(key, value, delta);
    }

    @Override
    public <T> T popMin(String key, TypeReference<T> reference) {
        return fn.popMinFromZSet(key, reference);
    }

    @Override
    public <T> Set<T> popMin(String key, long count, TypeReference<T> reference) {
        return fn.popMinFromZSet(key, count, reference);
    }

    @Override
    public <T> Set<T> range(String key, long start, long end, TypeReference<T> reference) {
        return fn.reverseRangeWithScores(key, start, end, reference);
    }

    @Override
    public <T> Set<T> rangeByScore(String key, double minScore, double maxScore, TypeReference<T> reference) {
        return fn.reverseRangeByScore(key, minScore, maxScore, reference);
    }

    @Override
    public long reverseRank(String key, Object value) {
        return fn.getZSetReverseRank(key, value);
    }

    // ─────────── 容量守卫 ───────────

    private void checkCapacity(String key) {
        long currentSize = fn.getZSetSize(key);
        if (ZSetCapacityLimits.exceedsHardLimit(currentSize)) {
            throw new IllegalStateException(
                    "ZSet '%s' has reached hard capacity limit (%d elements), refusing write. "
                            .formatted(key, ZSetCapacityLimits.ZSET_HARD_MAX_ELEMENTS)
                            + "Please use a more appropriate data structure or shard the data.");
        }
        if (ZSetCapacityLimits.exceedsRecommended(currentSize)) {
            log.warn("[CacheGuard] ZSet '{}' size={} exceeds recommended limit ({}), consider sharding or cleanup",
                    key, currentSize, ZSetCapacityLimits.ZSET_RECOMMENDED_MAX_ELEMENTS);
        }
    }
}
