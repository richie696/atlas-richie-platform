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
package com.richie.component.cache.redis.manage;

import com.richie.context.utils.data.JsonUtils;
import com.richie.component.cache.redis.enums.TopicTypeEnum;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.Topic;

import java.util.Objects;

/**
 * Redis消息订阅者抽象类
 *
 * @param <T> 消息类型
 * @author richie696
 * @version 1.0.0
 * @since 2024-08-12 22:56:38
 */
@Slf4j
public abstract class MessageSubscriber<T> implements MessageListener {

    /**
     * 接收到消息
     *
     * @param message 消息
     * @param pattern 匹配模式
     */
    @Override
    public void onMessage(@Nonnull Message message, byte[] pattern) {
        String body = new String(message.getBody());
        log.info("Received message: {}", body);
        if (body.startsWith("\"")) {
            body = body.substring(1, body.length() - 1);
        }
        if (body.contains("\\")) {
            body = body.replace("\\", "");
        }
        T value = JsonUtils.getInstance().deserialize(body, getValueType());
        Objects.requireNonNull(value, "Message value is null.");
        handleMessage(value, pattern);
    }

    /**
     * 接收到的消息的类型
     * @return 返回消息类型定义
     */
    protected abstract Class<T> getValueType();

    /**
     * 消息处理器
     * @param message 消息
     * @param pattern 匹配模式
     */
    protected abstract void handleMessage(@Nonnull T message, byte[] pattern);

    /**
     * 获取订阅的主题
     * @return 返回订阅主题
     */
    protected abstract String getTopicName();

    /**
     * 获取订阅的主题类型
     * @return 返回主题类型
     */
    protected abstract TopicTypeEnum getTopicType();

    /**
     * 获取订阅的主题
     * @return 订阅主题
     */
    public Topic getTopic() {
        return getTopicType() == TopicTypeEnum.CHANNEL ?
                ChannelTopic.of(getTopicName()) :
                PatternTopic.of(getTopicName());
    }

}
