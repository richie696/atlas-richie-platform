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

import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Map;

/**
 * Redis Stream 控制总线
 *
 * <p>基于 Reactor 的应用内控制事件总线，主要用于查询/响应类的控制消息（如拉取器状态查询）。
 *
 * <p>主要功能：
 * <ul>
 *   <li>发布与订阅控制事件，解耦端点与拉取器等组件</li>
 *   <li>支持携带 correlationId 的请求-响应查询模式</li>
 *   <li>多播模型并具备背压缓冲</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0.0
 * @since 2025-09-16
 */
public final class RedisStreamControlBus {

    private RedisStreamControlBus() {}

    /**
     * 控制事件
     */
    public sealed interface ControlEvent permits PollerStatusQuery, PollerStatusResponse {}

    /**
     * 拉取器状态查询事件
     *
     * @param correlationId 关联 ID，用于匹配请求与响应
     */
    public record PollerStatusQuery(String correlationId) implements ControlEvent {
    }

    /**
     * 拉取器状态响应事件
     *
     * @param correlationId 关联 ID
     * @param snapshot      拉取器状态快照
     */
    public record PollerStatusResponse(String correlationId, Map<String, Object> snapshot) implements ControlEvent {
    }

    /** 控制事件多播 Sink（背压缓冲） */
    private static final Sinks.Many<ControlEvent> controlSink =
            Sinks.many().multicast().onBackpressureBuffer();

    /**
     * 发布控制事件
     *
     * @param event 控制事件
     */
    public static void publish(ControlEvent event) {
        controlSink.tryEmitNext(event);
    }

    /**
     * 订阅控制事件
     *
     * @return 事件流对象
     */
    public static Flux<ControlEvent> controlFlow() {
        return controlSink.asFlux();
    }
}


