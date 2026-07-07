package com.richie.component.cache.bloom;

import com.richie.component.cache.config.CacheProperties;
import com.richie.context.bloom.BloomFilter;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Redisson 实现的布隆过滤器，作为 {@link com.richie.context.bloom.BloomFilter} SPI 的分布式实现。
 * <p>
 * 通过 {@code @Primary} + {@code @ConditionalOnMissingBean(BloomFilter.class)} 在容器无其它 BloomFilter
 * 时自动覆盖 {@code atlas-richie-context} 提供的 Guava 默认实现，无需手动排除。
 * <p>
 * <strong>激活条件</strong>：{@code platform.cache.bloom-filter.enable=true} 且
 * {@code platform.cache.bloom-filter.type=REDISSON}。当 {@code type=GUAVA} 或未启用 bloom 时本 bean 不创建，
 * 自动回落到 {@code atlas-richie-context} 的 GuavaBloomFilter。
 *
 * <h2>存储</h2>
 * <p>底层走 Redisson 客户端，bloom 数据持久化到 Redis（key 由 {@link CacheProperties.BloomFilterConfig#getKey()} 控制）。
 * 多实例间共享同一份 bloom 状态，避免单点 Guava 内存 bloom 在分布式部署下失效。
 *
 * @author richie696
 * @since 2025-06
 */
@Primary
@Component
@RequiredArgsConstructor
@ConditionalOnExpression("'${platform.cache.bloom-filter.enable:false}'=='true' && '${platform.cache.bloom-filter.type:REDISSON}'=='REDISSON'")
public class RedissonBloomFilter implements BloomFilter {

    private final RedissonClient redissonClient;
    private final CacheProperties cacheProperties;

    private RBloomFilter<String> delegate;

    @PostConstruct
    public void init() {
        var config = cacheProperties.getBloomFilter();
        RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter(config.getKey());
        if (!bloomFilter.isExists()) {
            bloomFilter.tryInit(config.getExpectedInsertions(), config.getFalseProbability());
        }
        this.delegate = redissonClient.getBloomFilter(config.getKey());
    }

    @Override
    public boolean mightContain(String key) {
        return delegate.contains(key);
    }

    @Override
    public void put(String key) {
        delegate.add(key);
    }

    @Override
    public void putAll(Set<String> keys) {
        // Redisson RBloomFilter 不支持批量 add，逐个调用（业务少见此路径）
        for (String key : keys) {
            delegate.add(key);
        }
    }

    @Override
    public boolean isExists() {
        return delegate != null && delegate.isExists();
    }
}