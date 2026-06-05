package com.richie.component.cache.subscriber;

import com.richie.component.cache.domain.UserInfo;
import com.richie.component.redis.streammq.stream.AbstractStreamConsumer;
import com.richie.component.redis.streammq.stream.EventContext;
import com.richie.component.redis.streammq.stream.RedisStreamConsumer;
import com.richie.component.cache.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.SocketTimeoutException;
import java.sql.SQLException;

/**
 * 用户信息 Redis Stream 消费者
 *
 * <p>订阅用户信息消息，处理用户相关的业务逻辑
 *
 * <p>功能特性：
 * <ul>
 *   <li>自动订阅 user-events Stream</li>
 *   <li>支持并发处理（默认 CPU 核心数的一半）</li>
 *   <li>自动确认消息</li>
 *   <li>错误重试机制</li>
 *   <li>完整的监控指标</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0.0
 * @since 2025-09-18
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RedisStreamConsumer("user-events")
public class UserStreamConsumer extends AbstractStreamConsumer<UserInfo> {

    private final UserService userService;

    /**
     * 处理用户信息消息
     * <p>
     * ACK机制有2种：
     * <ul>
     *   <li>1. 自动ACK：消息正确处理完成后自动确认，适合处理时间短、失败率低的场景，当处理失败时抛出异常则不会进行自动ACK，会调用下方的onError方法进行失败后的处理动作，注意onError为可选实现方法。</li>
     *   <li>2. 手动ACK：需要在handle方法内手动调用ctx.ack()进行确认，适合处理时间长、失败率高的场景，可以在处理完成后再确认消息，避免消息丢失。</li>
     * </ul>
     *
     * @param userInfo 用户信息
     * @param ctx      事件上下文
     * @throws Exception 处理异常
     */
    @Override
    protected void handle(UserInfo userInfo, EventContext ctx) throws Exception {

        // 模拟业务处理
        userService.processUserInfo(userInfo);

        // 记录处理成功
        log.info("用户信息处理完成: name={}, age={}", userInfo.getName(), userInfo.getAge());
    }

    /**
     * 错误处理方法
     *
     * <p>当消息处理发生异常时调用，用户决定如何处理错误
     * <p>包括：是否发送到死信队列、发送告警通知、更新数据库状态等
     *
     * @param e        异常对象
     * @param userInfo 发生错误的消息负载
     * @param ctx      事件上下文
     */
    @Override
    protected void onError(Throwable e, UserInfo userInfo, EventContext ctx) {
        log.error("处理用户信息消息时发生错误: name={}, age={}, error={}",
                userInfo.getName(), userInfo.getAge(), e.getMessage(), e);

        // 用户决定是否发送到死信队列
        boolean shouldSendToDeadLetter = shouldSendToDeadLetter(e, userInfo, ctx);

        if (shouldSendToDeadLetter) {
            // 发送到死信队列（使用默认策略）
            boolean success = sendToDeadLetterQueue(userInfo, e, ctx);
            if (success) {
                log.info("用户消息已发送到死信队列: name={}, age={}", userInfo.getName(), userInfo.getAge());
            } else {
                log.error("发送到死信队列失败: name={}, age={}", userInfo.getName(), userInfo.getAge());
            }
        } else {
            log.info("根据业务规则，不发送到死信队列: name={}, age={}", userInfo.getName(), userInfo.getAge());
        }

        // 其他错误处理逻辑：
        // 1. 发送告警通知
        // 2. 写入错误日志文件
        // 3. 更新数据库错误状态
        // 4. 发送邮件通知等

        // 示例：更新用户状态为异常
        try {
            userService.markUserAsError(userInfo, e.getMessage());
            log.info("用户状态已更新为异常: userId={}", userInfo.getName());
        } catch (Exception ex) {
            log.error("更新用户状态失败: userId={}, error={}", userInfo.getName(), ex.getMessage(), ex);
        }
    }

    /**
     * 判断是否应该发送到死信队列
     *
     * <p>用户可以根据业务规则决定是否发送到死信队列
     * <p>例如：根据异常类型、用户信息、重试次数等
     *
     * @param e        异常对象
     * @param userInfo 用户信息
     * @param ctx      事件上下文
     * @return 是否发送到死信队列
     */
    private boolean shouldSendToDeadLetter(Throwable e, UserInfo userInfo, EventContext ctx) {
        // 示例业务规则：使用增强的switch表达式
        return switch (e) {
            case IllegalArgumentException ex -> {
                log.warn("数据格式错误，不发送到死信队列: name={}, error={}", userInfo.getName(), ex.getMessage());
                yield false; // 数据格式错误，直接丢弃
            }
            case SocketTimeoutException ex -> {
                log.warn("网络超时，发送到死信队列: name={}, error={}", userInfo.getName(), ex.getMessage());
                yield true; // 网络超时，可以重试
            }
            case SQLException ex -> {
                log.warn("数据库错误，发送到死信队列: name={}, error={}", userInfo.getName(), ex.getMessage());
                yield true; // 数据库错误，可以重试
            }
            default -> {
                log.warn("未知错误，发送到死信队列: name={}, error={}", userInfo.getName(), e.getMessage());
                yield true; // 默认情况下，发送到死信队列
            }
        };
    }


}
