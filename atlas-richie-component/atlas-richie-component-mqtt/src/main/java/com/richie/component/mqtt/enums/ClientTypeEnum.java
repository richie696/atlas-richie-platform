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
package com.richie.component.mqtt.enums;

/**
 * 客户端类型枚举
 * <p>
 * 用于区分MQTT客户端是服务端还是客户端。
 *
 * @author richie696
 * @version 1.0
 * @since 2025-06-30 23:18:54
 */
public enum ClientTypeEnum {

    /**
     * 服务端类型
     * <p>
     * 用于标识MQTT服务端实例。
     */
    SERVER,

    /**
     * 客户端类型
     * <p>
     * 用于标识MQTT客户端实例。
     */
    CLIENT
}
