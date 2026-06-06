package com.richie.component.messaging.event;

import org.junit.jupiter.api.Test;

import tools.jackson.core.type.TypeReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessageEventTest {

    @Test
    void constructor_setsTopicAndMessageId() {
        MessageEvent event = new MessageEvent("orders", "hello");
        assertThat(event.getTopic()).isEqualTo("orders");
        assertThat(event.getMessageId()).isNotBlank();
        assertThat(event.getBody(String.class)).isEqualTo("hello");
    }

    @Test
    void frozenObject_rejectsMutation() {
        MessageEvent event = new MessageEvent("orders", "hello");
        event.setFrozen(true);

        assertThatThrownBy(() -> event.setTopic("other"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(event.isFrozen()).isTrue();
    }

    @Test
    void retryCount_incrementsViaReflection() throws Exception {
        MessageEvent event = new MessageEvent("orders", "hello");
        var method = MessageEvent.class.getDeclaredMethod("addRetryCount");
        method.setAccessible(true);
        method.invoke(event);
        method.invoke(event);
        assertThat(event.getRetryCount()).isEqualTo(2);
    }

    @Test
    void setters_updateFieldsWhenNotFrozen() {
        MessageEvent event = new MessageEvent();
        event.setTopic("t").setMessageId("mid").setSendTime(100L).setReceiveTime(200L).setDelay(true);
        event.setContentClassName("java.lang.String");
        event.setRetryCount(3);

        assertThat(event.getTopic()).isEqualTo("t");
        assertThat(event.getMessageId()).isEqualTo("mid");
        assertThat(event.getSendTime()).isEqualTo(100L);
        assertThat(event.getReceiveTime()).isEqualTo(200L);
        assertThat(event.isDelay()).isTrue();
        assertThat(event.getDelayTime()).isEqualTo(100L);
        assertThat(event.getContentClassName()).isEqualTo("java.lang.String");
        assertThat(event.getRetryCount()).isEqualTo(3);
    }

    @Test
    void getBody_supportsTypeReference() {
        MessageEvent event = new MessageEvent("orders", java.util.Map.of("k", "v"));
        assertThat(event.getBody(new TypeReference<java.util.Map<String, String>>() {}))
                .containsEntry("k", "v");
    }
}
