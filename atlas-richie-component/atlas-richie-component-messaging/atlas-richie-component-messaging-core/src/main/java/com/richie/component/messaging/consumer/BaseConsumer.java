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
package com.richie.component.messaging.consumer;

import com.richie.component.messaging.config.MessagingProperties;
import com.richie.component.messaging.event.MessageEvent;
import com.richie.component.messaging.filter.CanaryMessageFilter;
import com.richie.component.messaging.filter.handler.MessageHandlerService;
import com.richie.component.messaging.service.MessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import jakarta.annotation.Nullable;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.util.function.Function;

/**
 * 消息队列消费者
 * <p>
 * 支持灰度发布功能，当满足以下条件时会启用灰度消息过滤：
 * 1. Gateway 的灰度配置（DeployConfig）存在
 * 2. 服务发现（DiscoveryClient）可用
 * <p>
 * 如果上述条件不满足，CanaryMessageFilter 将为 null，消息将正常处理（不进行灰度过滤）
 *
 * @author richie696
 * @version 2.0
 * @since 2025/12/10
 */
@Slf4j
@Component
public class BaseConsumer extends AbstractBaseConsumer {

    /**
     * 构造函数
     * <p>
     * CanaryMessageFilter 是条件 Bean，只有在以下条件满足时才会创建：
     * - DeployConfig 存在（Gateway 灰度配置可用）
     * - DiscoveryClient 存在（服务发现可用）
     * <p>
     * 如果条件不满足，canaryMessageFilter 将为 null，消息将正常处理（不进行灰度过滤）
     *
     * @param messageHandlerService 消息处理器
     * @param messageService        消息服务
     * @param properties            消息配置
     * @param canaryMessageFilter   灰度消息过滤器（可选，如果未启用灰度功能则为 null）
     */
    public BaseConsumer(MessageHandlerService messageHandlerService,
                        MessageService messageService,
                        MessagingProperties properties,
                        @Nullable CanaryMessageFilter canaryMessageFilter) {
        super(messageHandlerService, messageService, properties, canaryMessageFilter);
        if (canaryMessageFilter != null) {
            log.info("BaseConsumer initialized with canary message filter enabled");
        } else {
            log.debug("BaseConsumer initialized without canary message filter (gray release not enabled or dependencies not available)");
        }
    }

    /**
     * 普通消息处理
     *
     * @return 消息处理函数
     */
    @Bean
    public Function<Message<MessageEvent>, MessageEvent> normalProcess() {
        return getMessageFunction(false);
    }

    /**
     * 延迟消息处理
     *
     * @return 消息处理函数
     */
    @Bean
    public Function<Message<MessageEvent>, MessageEvent> delayProcess() {
        return getMessageFunction(true);
    }

}
