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
package com.richie.component.messaging.consumer;

import com.richie.context.common.api.HeaderContextHolder;
import com.richie.component.messaging.config.MessagingProperties;
import com.richie.component.messaging.event.MessageEvent;
import com.richie.component.messaging.filter.CanaryMessageFilter;
import com.richie.component.messaging.filter.handler.MessageHandlerService;
import com.richie.component.messaging.service.MessageService;
import com.richie.component.messaging.service.impl.MessageServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.i18n.LocaleContextHolder;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.springframework.messaging.Message;

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.richie.contract.constant.GlobalConstants.*;


/**
 * 消息队列消费者
 *
 * @author richie696
 * @version 1.0
 * @since 2021/08/11
 */
@Slf4j
@RequiredArgsConstructor
public abstract class AbstractBaseConsumer {

    /**
     * 消息处理器
     */
    protected final MessageHandlerService messageHandlerService;
    @Qualifier("defaultMessageService")
    protected final MessageService messageService;
    private final MessagingProperties properties;

    /**
     * 灰度消息过滤器（可选，如果未配置则不进行灰度过滤）
     */
    @Nullable
    private final CanaryMessageFilter canaryMessageFilter;

    protected static final ConcurrentMap<String, Function<MessageEvent, Boolean>> CONSUMER_MAP = new ConcurrentHashMap<>(10);

    /**
     * 获取消息处理函数
     *
     * @param isDelay 是否延迟
     * @return 消息处理函数
     */
    @Nonnull
    protected Function<Message<MessageEvent>, MessageEvent> getMessageFunction(boolean isDelay) {
        return message -> {
            MessageEvent event = null;
            try {
                event = message.getPayload();
            } catch (Exception e) {
                log.error("消息反序列化失败，消息内容：{}", message);
                return event;
            }
            log.info("接收到新消息(ID = {})", event.getMessageId());
            var topic = event.getTopic();
            if (!CONSUMER_MAP.containsKey(topic)) {
                log.warn("当前消息无可用主题，消息内容：{}", event);
                return event;
            }

            // 灰度消息过滤：判断是否应该处理该消息（优先执行，避免不必要的幂等去重）
            // 注意：灰度过滤必须在幂等去重之前执行，原因：
            // 1. 如果使用 Redis 共享缓存，灰度消息被不符合条件的实例消费后，会先执行幂等去重（写入 Redis）
            //    然后被灰度过滤器过滤，可能导致符合条件的实例（如灰度实例）也认为消息重复
            // 2. 避免不符合条件的实例进行不必要的幂等去重检查，提高性能
            // 3. 灰度过滤是轻量级检查，应该优先执行
            if (canaryMessageFilter != null && !canaryMessageFilter.shouldProcess(message)) {
                if (log.isDebugEnabled()) {
                    log.debug("消息被灰度过滤器跳过，消息ID: {}, topic: {}", event.getMessageId(), topic);
                }
                // 返回 null 表示消息已处理（跳过），不进行重试
                return null;
            }

            // 幂等去重处理（在灰度过滤之后执行，只对符合条件的消息进行去重）
            if (!messageHandlerService.saveCache(message, TimeUnit.MINUTES.toMillis(2))) {
                log.debug("重复消息，被过滤。消息ID: {}, topic: {}", event.getMessageId(), topic);
                return null;
            }

            event
                    .setFrozen(false)
                    .setReceiveTime(System.currentTimeMillis())
                    .setDelay(isDelay)
                    .setFrozen(true);
            // 设置请求头
            setHeader(message);
            var result = false;
            try {
                result = CONSUMER_MAP.get(topic).apply(event);
            } catch (Exception e) {
                log.error("消息处理失败，消息内容：{}", event, e);
            }
            if (event.getRetryCount() >= properties.getMaxRetries()) {
                log.warn("消息重试次数已达上限，消息内容：{}", event);
                return null;
            }
            if (!result) {
                messageHandlerService.clearCache(message);
                var rePublishResult = ((MessageServiceImpl) messageService).rePublishMessage(event);
                if (!rePublishResult) {
                    log.error("消息重新入队失败，消息内容：{}", event);
                    return null;
                }
            }
            return null;
        };
    }

    /**
     * 将消息头中的时区、语言、租户、灰度等写入请求上下文与 LocaleContextHolder。
     *
     * @param message 当前消息
     */
    private void setHeader(Message<MessageEvent> message) {
        Consumer<String> lineHandler = headerKey -> {
            if (message.getHeaders().containsKey(headerKey)) {
                var value = Objects.toString(message.getHeaders().get(headerKey));
                HeaderContextHolder.setHeader(headerKey, value);
                if (headerKey.equals(X_RD_REQUEST_LANGUAGE)) {
                    LocaleContextHolder.setLocale(Locale.forLanguageTag(value));
                }
            }
        };
        lineHandler.accept(X_TIME_FORMAT_PATTERN);
        lineHandler.accept(X_CURRENCY_FORMAT_PATTERN);
        lineHandler.accept(X_RD_REQUEST_TIMEZONE);
        lineHandler.accept(X_RD_REQUEST_LANGUAGE);
        lineHandler.accept(X_RD_REQUEST_SHOP_CODE);
        lineHandler.accept(X_TENANT_ID);
        lineHandler.accept(X_ACCESS_TOKEN);
        // 灰度标识传递：从消息头恢复 X-Canary-Id 到请求上下文
        lineHandler.accept(X_CANARY_ID);
        lineHandler.accept(X_CANARY_CATEGORY);
    }


    /**
     * 注册消费者回调函数的方法
     *
     * @param topicAlias       主题别名
     * @param consumerCallback 消费者回调
     * @throws IllegalArgumentException 若 topicAlias 已注册
     */
    public void registerConsumer(String topicAlias, Function<MessageEvent, Boolean> consumerCallback) {
        if (CONSUMER_MAP.containsKey(topicAlias)) {
            throw new IllegalArgumentException("当前消费者已注册，无法重复注册。");
        }
        CONSUMER_MAP.put(topicAlias, consumerCallback);
        log.info("当前消费者列表：{}", CONSUMER_MAP.keySet());
    }

    /**
     * 取消注册消费者的接口
     *
     * @param topicAlias 主题别名
     */
    public void unregisterConsumer(String topicAlias) {
        if (!CONSUMER_MAP.containsKey(topicAlias)) {
            log.error("当前消息无可用主题，无法取消注册。");
            return;
        }
        CONSUMER_MAP.remove(topicAlias);
    }

}
