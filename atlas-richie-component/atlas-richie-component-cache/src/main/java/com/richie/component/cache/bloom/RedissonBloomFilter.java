package com.richie.component.cache.bloom;

import com.richie.component.cache.config.CacheProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Redisson实现的布隆过滤器
 *
 * @author richie696
 * @version 1.0
 * @since 2025-06-25 14:34:05
 */
@Primary
@Component
@RequiredArgsConstructor
@ConditionalOnExpression("${platform.cache.bloom-filter.enable:false} && '${platform.cache.bloom-filter.type:REDISSON}'=='REDISSON'")
public class RedissonBloomFilter implements BloomFilterFacade {

    /** Redisson 客户端 */
    private final RedissonClient redissonClient;

    /** 缓存配置（含布隆过滤器配置） */
    private final CacheProperties cacheProperties;

    /** 底层 Redisson 布隆过滤器实例 */
    private RBloomFilter<String> delegate;

    /**
     * 初始化布隆过滤器，根据配置创建或获取已有实例。
     */
    @PostConstruct
    public void init() {
        // 初始化布隆过滤器
        var config = cacheProperties.getBloomFilter();
        RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter(config.getKey());
        if (!bloomFilter.isExists()) {
            bloomFilter.tryInit(config.getExpectedInsertions(), config.getFalseProbability());
        }
        this.delegate = redissonClient.getBloomFilter(cacheProperties.getBloomFilter().getKey());
    }

    @Override
    public boolean contains(String key) {
        return delegate.contains(key);
    }

    @Override
    public void add(String key) {
        delegate.add(key);
    }

    @Override
    public void addAll(Set<String> keys) {

    }

    @Override
    public boolean isExists() {
        return delegate.isExists();
    }
}
