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
package com.richie.component.cache.redis.manage;

import com.richie.component.cache.function.NotificationFunction;
import com.richie.component.cache.redis.bean.MultiRedisTemplate;
import com.richie.component.cache.redis.perf.RedisOperationCatalog;
import com.richie.component.cache.redis.perf.RedisPerfGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/**
 * 发布通知管理器，封装了Redis发布订阅（Pub/Sub）机制的通知功能。
 * <p>
 * <b>与Stream消息队列的区别：</b>
 * <ul>
 *   <li><b>发布订阅（convertAndSend）</b>：基于Redis的Pub/Sub机制，消息只在内存中，只有在线订阅者能收到，离线后消息丢失，无持久化、无消费确认，适合事件通知、在线推送等场景。</li>
 *   <li><b>Stream消息队列</b>：基于Redis Stream数据结构，消息持久化存储，支持消费组、消息确认、消息堆积和回溯，适合可靠消息、异步任务、日志收集等场景。</li>
 * </ul>
 * <b>本类只封装了发布订阅（Pub/Sub）机制，适用于在线通知、推送、事件广播等对可靠性要求不高的场景。</b>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnExpression("'${platform.cache.cache-provider:REDIS}'=='REDIS'")
public class RedisNotificationManager implements NotificationFunction {

    /** Redis 模板（JSON 序列化） */
    @Qualifier("jsonTemplate")
    private final MultiRedisTemplate<Object> redisTemplate;

    private final RedisPerfGuard redisPerfGuard;

    @Override
    public Long publishNotify(String topic, Object message) {
        return redisPerfGuard.<Long>execute("RedisNotificationManager", "publishNotify", RedisOperationCatalog.PUBLISH,
                () -> redisTemplate.convertAndSend(topic, message));
    }
}
