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
package com.richie.component.messaging.event;


import com.richie.context.utils.data.JsonUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import tools.jackson.core.type.TypeReference;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.util.MimeType;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * 消息事件定义
 *
 * @author richie696
 * @version 1.0
 * @since 2022-09-27 14:49:17
 */
@ToString
public class MessageEvent implements Serializable {

    /**
     * 消息ID
     */
    @Getter
    private String messageId;

    /**
     * 主题别名（application.yml中配置的KEY，不是topic名）
     */
    @Getter
    private String topic;
    /**
     * 消息内容
     */
    @Getter
    private byte[] content;
    /**
     * 消息体类型名称
     */
    @Getter
    @Setter
    private String contentClassName;
    /**
     * 发送时间
     */
    private Long sendTime;
    /**
     * 接收时间
     */
    private Long receiveTime;
    /**
     * 是否冻结当前对象
     */
    @Getter
    private Boolean frozen;
    /**
     * 是否是延迟消息
     */
    @Getter
    private Boolean delay;
    /**
     * MQ队列Binder名称
     */
    @Setter
    @Getter
    private String binderName;
    /**
     * 消息输出类型
     */
    @Setter
    @Getter
    private MimeType outputContentType;
    /**
     * 消息重试次数
     */
    private Integer retryCount;

    /**
     * 默认构造方法
     */
    public MessageEvent() {
        this.receiveTime = System.currentTimeMillis();
    }

    /**
     * 根据主题和消息内容构造消息事件
     *
     * @param topic 主题
     * @param content 消息内容
     */
    public MessageEvent(String topic, Object content) {
        this.topic = topic;
        this.content = JsonUtils.getInstance().serializeBytes(content);
        this.receiveTime = 0L;
        this.messageId = UUID.randomUUID().toString();
    }

    /**
     * 设置消息ID的方法
     *
     * @param messageId 消息ID
     * @return 返回当前对象
     * @throws UnsupportedOperationException 若当前对象已冻结
     */
    public MessageEvent setMessageId(String messageId) {
        checkFrozen(this.messageId);
        this.messageId = messageId;
        return this;
    }

    /**
     * 获取消息值的方法
     * <p style="color: green">（注：content内容为集合/数组/或嵌套层次较深的复杂对象时建议使用此方法）
     *
     * @param reference 复杂类型转换
     * @param <T>       期望转换的目标对象类型
     * @return 返回消息值
     * @throws com.fasterxml.jackson.core.JsonProcessingException 当 content 与目标类型不匹配或 JSON 非法时
     */
    @JsonIgnore
    public <T> T getBody(TypeReference<T> reference) {
        return JsonUtils.getInstance().deserializePayload(content, reference);
    }

    /**
     * 获取消息值的方法
     * <p style="color: green">（注：content内容为简单对象时建议使用此方法）
     *
     * @param cls 简单类型转换
     * @param <T> 期望转换的目标对象类型
     * @return 返回消息值
     * @throws com.fasterxml.jackson.core.JsonProcessingException 当 content 与目标类型不匹配或 JSON 非法时
     */
    @JsonIgnore
    public <T> T getBody(Class<T> cls) {
        return JsonUtils.getInstance().deserializePayload(content, cls);
    }

    /**
     * 设置主题的方法
     *
     * @param topic 主题
     * @return 返回当前对象
     * @throws UnsupportedOperationException 若当前对象已冻结
     */
    public MessageEvent setTopic(String topic) {
        checkFrozen(this.topic);
        this.topic = topic;
        return this;
    }

    /**
     * 设置消息内容的方法
     *
     * @param content 消息内容
     * @return 返回当前对象
     * @throws UnsupportedOperationException 若当前对象已冻结
     */
    public MessageEvent setContent(byte[] content) {
        checkFrozen(this.content);
        this.content = content;
        return this;
    }

    /**
     * 设置消息发送时间的方法
     *
     * @param sendTime 消息发送时间
     * @return 返回当前对象
     * @throws UnsupportedOperationException 若当前对象已冻结
     */
    public MessageEvent setSendTime(long sendTime) {
        checkFrozen(this.sendTime);
        this.sendTime = sendTime;
        return this;
    }

    /**
     * 获取消息发送时间的方法
     *
     * @return 返回消息发送时间
     */
    public long getSendTime() {
        return sendTime;
    }

    /**
     * 获取消息接收时间的方法
     *
     * @return 返回消息接收时间
     */
    public long getReceiveTime() {
        return receiveTime;
    }

    /**
     * 设置消息接收时间的方法
     *
     * @param receiveTime 消息接收时间
     * @return 返回当前对象
     * @throws UnsupportedOperationException 若当前对象已冻结
     */
    public MessageEvent setReceiveTime(long receiveTime) {
        checkFrozen(this.receiveTime);
        this.receiveTime = receiveTime;
        return this;
    }

    /**
     * 判断当前对象是否被冻结
     *
     * @return 返回是否被冻结
     */
    @JsonIgnore
    public boolean isFrozen() {
        return Boolean.TRUE.equals(frozen);
    }

    /**
     * 设置当前对象是否被冻结
     *
     * @param frozen 是否冻结
     * @return 返回当前对象
     */
    public MessageEvent setFrozen(Boolean frozen) {
        this.frozen = frozen;
        return this;
    }

    private void checkFrozen(Object field) {
        if (Objects.nonNull(frozen) && frozen && field != null) {
            throw new UnsupportedOperationException("当前对象不允许修改操作。");
        }
    }

    /**
     * 判断当前消息是否是延迟消息
     *
     * @return 返回是否是延迟消息
     */
    @JsonIgnore
    public boolean isDelay() {
        return Boolean.TRUE.equals(delay);
    }

    /**
     * 设置当前消息是否是延迟消息
     *
     * @param delay 是否是延迟消息
     * @return 返回当前对象
     * @throws UnsupportedOperationException 若当前对象已冻结
     */
    public MessageEvent setDelay(boolean delay) {
        checkFrozen(this.delay);
        this.delay = delay;
        return this;
    }

    /**
     * 获取消息延迟时间的方法
     *
     * @return 返回消息延迟时间
     */
    @JsonIgnore
    public long getDelayTime() {
        return receiveTime - sendTime;
    }

    /**
     * 获取消息重试次数
     *
     * @return 返回消息重试次数
     */
    public int getRetryCount() {
        return retryCount == null ? 0 : retryCount;
    }

    /**
     * 重试次数+1
     * 此方法通过反射调用，不要随意修改
     */
    private void addRetryCount() {
        if (this.retryCount == null) {
            this.retryCount = 0;
        }
        this.retryCount++;
    }

    /**
     * 设置消息重试次数
     *
     * @param retryCount 消息重试次数
     * @throws UnsupportedOperationException 若当前对象已冻结
     */
    public void setRetryCount(int retryCount) {
        checkFrozen(this.retryCount);
        this.retryCount = retryCount;
    }
}
