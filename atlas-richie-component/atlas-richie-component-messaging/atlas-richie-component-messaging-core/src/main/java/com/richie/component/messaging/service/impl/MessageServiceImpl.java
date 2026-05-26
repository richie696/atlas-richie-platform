package com.richie.component.messaging.service.impl;

import com.richie.context.common.api.HeaderContextHolder;
import com.richie.contract.constant.GlobalConstants;
import com.richie.component.messaging.event.MessageEvent;
import com.richie.component.messaging.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

import java.lang.reflect.InvocationTargetException;
import java.util.Objects;
import java.util.UUID;

/**
 * MQ消息队列服务接口默认实现类
 *
 * @author richie696
 * @version 1.0
 * @since 2022-09-27 11:15:28
 */
@Slf4j
@Service("defaultMessageService")
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {

    /** Spring Cloud Stream 桥接器，用于发送消息到 binding */
    private final StreamBridge streamBridge;

    @Override
    public boolean sendMessage(String topicAlias, Object message) {
        return sendDelayMessage(topicAlias, null, message, null, 0L);
    }

    @Override
    public boolean sendMessage(String topicAlias, String binderName, Object message) {
        return sendDelayMessage(topicAlias, binderName, message, null, 0L);
    }

    @Override
    public boolean sendMessage(String topicAlias, Object message, MimeType outputContentType) {
        return sendDelayMessage(topicAlias, null, message, outputContentType, 0L);
    }

    @Override
    public boolean sendMessage(String topicAlias, String binderName, Object body, MimeType outputContentType) {
        return sendDelayMessage(topicAlias, binderName, body, outputContentType, 0L);
    }

    @Override
    public boolean sendDelayMessage(String topicAlias, Object message, long delayTime) {
        return sendDelayMessage(topicAlias, null, message, null, delayTime);
    }

    @Override
    public boolean sendDelayMessage(String topicAlias, String binderName, Object message, long delayTime) {
        return sendDelayMessage(topicAlias, binderName, message, null, delayTime);
    }

    @Override
    public boolean sendDelayMessage(String topicAlias, Object message, MimeType outputContentType, long delayTime) {
        return sendDelayMessage(topicAlias, null, message, outputContentType, delayTime);
    }

    @Override
    public boolean sendDelayMessage(String topicAlias, String binderName, Object body, MimeType outputContentType, long delayTime) {
        streamBridge.afterSingletonsInstantiated();
        var event = new MessageEvent(topicAlias, body);
        if (Objects.isNull(outputContentType)) {
            outputContentType = MimeTypeUtils.APPLICATION_JSON;
        }
        event.setBinderName(binderName);
        event.setContentClassName(body.getClass().getName());
        event.setSendTime(System.currentTimeMillis());
        event.setMessageId(UUID.randomUUID().toString());
        Message<MessageEvent> message = getMessage(event, delayTime);
        var result = streamBridge.send(event.getTopic(), event.getBinderName(), message, outputContentType);
        log.info("[Message Publish] result: {}, messageId: {}, topic: {}",
                result, event.getMessageId(), event.getTopic());
        return result;
    }

    /**
     * 将消息重新发布到原 topic（用于消费失败重试）。
     *
     * @param event 原消息事件（会解冻并增加重试次数）
     * @return true 发送成功，false 发送失败
     */
    public boolean rePublishMessage(MessageEvent event) {
        streamBridge.afterSingletonsInstantiated();
        var outputContentType = event.getOutputContentType();
        if (Objects.isNull(outputContentType)) {
            outputContentType = MimeTypeUtils.APPLICATION_JSON;
        }
        event.setFrozen(false);
        try {
            var retryCountMethod = event.getClass().getDeclaredMethod("addRetryCount");
            retryCountMethod.setAccessible(true);
            retryCountMethod.invoke(event);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            log.error("Add retry count error: {}", e.getMessage());
        }
        var result = streamBridge.send(event.getTopic(), event.getBinderName(), event, outputContentType);
        log.info("[Message RePublish] result: {}, messageId: {}, topic: {}, retryCount: {}",
                result, event.getMessageId(), event.getTopic(), event.getRetryCount());
        return result;
    }


    /**
     * 将 MessageEvent 包装为 Spring Message，并注入请求头（时区、语言、租户、灰度等）与延迟头。
     *
     * @param event     消息事件
     * @param delayTime 延迟时间（毫秒），大于 0 时设置 x-delay 头
     * @return 构建好的 Message
     */
    private Message<MessageEvent> getMessage(MessageEvent event, long delayTime) {
        var builder = MessageBuilder.withPayload(event);
        var timeFormat = HeaderContextHolder.getHeader(GlobalConstants.X_TIME_FORMAT_PATTERN);
        if (StringUtils.isNotBlank(timeFormat)) {
            builder.setHeader(GlobalConstants.X_TIME_FORMAT_PATTERN, timeFormat);
        }
        var currencyFormat = HeaderContextHolder.getHeader(GlobalConstants.X_CURRENCY_FORMAT_PATTERN);
        if (StringUtils.isNotBlank(currencyFormat)) {
            builder.setHeader(GlobalConstants.X_CURRENCY_FORMAT_PATTERN, currencyFormat);
        }
        var timezone = HeaderContextHolder.getHeader(GlobalConstants.X_RD_REQUEST_TIMEZONE);
        if (StringUtils.isNotBlank(timezone)) {
            builder.setHeader(GlobalConstants.X_RD_REQUEST_TIMEZONE, timezone);
        }
        var language = HeaderContextHolder.getHeader(GlobalConstants.X_RD_REQUEST_LANGUAGE);
        if (StringUtils.isNotBlank(language)) {
            builder.setHeader(GlobalConstants.X_RD_REQUEST_LANGUAGE, language);
        }
        var shopCode = HeaderContextHolder.getHeader(GlobalConstants.X_RD_REQUEST_SHOP_CODE);
        if (StringUtils.isNotBlank(shopCode)) {
            builder.setHeader(GlobalConstants.X_RD_REQUEST_SHOP_CODE, shopCode);
        }
        var tenantCode = HeaderContextHolder.getHeader(GlobalConstants.X_TENANT_CODE_TOKEN);
        if (StringUtils.isNotBlank(tenantCode)) {
            builder.setHeader(GlobalConstants.X_TENANT_CODE_TOKEN, tenantCode);
        }
        // 灰度标识传递：从请求上下文获取 X-Canary-Id，设置到消息头
        // 灰度发布统一使用 ID 模式，只需传递 X-Canary-Id
        var canaryId = HeaderContextHolder.getHeader(GlobalConstants.X_CANARY_ID);
        if (StringUtils.isNotBlank(canaryId)) {
            builder.setHeader(GlobalConstants.X_CANARY_ID, canaryId);
        }
        if (delayTime > 0L) {
            event.setDelay(true);
            builder.setHeader("x-delay", delayTime);
        } else {
            event.setDelay(false);
        }
        return builder.build();
    }
}
