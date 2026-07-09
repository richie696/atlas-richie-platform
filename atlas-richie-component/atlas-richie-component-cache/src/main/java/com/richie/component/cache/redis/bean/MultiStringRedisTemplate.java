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
package com.richie.component.cache.redis.bean;

import com.richie.contract.exception.PlatformRuntimeException;
import lombok.NoArgsConstructor;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import jakarta.annotation.Nonnull;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 多节点 StringRedisTemplate
 *
 * <p>支持在同一应用内路由到不同的 StringRedisTemplate 实例，通过约定的 key 规则进行选择。
 *
 * <p>主要功能：
 * <ul>
 *   <li>按前缀路由：形如 prefix@@key 的键路由到对应从库模板</li>
 *   <li>便捷访问：提供获取与设置从库模板的接口</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0
 * @since 2023-07-07 15:29:24
 */
@NoArgsConstructor
public class MultiStringRedisTemplate extends StringRedisTemplate {

    /** 从库/分库前缀到 StringRedisTemplate 的映射（prefix -> template） */
    private final Map<String, StringRedisTemplate> slaveTemplateMap = new ConcurrentHashMap<>();

    /**
     * Constructs a new {@link MultiStringRedisTemplate} instance.
     * @param connectionFactory RedisConnectionFactory
     */
    public MultiStringRedisTemplate(RedisConnectionFactory connectionFactory) {
        super(connectionFactory);
    }

    /**
     * 设置子节点template
     * @param slaveTemplateMap 子节点template
     */
    public void setSlaveTemplateMap(Map<String, StringRedisTemplate> slaveTemplateMap) {
        this.slaveTemplateMap.putAll(slaveTemplateMap);
    }

    /**
     * 根据key获取对应的template
     * @param key key
     * @return template
     */
    public StringRedisTemplate getSlaveTemplate(String key) {
        return slaveTemplateMap.get(key);
    }

    /**
     * 根据key获取对应的template
     * @param key key
     * @return template
     */
    public StringRedisTemplate getTargetTemplate(@Nonnull String key) {
        if (key.contains("@@")) {
            String[] keys = key.split("@@");
            StringRedisTemplate value = slaveTemplateMap.get(keys[0]);
            if (Objects.isNull(value)) {
                throw new PlatformRuntimeException("The key '%s' is not found in slave redis template".formatted(key));
            }
            return value;
        }
        return this;
    }

}
