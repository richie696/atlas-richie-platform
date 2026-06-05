package com.richie.component.cache.subscriber;

import com.richie.component.cache.domain.OrderInfo;
import com.richie.component.redis.streammq.stream.AbstractStreamConsumer;
import com.richie.component.redis.streammq.stream.EventContext;
import com.richie.component.redis.streammq.stream.RedisStreamConsumer;
import com.richie.component.redis.streammq.utils.DeadLetterQueueUtil;
import com.richie.component.cache.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 订单信息 Redis Stream 消费者
 *
 * <p>演示如何自定义死信队列策略
 *
 * @author richie696
 * @since 2025-12-09
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RedisStreamConsumer("order-events")
public class OrderStreamConsumer extends AbstractStreamConsumer<OrderInfo> {

    private final OrderService orderService;

    /**
     * 处理订单信息消息
     */
    @Override
    protected void handle(OrderInfo orderInfo, EventContext ctx) throws Exception {
        log.info("处理订单信息: orderId={}, userId={}", orderInfo.getOrderId(), orderInfo.getUserId());

        // 模拟业务处理
        orderService.processOrder(orderInfo);

        // 手动确认消息
        ctx.ack();
    }

    /**
     * 错误处理方法
     *
     * <p>当消息处理发生异常时调用，用户决定如何处理错误
     * <p>包括：是否发送到死信队列、使用什么策略、发送告警通知等
     *
     * @param e         异常对象
     * @param orderInfo 发生错误的消息负载
     * @param ctx       事件上下文
     */
    @Override
    protected void onError(Throwable e, OrderInfo orderInfo, EventContext ctx) {
        log.error("处理订单消息时发生错误: orderId={}, userId={}, error={}",
                orderInfo.getOrderId(), orderInfo.getUserId(), e.getMessage(), e);

        // 用户决定是否发送到死信队列
        boolean shouldSendToDeadLetter = orderService.shouldSendToDeadLetter(e, orderInfo, ctx);

        if (shouldSendToDeadLetter) {
            // 根据业务规则选择死信队列策略
            DeadLetterQueueUtil.DeadLetterStrategy strategy = selectDeadLetterStrategy(orderInfo);

            // 发送到死信队列（使用指定策略）
            boolean success = sendToDeadLetterQueue(orderInfo, e, ctx, strategy);
            if (success) {
                log.info("订单消息已发送到死信队列: orderId={}, amount={}, strategy={}",
                        orderInfo.getOrderId(), orderInfo.getAmount(), strategy);
            } else {
                log.error("发送到死信队列失败: orderId={}, amount={}",
                        orderInfo.getOrderId(), orderInfo.getAmount());
            }
        } else {
            log.info("根据业务规则，不发送到死信队列: orderId={}, amount={}",
                    orderInfo.getOrderId(), orderInfo.getAmount());
        }

        // 其他错误处理逻辑：
        // 1. 更新订单状态为处理失败
        // 2. 发送告警通知
        // 3. 记录到业务日志
        // 4. 通知相关业务人员等

        // 示例：记录订单处理失败
        log.warn("订单处理失败，需要人工干预: orderId={}, userId={}, error={}",
                orderInfo.getOrderId(), orderInfo.getUserId(), e.getMessage());
    }

    /**
     * 选择死信队列策略
     *
     * <p>根据订单信息选择不同的死信队列策略
     *
     * @param orderInfo 订单信息
     * @return 死信队列策略
     */
    private DeadLetterQueueUtil.DeadLetterStrategy selectDeadLetterStrategy(OrderInfo orderInfo) {
        // 根据订单金额选择不同的死信队列策略
        if (orderInfo.getAmount() != null && orderInfo.getAmount().compareTo(BigDecimal.valueOf(1000)) > 0) {
            // 高金额订单使用按源队列分组策略（便于重点监控）
            return DeadLetterQueueUtil.DeadLetterStrategy.BY_SOURCE_STREAM;
        } else if (orderInfo.getAmount() != null && orderInfo.getAmount().compareTo(BigDecimal.valueOf(100)) > 0) {
            // 中等金额订单使用按消息类型分组策略
            return DeadLetterQueueUtil.DeadLetterStrategy.BY_MESSAGE_TYPE;
        } else {
            // 低金额订单使用全局死信队列策略
            return DeadLetterQueueUtil.DeadLetterStrategy.GLOBAL;
        }
    }

}
