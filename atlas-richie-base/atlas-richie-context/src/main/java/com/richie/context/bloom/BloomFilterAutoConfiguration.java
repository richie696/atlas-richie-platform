package com.richie.context.bloom;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 布隆过滤器自动装配。
 * <p>
 * 装配 {@link BloomFilterProperties} + {@link GuavaBloomFilter}。用户实现 {@link BloomFilter} 接口
 * 并注册为 Spring bean 后，本默认实现自动让位（{@link org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean}）。
 *
 * @author richie696
 * @since 2026-07
 */
@AutoConfiguration
@EnableConfigurationProperties(BloomFilterProperties.class)
public class BloomFilterAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(BloomFilter.class)
    public GuavaBloomFilter guavaBloomFilter(BloomFilterProperties properties) {
        return new GuavaBloomFilter(properties);
    }
}