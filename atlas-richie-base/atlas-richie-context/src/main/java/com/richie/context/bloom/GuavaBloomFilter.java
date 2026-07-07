package com.richie.context.bloom;

import com.google.common.hash.Funnels;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * 基于 Guava 的布隆过滤器默认实现。
 * <p>
 * <strong>适用场景</strong>：单体部署 / 单 JVM 内有效，不跨实例共享。多实例部署请实现
 * {@link BloomFilter} 接口（如基于 Redis bitmap）并注册为 Spring bean，本默认实现会自动让位。
 *
 * <h2>装配条件</h2>
 * <ul>
 *   <li>Spring 容器中<strong>无</strong>其他 {@link BloomFilter} bean（用户自定义实现优先）</li>
 * </ul>
 * <p>由 {@link BloomFilterAutoConfiguration} 注入 {@link BloomFilterProperties} 并装配本 bean。
 * <p>本类<strong>不</strong>标注 {@code @Component} —— 装配完全由 auto-config 的
 * {@code @Bean} 方法负责，避免双重装配机制（{@code @Component} + {@code @Bean}）在
 * {@code @ComponentScan} 范围较广的应用中产生 bean 注册冲突。
 */
public class GuavaBloomFilter implements BloomFilter {

    private final com.google.common.hash.BloomFilter<CharSequence> delegate;

    public GuavaBloomFilter(BloomFilterProperties properties) {
        this.delegate = com.google.common.hash.BloomFilter.create(
                Funnels.stringFunnel(StandardCharsets.UTF_8),
                properties.getExpectedInsertions(),
                properties.getFalseProbability());
    }

    @Override
    public boolean mightContain(String key) {
        return delegate.mightContain(key);
    }

    @Override
    public void put(String key) {
        delegate.put(key);
    }

    @Override
    public void putAll(Set<String> keys) {
        for (String key : keys) {
            delegate.put(key);
        }
    }

    @Override
    public boolean isExists() {
        return delegate != null;
    }
}