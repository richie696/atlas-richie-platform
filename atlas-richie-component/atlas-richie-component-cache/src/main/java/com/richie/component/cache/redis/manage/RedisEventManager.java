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
package com.richie.component.cache.redis.manage;

import com.richie.component.cache.function.EventFunction;
import com.richie.component.cache.redis.perf.RedisOperationCatalog;
import com.richie.component.cache.redis.perf.RedisPerfGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

/**
 * Redis Key 事件订阅与通知管理器
 *
 * <p>封装基于 Redis 发布/订阅机制的 Key 事件监听能力，支持对过期、删除等事件的订阅与回调。
 *
 * <p>主要功能：
 * <ul>
 *   <li>按模式订阅 Key 事件（如 __keyevent@0__:expired）</li>
 *   <li>注册监听器并转发到业务回调</li>
 *   <li>用于缓存失效通知、分布式事件驱动等场景</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0.0
 * @since 2025-06-25 17:47:29
 */
@Component
@RequiredArgsConstructor
@ConditionalOnExpression("'${platform.cache.cache-provider:REDIS}'=='REDIS'")
public class RedisEventManager implements EventFunction {

    /** Redis 消息监听容器 */
    private final RedisMessageListenerContainer container;

    private final RedisPerfGuard redisPerfGuard;

    /**
     * 订阅指定模式的Key事件。
     *
     * @param pattern 事件模式（如__keyevent@0__:expired）
     * @param listener 消息监听器，收到事件时回调
     * @apiNote
     * <p><b>时间复杂度</b>：应用侧注册监听为 {@code O(1)}；后续事件流负载与键空间与订阅模式相关。
     * <p><b>严禁</b>：过于宽泛的 pattern 导致服务端事件风暴。
     * <p><b>可用</b>：缓存失效联动、运维事件等。
     * <p><b>注意</b>：Keyspace 通知需服务端开启相关配置；回调内勿阻塞。
     */
    @Override
    public void subscribeKeyEvent(String pattern, MessageListener listener) {
        redisPerfGuard.execute("RedisEventManager", "subscribeKeyEvent", RedisOperationCatalog.EVENT_SUBSCRIBE,
                () -> container.addMessageListener(listener, new PatternTopic(pattern)));
    }
}
