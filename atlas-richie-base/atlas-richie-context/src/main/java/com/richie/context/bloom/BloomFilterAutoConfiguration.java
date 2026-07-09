/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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