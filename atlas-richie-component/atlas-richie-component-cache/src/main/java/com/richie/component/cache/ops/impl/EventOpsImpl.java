package com.richie.component.cache.ops.impl;

import com.richie.component.cache.function.EventFunction;
import com.richie.component.cache.ops.EventOps;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EventOpsImpl implements EventOps {

    private final EventFunction fn;

    @Override
    public void subscribeKeyEvent(String pattern, MessageListener listener) {
        fn.subscribeKeyEvent(pattern, listener);
    }
}
