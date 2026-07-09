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
package com.richie.component.redis.streammq.bean;

import com.richie.component.redis.streammq.stream.EventContext;
import com.richie.contract.model.BaseStreamMessage;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * 通用死信队列消息封装类
 *
 * <p>使用 record 类实现，提供不可变的数据传输对象
 * <p>自动生成构造函数、getter、equals、hashCode、toString 方法
 *
 * @param originalMessage 原始消息（使用Object类型，支持任意消息类型）
 * @param originalMessageType 原始消息类型，用于反序列化
 * @param originalStreamKey 原始Stream键名
 * @param originalGroup 原始消费者组
 * @param originalRecordId 原始记录ID
 * @param errorMessage 错误消息
 * @param errorType 错误类型
 * @param stackTrace 异常堆栈信息
 * @param timestamp 时间戳
 * @param sourceConsumer 来源消费者
 * @param retryCount 重试次数
 * @param businessId 业务ID，如订单号、用户ID等
 * @param priority 优先级：HIGH, MEDIUM, LOW
 */
public record DeadLetterMessage(
        Object originalMessage,
        String originalMessageType,
        String originalStreamKey,
        String originalGroup,
        String originalRecordId,
        String errorMessage,
        String errorType,
        String stackTrace,
        Long timestamp,
        String sourceConsumer,
        Integer retryCount,
        String businessId,
        String priority
) implements BaseStreamMessage {

    /**
     * 创建死信队列消息的便捷方法
     *
     * @param originalMessage 原始消息
     * @param error 异常对象
     * @param ctx 事件上下文
     * @param sourceConsumer 来源消费者
     * @return 死信队列消息
     */
    public static DeadLetterMessage of(Object originalMessage, Throwable error, EventContext ctx, String sourceConsumer) {
        return new DeadLetterMessage(
                originalMessage,
                originalMessage.getClass().getName(),
                ctx.streamKey(),
                ctx.group(),
                ctx.recordId().getValue(),
                error.getMessage(),
                error.getClass().getSimpleName(),
                getStackTrace(error),
                System.currentTimeMillis(),
                sourceConsumer,
                0,
                null,
                null
        );
    }

    /**
     * 创建带业务ID的死信队列消息
     *
     * @param originalMessage 原始消息
     * @param error 异常对象
     * @param ctx 事件上下文
     * @param sourceConsumer 来源消费者
     * @param businessId 业务ID
     * @param priority 优先级
     * @return 死信队列消息
     */
    public static DeadLetterMessage of(Object originalMessage, Throwable error, EventContext ctx,
                                       String sourceConsumer, String businessId, String priority) {
        return new DeadLetterMessage(
                originalMessage,
                originalMessage.getClass().getName(),
                ctx.streamKey(),
                ctx.group(),
                ctx.recordId().getValue(),
                error.getMessage(),
                error.getClass().getSimpleName(),
                getStackTrace(error),
                System.currentTimeMillis(),
                sourceConsumer,
                0,
                businessId,
                priority
        );
    }

    /**
     * 获取异常堆栈信息
     *
     * @param error 异常对象
     * @return 堆栈字符串
     */
    private static String getStackTrace(Throwable error) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        error.printStackTrace(pw);
        return sw.toString();
    }
}
