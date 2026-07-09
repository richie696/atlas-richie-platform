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
package com.richie.component.messaging.filter.datasource;

import com.richie.component.messaging.event.MessageEvent;
import org.springframework.messaging.Message;

/**
 * 数据源处理器
 *
 * @author richie696
 * @version 1.0
 * @since 2022-09-16 18:09:05
 */
public interface DatasourceHandler {

    /**
     * 缓存KEY前缀
     */
    String KEY = "atlas:platform:duplicate:messaging:";

    /**
     * 检查当前消息是否是重复消息的方法
     *
     * @param event 待检测的消息
     * @return 返回检查结果（true：是重复消息，false：不是重复消息）
     */
    boolean isDuplicate(Message<MessageEvent> event);

    /**
     * 保存消息数据的方法（原子操作，用于幂等去重）
     * <p>
     * 使用原子操作（如 Redis SET NX）确保并发场景下只有一个实例能成功写入。
     *
     * @param event   带保存的消息
     * @param expired 该消息的过期时间（单位：毫秒）
     * @return 返回保存结果（true：成功保存，消息首次处理；false：消息已存在，重复消息）
     */
    boolean saveCache(Message<MessageEvent> event, long expired);

    /**
     * 清除缓存的方法
     *
     * @param message 待清除的消息
     */
    void clearCache(Message<MessageEvent> message);

    /**
     * 获取缓存 KEY 的方法（用于幂等去重）。
     *
     * @param message 收到的消息体
     * @return 缓存 KEY，格式为 KEY + topic + ":" + messageId
     */
    default String getCacheKey(Message<MessageEvent> message) {
        return KEY + message.getPayload().getTopic() + ":" + message.getPayload().getMessageId();
    }
}
