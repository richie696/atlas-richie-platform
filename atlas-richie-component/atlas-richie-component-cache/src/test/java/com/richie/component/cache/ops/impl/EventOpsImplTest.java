package com.richie.component.cache.ops.impl;

import com.richie.component.cache.function.EventFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.MessageListener;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EventOpsImplTest {

    @Mock
    private EventFunction fn;

    @Mock
    private MessageListener listener;

    @InjectMocks
    private EventOpsImpl ops;

    @Test
    void subscribeKeyEvent_delegates() {
        ops.subscribeKeyEvent("__keyevent@0__:expired", listener);
        verify(fn).subscribeKeyEvent("__keyevent@0__:expired", listener);
    }
}
