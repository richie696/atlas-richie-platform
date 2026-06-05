package com.richie.component.redis.streammq.stream;

import com.richie.component.redis.streammq.function.StreamFunction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.stream.RecordId;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class EventContextTest {

    @AfterEach
    void tearDown() {
        EventContext.setStreamFunction(null);
    }

    @Test
    void ack_delegatesToInjectedStreamFunction() {
        StreamFunction streamFunction = mock(StreamFunction.class);
        EventContext.setStreamFunction(streamFunction);
        RecordId recordId = RecordId.of("1700000000000-0");
        EventContext ctx = new EventContext("orders", "group-a", recordId);

        ctx.ack();

        verify(streamFunction).acknowledge("orders", "group-a", "1700000000000-0");
    }

    @Test
    void ack_throwsWhenStreamFunctionUnavailable() {
        EventContext ctx = new EventContext("orders", "group-a", RecordId.of("1-0"));

        assertThatThrownBy(ctx::ack)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("StreamFunction");
    }

}
