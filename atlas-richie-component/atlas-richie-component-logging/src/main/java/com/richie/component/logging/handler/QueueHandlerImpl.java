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
package com.richie.component.logging.handler;

import com.richie.context.utils.data.JsonUtils;
import com.richie.component.messaging.event.MessageEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

import java.util.Objects;

/**
 * MQ消息队列服务接口默认实现类
 *
 * @author richie696
 * @version 1.0
 * @since 2022-09-27 11:15:28
 */
@Slf4j
@RequiredArgsConstructor
@Service
class QueueHandlerImpl implements QueueHandler {

    /** Spring Cloud Stream 桥接器，用于发送消息 */
    private final StreamBridge streamBridge;

    @Override
    public boolean sendMessage(String topicAlias, Object message) {
        return sendMessage(topicAlias, null, message, null, 0L);
    }

    @Override
    public boolean sendMessage(String topicAlias, String binderName, Object message) {
        return sendMessage(topicAlias, binderName, message, null, 0L);
    }

    @Override
    public boolean sendMessage(String topicAlias, Object message, MimeType outputContentType) {
        return sendMessage(topicAlias, null, message, outputContentType, 0L);
    }

    @Override
    public boolean sendMessage(String topicAlias, String binderName, Object body, MimeType outputContentType) {
        return sendMessage(topicAlias, binderName, body, outputContentType, 0L);
    }

    @Override
    public boolean sendMessage(String topicAlias, Object message, long delayTime) {
        return sendMessage(topicAlias, null, message, null, delayTime);
    }

    @Override
    public boolean sendMessage(String topicAlias, String binderName, Object message, long delayTime) {
        return sendMessage(topicAlias, binderName, message, null, delayTime);
    }

    @Override
    public boolean sendMessage(String topicAlias, Object message, MimeType outputContentType, long delayTime) {
        return sendMessage(topicAlias, null, message, outputContentType, delayTime);
    }

    @Override
    public boolean sendMessage(String topicAlias, String binderName, Object body, MimeType outputContentType, long delayTime) {
        streamBridge.afterSingletonsInstantiated();
        var event = new MessageEvent(topicAlias, body);
        event.setBinderName(binderName);
        event.setContentClassName(body.getClass().getName());
        event.setSendTime(System.currentTimeMillis());
        log.debug("\n[Message Service] Send message: \n{}", JsonUtils.getInstance().serialize(body, true));
        if (Objects.isNull(outputContentType)) {
            outputContentType = MimeTypeUtils.APPLICATION_JSON;
        }
        Message<MessageEvent> message;
        if (delayTime > 0L) {
            event.setDelay(true);
            message = MessageBuilder.withPayload(event).setHeader("x-delay", delayTime).build();
        } else {
            event.setDelay(false);
            message = MessageBuilder.withPayload(event).build();
        }
        var send = streamBridge.send(topicAlias, binderName, message, outputContentType);
        log.info("[MQ] Send Result = {}", send);
        return send;
    }
}
