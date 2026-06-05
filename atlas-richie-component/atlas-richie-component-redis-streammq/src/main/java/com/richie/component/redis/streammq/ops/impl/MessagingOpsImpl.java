package com.richie.component.redis.streammq.ops.impl;

import com.richie.component.redis.streammq.function.NotificationFunction;
import com.richie.component.redis.streammq.ops.MessagingOps;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MessagingOpsImpl implements MessagingOps {

    private final NotificationFunction fn;

    @Override
    public Long publish(String topic, Object message) {
        return fn.publishNotify(topic, message);
    }
}
