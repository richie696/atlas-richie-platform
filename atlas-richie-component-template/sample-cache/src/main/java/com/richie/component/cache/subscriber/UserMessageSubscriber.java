package com.richie.component.cache.subscriber;

import com.richie.component.cache.domain.UserInfo;
import com.richie.component.cache.redis.enums.TopicTypeEnum;
import com.richie.component.cache.redis.manage.MessageSubscriber;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.Nonnull;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class UserMessageSubscriber extends MessageSubscriber<UserInfo> {


    @Override
    protected Class<UserInfo> getValueType() {
        return UserInfo.class;
    }

    @Override
    protected void handleMessage(@Nonnull UserInfo userInfo, byte[] pattern) {
        log.info("Received user message: {}", userInfo);
    }

    @Override
    protected String getTopicName() {
        return "topic-user";
    }

    @Override
    protected TopicTypeEnum getTopicType() {
        return TopicTypeEnum.CHANNEL;
    }

}
