package com.richie.component.cache.service;

import com.richie.component.cache.domain.OrderInfo;

/**
 * 订单服务接口
 *
 * @author richie696
 * @version 1.0
 * @since 2025-09-18 10:40:58
 */
public interface OrderService {

    /**
     * 处理订单的业务逻辑
     *
     * @param orderInfo 订单信息
     * @throws Exception 当处理失败时抛出异常，阻止消息被确认
     */
    void processOrder(OrderInfo orderInfo) throws Exception;

    /**
     * 将订单标记为错误状态
     *
     * @param orderInfo 订单信息
     * @param message   错误消息
     */
    void markOrderAsError(OrderInfo orderInfo, String message);

    /**
     * 判断是否应该发送到死信队列
     *
     * <p>用户可以根据业务规则决定是否发送到死信队列
     * <p>例如：根据异常类型、订单金额、重试次数等
     *
     * @param e         异常对象
     * @param orderInfo 订单信息
     * @param ctx       事件上下文
     * @return 是否发送到死信队列
     */
    boolean shouldSendToDeadLetter(Throwable e, OrderInfo orderInfo, Object ctx);
}
