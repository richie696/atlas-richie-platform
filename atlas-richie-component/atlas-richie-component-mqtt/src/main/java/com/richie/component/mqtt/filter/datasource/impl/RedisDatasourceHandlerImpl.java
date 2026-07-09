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
package com.richie.component.mqtt.filter.datasource.impl;

import com.richie.component.cache.GlobalCache;
import com.richie.component.mqtt.filter.datasource.DatasourceHandler;
import org.springframework.stereotype.Service;

import javax.management.openmbean.KeyAlreadyExistsException;

/**
 * Redis 缓存处理器类
 * <p>
 * 基于Redis实现的消息去重处理器，用于MQTT消息的幂等性控制。
 * 使用Redis存储已处理的消息标识，避免重复处理相同的消息。
 *
 * @author richie696
 * @version 2.0
 * @since 2022-09-16 18:15:03
 */
@Service("mqttRedisStoreHandler")
public class RedisDatasourceHandlerImpl implements DatasourceHandler {

    /**
     * 构造Redis数据源处理器
     * <p>
     * 使用默认构造函数，由Spring容器管理实例。
     */
    public RedisDatasourceHandlerImpl() {
    }

    @Override
    public boolean isDuplicate(String hash) {
        if (hash == null || hash.isEmpty()) {
            return true;
        }
        return GlobalCache.key().hasKey(getCacheKey(hash));
    }

    @Override
    public void saveCache(String hash, long expired) {
        if (hash == null || hash.isEmpty()) {
            return;
        }
        if (isDuplicate(hash)) {
            throw new KeyAlreadyExistsException("当前数据已存在，无法重复保存。");
        }
        GlobalCache.value().set(getCacheKey(hash), "1", expired);
    }
}
