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
package com.richie.component.redis.streammq.config.stream;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * 缓存提供者守卫：cache-provider 非 REDIS 时阻止 streammq 组件加载。
 */
@Slf4j
@Configuration
@ConditionalOnExpression("'${platform.cache.cache-provider:REDIS}'!='REDIS'")
public class RedisStreamProviderGuard {

    private final Environment environment;

    public RedisStreamProviderGuard(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void failFast() {
        String provider = environment.getProperty("platform.cache.cache-provider", "REDIS");
        throw new IllegalStateException(
                "redis-streammq 组件依赖于 REDIS 缓存提供者，但当前配置为: " + provider + "。\n" +
                        "请将 platform.cache.cache-provider 设置为 REDIS，或移除 redis-streammq 依赖。");
    }
}
