package com.richie.component.cache.subscriber;

import com.richie.component.cache.domain.GeneralInfo;
import com.richie.component.cache.redis.enums.TopicTypeEnum;
import com.richie.component.cache.redis.manage.MessageSubscriber;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.Nonnull;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class GeneralMessageSubscriber extends MessageSubscriber<GeneralInfo> {

    @Override
    protected Class<GeneralInfo> getValueType() {
        return GeneralInfo.class;
    }

    @Override
    protected void handleMessage(@Nonnull GeneralInfo generalInfo, byte[] pattern) {

    }

    @Override
    protected String getTopicName() {
        return "topic-order*";
    }

    @Override
    protected TopicTypeEnum getTopicType() {
        return TopicTypeEnum.PATTERN;
    }

}
