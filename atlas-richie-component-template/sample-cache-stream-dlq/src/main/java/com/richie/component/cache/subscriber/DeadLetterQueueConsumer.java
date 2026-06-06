package com.richie.component.cache.subscriber;

import com.richie.component.redis.streammq.bean.DeadLetterMessage;
import com.richie.component.redis.streammq.stream.AbstractStreamConsumer;
import com.richie.component.redis.streammq.stream.EventContext;
import com.richie.component.redis.streammq.stream.RedisStreamConsumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 死信队列消费者
 *
 * <p>专门处理死信消息的独立消费者，建议部署在独立的服务中
 *
 * <p>职责：
 * <ul>
 *   <li>接收和处理死信消息</li>
 *   <li>记录死信日志和监控指标</li>
 *   <li>发送告警通知</li>
 *   <li>尝试重新处理或存储用于后续分析</li>
 * </ul>
 *
 * @author richie696
 * @since 2025-09-18
 */
@Slf4j
@Component
@RedisStreamConsumer("dlq-global")
public class DeadLetterQueueConsumer extends AbstractStreamConsumer<DeadLetterMessage> {

    @Override
    protected void handle(DeadLetterMessage deadLetterMessage, EventContext ctx) {
        log.warn("收到死信消息: originalType={}, errorType={}, businessId={}, retryCount={}",
                deadLetterMessage.originalMessageType(),
                deadLetterMessage.errorType(),
                deadLetterMessage.businessId(),
                deadLetterMessage.retryCount());

        try {
            // 处理死信消息
            processDeadLetter(deadLetterMessage);

            // 记录处理成功
            log.info("死信消息处理完成: businessId={}", deadLetterMessage.businessId());

        } catch (Exception e) {
            log.error("死信消息处理失败: businessId={}, error={}",
                    deadLetterMessage.businessId(), e.getMessage(), e);
            // 死信处理失败时，可以选择重新抛出异常让消息重新入队
            // 或者记录到数据库用于人工处理
        }
    }

    /**
     * 处理死信消息的具体逻辑
     *
     * @param message 死信消息
     */
    private void processDeadLetter(DeadLetterMessage message) {
        // 1. 记录详细的死信日志
        logDeadLetterDetails(message);

        // 2. 发送告警通知（如果配置了告警）
        sendAlertIfNeeded(message);

        // 3. 存储到数据库用于后续分析
        saveToDatabase(message);

        // 4. 根据业务规则决定是否尝试重新处理
        if (shouldRetry(message)) {
            attemptRetry(message);
        }
    }

    /**
     * 记录死信详细信息
     */
    private void logDeadLetterDetails(DeadLetterMessage message) {
        log.warn("死信消息详情: " +
                        "originalMessageType={}, " +
                        "originalStreamKey={}, " +
                        "originalGroup={}, " +
                        "originalRecordId={}, " +
                        "errorMessage={}, " +
                        "errorType={}, " +
                        "sourceConsumer={}, " +
                        "retryCount={}, " +
                        "businessId={}, " +
                        "priority={}, " +
                        "timestamp={}",
                message.originalMessageType(),
                message.originalStreamKey(),
                message.originalGroup(),
                message.originalRecordId(),
                message.errorMessage(),
                message.errorType(),
                message.sourceConsumer(),
                message.retryCount(),
                message.businessId(),
                message.priority(),
                message.timestamp());
    }

    /**
     * 发送告警通知
     */
    private void sendAlertIfNeeded(DeadLetterMessage message) {
        // 根据错误类型和业务ID决定是否发送告警
        if (isCriticalError(message)) {
            // 发送紧急告警
            log.error("发送紧急告警: businessId={}, errorType={}",
                    message.businessId(), message.errorType());
            // 这里可以集成钉钉、邮件、短信等告警渠道
        }
    }

    /**
     * 保存到数据库
     */
    private void saveToDatabase(DeadLetterMessage message) {
        // 保存死信消息到数据库，用于后续分析和人工处理
        log.debug("保存死信消息到数据库: businessId={}", message.businessId());
        // 这里可以调用数据库服务保存死信记录
    }

    /**
     * 判断是否应该重试
     */
    private boolean shouldRetry(DeadLetterMessage message) {
        // 根据重试次数、错误类型、业务规则等决定是否重试
        return message.retryCount() < 3 &&
                !isPermanentError(message) &&
                isRetryableBusiness(message);
    }

    /**
     * 尝试重新处理
     */
    private void attemptRetry(DeadLetterMessage message) {
        log.info("尝试重新处理死信消息: businessId={}, retryCount={}",
                message.businessId(), message.retryCount());

        // 这里可以实现重新处理的逻辑
        // 例如：重新发送到原始队列，或者调用原始业务服务
    }

    /**
     * 判断是否为严重错误
     */
    private boolean isCriticalError(DeadLetterMessage message) {
        return message.errorType().contains("Exception") ||
                message.priority() != null && message.priority().equals("HIGH");
    }

    /**
     * 判断是否为永久性错误（不可重试）
     */
    private boolean isPermanentError(DeadLetterMessage message) {
        return message.errorType().contains("IllegalArgumentException") ||
                message.errorType().contains("ValidationException");
    }

    /**
     * 判断业务是否可重试
     */
    private boolean isRetryableBusiness(DeadLetterMessage message) {
        // 根据业务ID或消息类型判断是否可重试
        return message.businessId() != null && !message.businessId().isEmpty();
    }
}
