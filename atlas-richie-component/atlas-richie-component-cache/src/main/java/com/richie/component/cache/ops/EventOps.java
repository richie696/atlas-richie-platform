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
package com.richie.component.cache.ops;

import org.springframework.data.redis.connection.MessageListener;

/**
 * Redis Key 空间事件订阅操作接口。
 * <p>用于 L2 联动之外的自定义键事件监听（如过期、删除等），需服务端开启 {@code notify-keyspace-events}。</p>
 *
 * @author richie696
 * @version 1.0.0
 * @since 2026-06-05
 */
public interface EventOps {

    /**
     * 订阅指定模式的 Key 事件。
     *
     * @param pattern  事件模式（如 {@code __keyevent@0__:expired}）
     * @param listener 消息监听器
     */
    void subscribeKeyEvent(String pattern, MessageListener listener);
}
