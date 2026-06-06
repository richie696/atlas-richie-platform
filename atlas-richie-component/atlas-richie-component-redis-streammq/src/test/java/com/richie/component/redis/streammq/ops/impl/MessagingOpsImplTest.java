package com.richie.component.redis.streammq.ops.impl;

import com.richie.component.redis.streammq.function.NotificationFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessagingOpsImplTest {

    @Mock
    private NotificationFunction notificationFunction;

    @InjectMocks
    private MessagingOpsImpl messagingOps;

    @Test
    void publish_shouldDelegateToNotificationFunction() {
        when(notificationFunction.publishNotify("it:topic", "hello")).thenReturn(2L);

        Long receivers = messagingOps.publish("it:topic", "hello");

        assertThat(receivers).isEqualTo(2L);
        verify(notificationFunction).publishNotify("it:topic", "hello");
    }
}
