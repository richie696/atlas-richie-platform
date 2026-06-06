package com.richie.component.mongodb.listener;

import com.mongodb.event.ServerClosedEvent;
import com.mongodb.event.ServerDescriptionChangedEvent;
import com.mongodb.event.ServerOpeningEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class DefaultMongoServerListenerTest {

    private final DefaultMongoServerListener listener = new DefaultMongoServerListener();

    @Test
    void callbacks_shouldNotThrow() {
        assertThatCode(() -> listener.serverOpening(mock(ServerOpeningEvent.class)))
                .doesNotThrowAnyException();
        assertThatCode(() -> listener.serverClosed(mock(ServerClosedEvent.class)))
                .doesNotThrowAnyException();
        assertThatCode(() -> listener.serverDescriptionChanged(mock(ServerDescriptionChangedEvent.class)))
                .doesNotThrowAnyException();
    }
}
