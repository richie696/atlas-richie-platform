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
package com.richie.component.cache.function;

/**
 * 发布通知API管理器接口，封装了基于Redis发布订阅（Pub/Sub）机制的通知功能。
 * <p>
 * <b>与Stream消息队列的区别：</b>
 * <ul>
 *   <li><b>发布订阅（convertAndSend）</b>：基于Redis的Pub/Sub机制，消息只在内存中，只有在线订阅者能收到，离线后消息丢失，无持久化、无消费确认，适合事件通知、在线推送等场景。</li>
 *   <li><b>Stream消息队列</b>：基于Redis Stream数据结构，消息持久化存储，支持消费组、消息确认、消息堆积和回溯，适合可靠消息、异步任务、日志收集等场景。</li>
 * </ul>
 * <b>本接口只封装了发布订阅（Pub/Sub）机制，适用于在线通知、推送、事件广播等对可靠性要求不高的场景。</b>
 * <br/>
 * 若需可靠消息、消费确认、消息堆积等能力，请使用RedisStreamManager。
 *
 * @author richie696
 * @version 1.0
 * @since 2025-06-26 10:53:37
 */
public interface NotificationFunction extends CacheFunction {
    /**
     * 发布消息到指定频道的方法
     *
     * @param topic 发布消息的主题
     * @param message 消息内容
     * @return 返回接收到消息的订阅者数量（当Redis处于管道或事务环境中时，返回null）
     */
    Long publishNotify(String topic, Object message);
}
