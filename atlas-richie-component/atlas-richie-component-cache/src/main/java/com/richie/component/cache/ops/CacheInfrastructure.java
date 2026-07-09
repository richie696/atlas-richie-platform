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
package com.richie.component.cache.ops;

import com.richie.component.cache.enums.KeyTypeEnum;

/**
 * 缓存框架内部基础设施接口，提供 L2 缓存开关、类型注册等框架级能力。
 * <p>仅供 {@code ops} 层与 {@code redis.manage} 层内部使用，不暴露给业务侧。
 *
 * @author richie696
 * @version 1.0.0
 * @since 2026-06-04
 */
public interface CacheInfrastructure {

    /**
     * 获取 Redis 连接信息（调试/日志用）。
     */
    String getConnectionString();

    /**
     * 检查是否启用二级缓存。
     */
    boolean enableL2Caching();

    /**
     * 检查指定数据类型是否开启二级缓存。
     */
    boolean enableKeyTypeCache(KeyTypeEnum keyType);

    /**
     * 获取指定 Key 的已注册值类型。
     */
    Class<?> getValueType(String key);

    /**
     * 注册 Key 的值类型（用于反序列化）。
     */
    void registerType(String key, Class<?> clazz);
}
