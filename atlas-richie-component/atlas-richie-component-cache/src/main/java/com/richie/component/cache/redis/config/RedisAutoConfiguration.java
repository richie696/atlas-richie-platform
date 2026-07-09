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
package com.richie.component.cache.redis.config;

import com.richie.component.cache.redis.config.base.RedisBaseAutoConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Redis 缓存服务自动配置类
 *
 * @author richie696
 * @since 1.0.0
 */
@Slf4j
@Configuration
@ComponentScan("com.richie.component.cache")
@Import({
        RedisBaseAutoConfiguration.class
})
public class RedisAutoConfiguration {

    /**
     * 构造函数
     */
    public RedisAutoConfiguration() {
        log.info("初始化Redis缓存服务配置模块");
    }

}
