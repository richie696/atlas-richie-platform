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
package com.richie.component.redis.streammq.stream;

import org.springframework.data.redis.connection.stream.RecordId;
import lombok.Builder;

/**
 * Redis Stream 消息事件
 *
 * <p>事件总线中分发的标准事件模型，封装了流键、消费者组、消费者、记录ID与消息载荷。
 *
 * @param <T>       事件载荷类型
 * @param streamKey 流键
 * @param group     消费者组
 * @param consumer  消费者名称
 * @param recordId  记录ID
 * @param payload   事件载荷
 */
@Builder
public record StreamMessageEvent<T>(String streamKey, String group, String consumer, RecordId recordId, T payload) {
}


