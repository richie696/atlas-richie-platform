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
package com.richie.component.cache.bloom;

import com.google.common.hash.Funnels;
import com.richie.component.cache.config.CacheProperties;
import com.richie.context.bloom.BloomFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Guava 实现的本地布隆过滤器，作为 {@link com.richie.context.bloom.BloomFilter} SPI 的备选实现。
 * <p>
 * <strong>激活条件</strong>：{@code platform.cache.bloom-filter.enable=true} 且
 * {@code platform.cache.bloom-filter.type=GUAVA}。
 * <p>
 * <strong>注意</strong>：当本 bean 与 {@code atlas-richie-context} 的默认 GuavaBloomFilter 同时存在时，
 * 本类通过 {@code @Primary} 优先被注入。若业务希望直接复用 context 的默认实现，把
 * {@code platform.cache.bloom-filter.enable} 设为 {@code false} 即可。
 *
 * @author richie696
 * @since 2025-06
 */
@SuppressWarnings("UnstableApiUsage")
@Primary
@Component
@ConditionalOnExpression("'${platform.cache.bloom-filter.enable:false}'=='true' && '${platform.cache.bloom-filter.type:REDISSON}'=='GUAVA'")
public class GuavaBloomFilter implements BloomFilter {

    private final com.google.common.hash.BloomFilter<String> delegate;

    public GuavaBloomFilter(CacheProperties properties) {
        this.delegate = com.google.common.hash.BloomFilter.create(Funnels.stringFunnel(StandardCharsets.UTF_8),
                properties.getBloomFilter().getExpectedInsertions(),
                properties.getBloomFilter().getFalseProbability());
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