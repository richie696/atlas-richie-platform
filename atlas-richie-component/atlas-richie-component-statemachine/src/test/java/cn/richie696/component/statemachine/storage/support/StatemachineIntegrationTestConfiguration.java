/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.richie696.component.statemachine.storage.support;

import cn.richie696.component.cache.config.CacheAutoConfiguration;
import cn.richie696.component.cache.local.config.LocalCacheAutoConfiguration;
import cn.richie696.component.cache.redis.config.base.RedisBaseAutoConfiguration;
import cn.richie696.component.statemachine.config.StateMachineProperties;
import cn.richie696.component.statemachine.storage.StateMachineKeyBuilder;
import cn.richie696.component.statemachine.storage.impl.RedisStateStorage;
import cn.richie696.context.bloom.BloomFilter;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.util.Set;

@SpringBootConfiguration
@EnableConfigurationProperties(StateMachineProperties.class)
@Import({
        CacheAutoConfiguration.class,
        RedisBaseAutoConfiguration.class,
        LocalCacheAutoConfiguration.class,
        RedisStateStorage.class,
        StateMachineKeyBuilder.class
})
public class StatemachineIntegrationTestConfiguration {

    /**
     * 提供 noop {@link BloomFilter} 作为测试上下文兜底。本配置用 {@code @SpringBootConfiguration}
     * 但未启用 {@code @EnableAutoConfiguration}，因此 {@code BloomFilterAutoConfiguration}
     * 不会被自动装配，而 {@code RedisStringManager} 构造器要求 {@link BloomFilter} bean，
     * 否则整个上下文加载失败。
     */
    @Bean
    @ConditionalOnMissingBean(BloomFilter.class)
    BloomFilter testBloomFilter() {
        return new BloomFilter() {
            @Override
            public boolean mightContain(String key) {
                return true;
            }

            @Override
            public void put(String key) {
            }

            @Override
            public void putAll(Set<String> keys) {
            }

            @Override
            public boolean isExists() {
                return false;
            }
        };
    }
}
