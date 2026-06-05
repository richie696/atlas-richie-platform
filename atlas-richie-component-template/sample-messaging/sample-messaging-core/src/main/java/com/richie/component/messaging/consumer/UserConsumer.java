package com.richie.component.messaging.consumer;

import com.richie.component.cache.GlobalCache;
import com.richie.component.messaging.domain.UserInfo;
import com.richie.component.messaging.service.UserService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

import static com.richie.component.messaging.constants.TopicConstant.*;


/**
 * 用户消费者类
 *
 * @author richie696
 * @version 1.0
 * @since 2022-10-02 11:32:52
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserConsumer {

    private final BaseConsumer consumer;

    private final UserService userService;
    /**
     * 请存放于配置中心的配置文件或全局静态常量类中
     */
    private final int MAX_RETRY_NUM = 3;

    @PostConstruct
    public void initial() {
        // 普通消息队列
        consumer.registerConsumer(QUEUE_1, message -> {
            String value = userService.sayHello(new String(message.getContent()));
            log.info("普通消息：{}", value);
            if (StringUtils.isBlank(value)) {
                if (message.getRetryCount() < MAX_RETRY_NUM) {
                    log.warn("消息消费失败，进行第{}次重试，最大重试次数{}。", message.getRetryCount(), MAX_RETRY_NUM);
                    return false;
                } else {
                    log.error("错误，消费失败，压入死信队列或其他中间件，进行后续处理");
                    GlobalCache.struct().set("platform:dlq:%s".formatted(message.getMessageId()), message, TimeUnit.HOURS.toMillis(1));
                    return true;
                }
            }
            log.info("进行其他业务处理");
            return true;
        });

        // 延时消息队列
        consumer.registerConsumer(QUEUE_2, message -> {
            String msg = message.getBody(String.class);
            log.info("延迟消息：{}，延迟时间：{} 毫秒", msg, message.getDelayTime());
            return true;
        });

        // 延时消息队列
        consumer.registerConsumer(QUEUE_3, message -> {
            UserInfo userInfo = message.getBody(UserInfo.class);
            String value = userService.sayHello(userInfo);
            log.info("系统和你打了招呼：{}", value);
            return true;
        });
    }
}
