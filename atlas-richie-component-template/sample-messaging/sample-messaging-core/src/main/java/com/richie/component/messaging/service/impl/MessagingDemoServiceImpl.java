package com.richie.component.messaging.service.impl;

import com.richie.contract.model.ApiResult;
import com.richie.component.messaging.service.MessageService;
import com.richie.component.messaging.service.MessagingDemoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.Nonnull;
import org.springframework.stereotype.Service;

/**
 * 消息队列演示服务接口实现类
 *
 * @author richie696
 * @version 1.0
 * @since 2022-10-02 11:12:48
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessagingDemoServiceImpl implements MessagingDemoService {

    private final MessageService messageService;

    @Override
    public ApiResult<Void> doSend(@Nonnull String topic, @Nonnull String content) {
        boolean result = messageService.sendMessage(topic, content);
        if (result) {
            return ApiResult.success("发送成功", null);
        }
        return ApiResult.error("发送失败。");
    }

    @Override
    public ApiResult<Void> doSend(@Nonnull String topic, @Nonnull String binder, @Nonnull String content) {
        boolean result = messageService.sendMessage(topic, binder, content);
        if (result) {
            return ApiResult.success("发送成功", null);
        }
        return ApiResult.error("发送失败。");
    }

    @Override
    public ApiResult<Void> doDelaySend(@Nonnull String topic, @Nonnull String content, long delayTime) {
        boolean result = messageService.sendMessage(topic, content, delayTime);
        if (result) {
            return ApiResult.success("发送成功", null);
        }
        return ApiResult.error("发送失败。");
    }
}
