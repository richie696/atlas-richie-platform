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
package com.richie.component.mqtt.enums;


import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 数据源类型枚举
 *
 * @author richie696
 * @version 1.0
 * @since 2022-09-16 18:06:00
 */
@Getter
@RequiredArgsConstructor
public enum DatasourceTypeEnum {

    /**
     * 内存数据库
     */
    MEMORY("mqttMemoryStoreHandler"),

    /**
     * Redis 缓存
     */
    REDIS("mqttRedisStoreHandler");

    /**
     * 处理器名称
     */
    private final String handlerName;

}
