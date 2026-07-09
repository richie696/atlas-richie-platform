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
package com.richie.component.web.core.sse;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SSE 连接包装（DESIGN §4.4）。
 * <p>
 * 持有 {@link SseEmitter} + 元数据（tags / createdAt / lastHeartbeatAt），
 * 供 {@link SseManager} 在内部 map 中跟踪每个活跃连接。
 *
 * @param clientId        业务侧客户端唯一 ID（必填）
 * @param emitter         底层 Spring SseEmitter（必填）
 * @param createdAt       连接建立时刻
 * @param tags            业务标签集合（线程安全；用于 broadcastByTag）
 * @param lastHeartbeatAt 最近一次成功心跳的 epoch millis（用于 stale 检测）
 *
 * @author richie696
 * @since 2026-07
 */
public record SseConnection(String clientId,
                            SseEmitter emitter,
                            Instant createdAt,
                            Set<String> tags,
                            AtomicLong lastHeartbeatAt) {

    public SseConnection(String clientId, SseEmitter emitter) {
        this(clientId, emitter, Instant.now(),
                ConcurrentHashMap.newKeySet(),
                new AtomicLong(System.currentTimeMillis()));
    }

    public void tag(String tag) {
        tags.add(tag);
    }

    public void untag(String tag) {
        tags.remove(tag);
    }

    public boolean hasTag(String tag) {
        return tags.contains(tag);
    }

    public void touch() {
        lastHeartbeatAt.set(System.currentTimeMillis());
    }
}