package com.richie.component.cache.ops.impl;

import com.richie.component.cache.function.NotificationFunction;
import com.richie.component.cache.ops.NotificationOps;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationOpsImpl implements NotificationOps {

    private final NotificationFunction fn;

    @Override
    public Long publish(String topic, Object message) {
        return fn.publishNotify(topic, message);
    }
}
