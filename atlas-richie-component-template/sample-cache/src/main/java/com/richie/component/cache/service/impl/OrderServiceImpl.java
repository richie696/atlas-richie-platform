package com.richie.component.cache.service.impl;

import com.richie.component.cache.domain.OrderInfo;
import com.richie.component.cache.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.sql.SQLException;

/**
 * 订单服务实现类
 *
 * @author richie696
 * @version 1.0
 * @since 2025-09-18 10:41:26
 */
@Slf4j
@Service
public class OrderServiceImpl implements OrderService {

    @Override
    public void processOrder(OrderInfo orderInfo) throws Exception {
        // 模拟业务处理
        if (orderInfo.getOrderId() == null || orderInfo.getOrderId() <= 0L) {
            throw new IllegalArgumentException("订单ID不能为空");
        }

        if (orderInfo.getAmount() != null && orderInfo.getAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("订单金额不能为负数");
        }

        // 模拟处理时间
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void markOrderAsError(OrderInfo orderInfo, String message) {
        log.error("标记订单为错误状态: {}, 原因: {}", orderInfo.getOrderId(), message);
        // 这里可以添加实际的错误标记逻辑，比如更新数据库状态等
    }

    @Override
    public boolean shouldSendToDeadLetter(Throwable e, OrderInfo orderInfo, Object ctx) {
        // 先检查业务规则：订单金额为负数，不发送到死信队列（直接丢弃）
        if (orderInfo.getAmount() != null && orderInfo.getAmount().compareTo(BigDecimal.ZERO) < 0) {
            log.warn("订单金额为负数，不发送到死信队列: orderId={}, amount={}",
                    orderInfo.getOrderId(), orderInfo.getAmount());
            return false;
        }

        // 使用增强的switch表达式处理异常类型
        return switch (e) {
            case IllegalArgumentException ex -> {
                log.warn("订单数据格式错误，不发送到死信队列: orderId={}, error={}",
                        orderInfo.getOrderId(), ex.getMessage());
                yield false; // 数据格式错误，直接丢弃
            }
            case SocketTimeoutException ex -> {
                log.warn("网络超时，发送到死信队列: orderId={}, error={}",
                        orderInfo.getOrderId(), ex.getMessage());
                yield true; // 网络超时，可以重试
            }
            case SQLException ex -> {
                log.warn("数据库错误，发送到死信队列: orderId={}, error={}",
                        orderInfo.getOrderId(), ex.getMessage());
                yield true; // 数据库错误，可以重试
            }
            default -> {
                log.warn("未知错误，发送到死信队列: orderId={}, error={}",
                        orderInfo.getOrderId(), e.getMessage());
                yield true; // 默认情况下，发送到死信队列
            }
        };
    }
}
