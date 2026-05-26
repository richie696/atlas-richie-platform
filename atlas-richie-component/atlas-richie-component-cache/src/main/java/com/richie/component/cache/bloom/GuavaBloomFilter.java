package com.richie.component.cache.bloom;

import com.richie.component.cache.config.CacheProperties;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Guava实现的本地布隆过滤器（仅用于本地测试或单机应用）
 *
 * @author richie696
 * @version 1.0
 * @since 2025-06-25 14:33:01
 */
@SuppressWarnings("UnstableApiUsage")
@Primary
@Component
@ConditionalOnExpression("${platform.cache.bloom-filter.enable:false} && '${platform.cache.bloom-filter.type:REDISSON}'=='GUAVA'")
public class GuavaBloomFilter implements BloomFilterFacade {

    /** Guava 布隆过滤器委托实例 */
    private final BloomFilter<String> delegate;

    /**
     * 使用缓存配置创建 Guava 布隆过滤器实例。
     *
     * @param properties 缓存配置（含布隆过滤器预期插入量与误判率）
     */
    public GuavaBloomFilter(CacheProperties properties) {
        this.delegate = BloomFilter.create(Funnels.stringFunnel(StandardCharsets.UTF_8),
                properties.getBloomFilter().getExpectedInsertions(),
                properties.getBloomFilter().getFalseProbability());
    }

    @Override
    public boolean contains(String key) {
        return delegate.mightContain(key);
    }

    @Override
    public void add(String key) {
        delegate.put(key);
    }

    @Override
    public void addAll(Set<String> keys) {
        for (String key : keys) {
            delegate.put(key);
        }
    }

    @Override
    public boolean isExists() {
        return delegate != null;
    }
}
