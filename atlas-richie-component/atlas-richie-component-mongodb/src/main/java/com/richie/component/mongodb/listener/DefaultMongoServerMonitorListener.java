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
package com.richie.component.mongodb.listener;

import com.mongodb.event.ServerHeartbeatFailedEvent;
import com.mongodb.event.ServerHeartbeatStartedEvent;
import com.mongodb.event.ServerHeartbeatSucceededEvent;
import com.mongodb.event.ServerMonitorListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 自定义MongoDB心跳监听器
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-03 23:55:01
 */
@Slf4j
@Component
@ConditionalOnMissingBean(ServerMonitorListener.class)
public class DefaultMongoServerMonitorListener implements ServerMonitorListener {

    /**
     * 心跳开始时回调。
     *
     * @param event 心跳开始事件
     */
    @Override
    public void serverHearbeatStarted(ServerHeartbeatStartedEvent event) {
        log.debug("MongoDB heartbeat started: {}", event.getConnectionId());
    }

    /**
     * 心跳成功时回调。
     *
     * @param event 心跳成功事件
     */
    @Override
    public void serverHeartbeatSucceeded(ServerHeartbeatSucceededEvent event) {
        log.debug("MongoDB heartbeat succeeded: {} in {}ms",
                event.getConnectionId(), event.getElapsedTime(TimeUnit.MILLISECONDS));
    }

    /**
     * 心跳失败时回调。
     *
     * @param event 心跳失败事件
     */
    @Override
    public void serverHeartbeatFailed(ServerHeartbeatFailedEvent event) {
        log.warn("MongoDB heartbeat failed: {} - {}",
                event.getConnectionId(), event.getThrowable().getMessage());
    }

}
