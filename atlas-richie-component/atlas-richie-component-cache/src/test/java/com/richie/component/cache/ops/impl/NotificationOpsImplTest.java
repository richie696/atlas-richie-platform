package com.richie.component.cache.ops.impl;

import com.richie.component.cache.function.NotificationFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationOpsImplTest {

    @Mock
    private NotificationFunction fn;

    @InjectMocks
    private NotificationOpsImpl ops;

    @Test
    void publish_delegates() {
        when(fn.publishNotify("topic", "msg")).thenReturn(3L);
        assertThat(ops.publish("topic", "msg")).isEqualTo(3L);
    }
}
