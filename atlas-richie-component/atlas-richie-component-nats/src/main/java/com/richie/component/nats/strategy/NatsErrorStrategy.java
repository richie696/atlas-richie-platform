package com.richie.component.nats.strategy;

import io.nats.client.Message;

/**
 * NATS 错误处理策略接口
 *
 * <p>定义发布/消费过程中的错误处理和重试决策。
 * 默认实现记录日志并按条件重试，用户可通过 Bean 替换自定义实现。</p>
 *
 * @author richie696
 * @since 1.0.0
 */
public interface NatsErrorStrategy {

    /**
     * 发布消息失败时的处理
     *
     * @param subject NATS subject
     * @param data    消息体
     * @param e       异常
     */
    void onPublishError(String subject, byte[] data, Exception e);

    /**
     * 消费消息失败时的处理
     *
     * @param subject NATS subject
     * @param msg     原始 NATS 消息
     * @param e       异常
     */
    void onConsumeError(String subject, Message msg, Exception e);

    /**
     * 判断是否应该重试
     *
     * @param e           异常
     * @param attempt     当前重试次数（从 1 开始）
     * @param maxAttempts 最大重试次数
     * @return true=应重试，false=不再重试
     */
    boolean shouldRetry(Exception e, int attempt, int maxAttempts);
}
