package com.richie.component.mongodb.listener;

import com.mongodb.event.ServerHeartbeatFailedEvent;
import com.mongodb.event.ServerHeartbeatStartedEvent;
import com.mongodb.event.ServerHeartbeatSucceededEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultMongoServerMonitorListenerTest {

    private final DefaultMongoServerMonitorListener listener = new DefaultMongoServerMonitorListener();

    @Test
    void heartbeatCallbacks_shouldNotThrow() {
        assertThatCode(() -> listener.serverHearbeatStarted(mock(ServerHeartbeatStartedEvent.class)))
                .doesNotThrowAnyException();
        assertThatCode(() -> listener.serverHeartbeatSucceeded(mock(ServerHeartbeatSucceededEvent.class)))
                .doesNotThrowAnyException();
        ServerHeartbeatFailedEvent failed = mock(ServerHeartbeatFailedEvent.class);
        when(failed.getThrowable()).thenReturn(new RuntimeException("heartbeat"));
        assertThatCode(() -> listener.serverHeartbeatFailed(failed))
                .doesNotThrowAnyException();
    }
}
