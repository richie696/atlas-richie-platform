package com.richie.component.messaging.service;

import com.richie.contract.model.ApiResult;
import jakarta.annotation.Nonnull;

/**
 * 消息队列演示服务接口
 *
 * @author richie696
 * @version 1.0
 * @since 2022-10-02 11:12:48
 */
public interface MessagingDemoService {

    /**
     * 发送消息
     *
     * @param topic   待发送消息的主题
     * @param content 消息内容
     * @return 返回发送结果
     */
    ApiResult<Void> doSend(@Nonnull String topic, @Nonnull String content);

    /**
     * 发送消息
     *
     * @param topic   待发送消息的主题
     * @param binder  绑定器名称
     * @param content 消息内容
     * @return 返回发送结果
     */
    ApiResult<Void> doSend(@Nonnull String topic, @Nonnull String binder, @Nonnull String content);

    /**
     * 发送消息
     *
     * @param topic     待发送消息的主题
     * @param content   消息内容
     * @param delayTime 延时时长（单位：毫秒）
     * @return 返回发送结果
     */
    ApiResult<Void> doDelaySend(@Nonnull String topic, @Nonnull String content, long delayTime);


}
