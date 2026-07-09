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
package com.richie.component.mqtt.beans;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 订阅结果
 * <p>
 * 用于表示MQTT订阅或取消订阅操作的结果，包含操作类型、主题、成功状态、消息和时间戳等信息。
 *
 * @author richie696
 * @version 1.0
 * @since 2022-09-15
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionResult implements Serializable {

    /**
     * 订阅操作类型枚举
     * <p>
     * 用于标识订阅操作的类型：订阅或取消订阅。
     */
    @Getter
    public enum SubscriptionAction {
        /**
         * 订阅操作
         */
        SUBSCRIBE,
        /**
         * 取消订阅操作
         */
        UNSUBSCRIBE,
    }

    /**
     * 订阅的主题
     */
    private String topic;

    /**
     * 订阅操作类型（订阅或取消订阅）
     */
    private SubscriptionAction action;

    /**
     * 操作是否成功
     */
    private boolean success;

    /**
     * 操作结果消息（成功或失败的原因）
     */
    private String message;

    /**
     * 操作时间戳（毫秒）
     */
    private long timestamp;
}
