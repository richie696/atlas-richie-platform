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

import com.mongodb.event.ServerClosedEvent;
import com.mongodb.event.ServerDescriptionChangedEvent;
import com.mongodb.event.ServerListener;
import com.mongodb.event.ServerOpeningEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * 自定义MongoDB服务器监听器
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-03 23:55:16
 */
@Slf4j
@Component
@ConditionalOnMissingBean(ServerListener.class)
public class DefaultMongoServerListener implements ServerListener {

    /**
     * 服务器开始连接时回调。
     *
     * @param event 打开事件
     */
    @Override
    public void serverOpening(ServerOpeningEvent event) {
        log.debug("MongoDB server opening: {}", event.getServerId());
    }

    /**
     * 服务器关闭时回调。
     *
     * @param event 关闭事件
     */
    @Override
    public void serverClosed(ServerClosedEvent event) {
        log.debug("MongoDB server closed: {}", event.getServerId());
    }

    /**
     * 服务器描述变更时回调。
     *
     * @param event 描述变更事件
     */
    @Override
    public void serverDescriptionChanged(ServerDescriptionChangedEvent event) {
        log.debug("MongoDB server description changed: {} -> {}",
                event.getPreviousDescription(), event.getNewDescription());
    }

}
