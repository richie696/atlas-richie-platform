package com.richie.component.messaging.service.impl;

import com.richie.context.common.api.HeaderContextHolder;
import com.richie.contract.constant.GlobalConstants;
import com.richie.component.messaging.event.MessageEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageServiceImplTest {

    @Mock
    private StreamBridge streamBridge;

    @InjectMocks
    private MessageServiceImpl messageService;

    @Captor
    private ArgumentCaptor<Message<MessageEvent>> messageCaptor;

    @AfterEach
    void clearHeaders() {
        HeaderContextHolder.removeContext();
    }

    @Test
    void sendMessage_publishesToStreamBridge() {
        when(streamBridge.send(eq("orders"), isNull(), any(Message.class), any())).thenReturn(true);

        boolean result = messageService.sendMessage("orders", "payload");

        assertThat(result).isTrue();
        verify(streamBridge).send(eq("orders"), isNull(), messageCaptor.capture(), any());
        assertThat(messageCaptor.getValue().getPayload().getTopic()).isEqualTo("orders");
        assertThat(messageCaptor.getValue().getPayload().getBody(String.class)).isEqualTo("payload");
    }

    @Test
    void sendDelayMessage_propagatesAllContextHeaders() {
        HeaderContextHolder.setHeader(GlobalConstants.X_TIME_FORMAT_PATTERN, "yyyy-MM-dd");
        HeaderContextHolder.setHeader(GlobalConstants.X_CURRENCY_FORMAT_PATTERN, "#,##0.00");
        HeaderContextHolder.setHeader(GlobalConstants.X_RD_REQUEST_TIMEZONE, "Asia/Shanghai");
        HeaderContextHolder.setHeader(GlobalConstants.X_RD_REQUEST_LANGUAGE, "zh-CN");
        HeaderContextHolder.setHeader(GlobalConstants.X_RD_REQUEST_SHOP_CODE, "shop-9");
        when(streamBridge.send(eq("orders"), isNull(), any(Message.class), any())).thenReturn(true);

        messageService.sendDelayMessage("orders", "payload", 0L);

        verify(streamBridge).send(eq("orders"), isNull(), messageCaptor.capture(), any());
        Message<MessageEvent> message = messageCaptor.getValue();
        assertThat(message.getHeaders().get(GlobalConstants.X_TIME_FORMAT_PATTERN)).isEqualTo("yyyy-MM-dd");
        assertThat(message.getHeaders().get(GlobalConstants.X_CURRENCY_FORMAT_PATTERN)).isEqualTo("#,##0.00");
        assertThat(message.getHeaders().get(GlobalConstants.X_RD_REQUEST_TIMEZONE)).isEqualTo("Asia/Shanghai");
        assertThat(message.getHeaders().get(GlobalConstants.X_RD_REQUEST_LANGUAGE)).isEqualTo("zh-CN");
        assertThat(message.getHeaders().get(GlobalConstants.X_RD_REQUEST_SHOP_CODE)).isEqualTo("shop-9");
    }

    @Test
    void sendDelayMessage_includesHeadersAndDelayFlag() {
        HeaderContextHolder.setHeader(GlobalConstants.X_TENANT_ID, "tenant-a");
        HeaderContextHolder.setHeader(GlobalConstants.X_CANARY_ID, "shop-1");
        when(streamBridge.send(eq("orders"), eq("kafka"), any(Message.class), eq(MimeTypeUtils.APPLICATION_JSON)))
                .thenReturn(true);

        boolean result = messageService.sendDelayMessage(
                "orders", "kafka", "payload", MimeTypeUtils.APPLICATION_JSON, 500L);

        assertThat(result).isTrue();
        verify(streamBridge).send(eq("orders"), eq("kafka"), messageCaptor.capture(), eq(MimeTypeUtils.APPLICATION_JSON));
        Message<MessageEvent> message = messageCaptor.getValue();
        assertThat(message.getHeaders().get(GlobalConstants.X_TENANT_ID)).isEqualTo("tenant-a");
        assertThat(message.getHeaders().get(GlobalConstants.X_CANARY_ID)).isEqualTo("shop-1");
        assertThat(message.getHeaders().get("x-delay")).isEqualTo(500L);
        assertThat(message.getPayload().isDelay()).isTrue();
    }

    @Test
    void sendMessage_withBinderName_delegates() {
        when(streamBridge.send(eq("orders"), eq("kafka"), any(Message.class), any())).thenReturn(true);
        assertThat(messageService.sendMessage("orders", "kafka", (Object) "payload")).isTrue();
    }

    @Test
    void sendMessage_withMimeType_delegates() {
        when(streamBridge.send(eq("orders"), isNull(), any(Message.class), eq(MimeTypeUtils.APPLICATION_JSON)))
                .thenReturn(true);
        assertThat(messageService.sendMessage("orders", (Object) "payload", MimeTypeUtils.APPLICATION_JSON)).isTrue();
    }

    @Test
    void sendDelayMessage_withoutBinder_delegates() {
        when(streamBridge.send(eq("orders"), isNull(), any(Message.class), any())).thenReturn(true);
        assertThat(messageService.sendDelayMessage("orders", "payload", 100L)).isTrue();
    }

    @Test
    void sendDelayMessage_withBinderAndMimeType_delegates() {
        when(streamBridge.send(eq("orders"), eq("kafka"), any(Message.class), eq(MimeTypeUtils.APPLICATION_JSON)))
                .thenReturn(true);
        assertThat(messageService.sendDelayMessage(
                "orders", "kafka", "payload", MimeTypeUtils.APPLICATION_JSON, 200L)).isTrue();
    }

    @Test
    void rePublishMessage_returnsFalseWhenStreamBridgeFails() {
        MessageEvent event = new MessageEvent("orders", "payload");
        when(streamBridge.send(eq("orders"), isNull(), eq(event), any())).thenReturn(false);
        assertThat(messageService.rePublishMessage(event)).isFalse();
    }

    @Test
    void rePublishMessage_resetsFrozenAndIncrementsRetry() {
        MessageEvent event = new MessageEvent("orders", "payload");
        event.setFrozen(true);
        when(streamBridge.send(eq("orders"), isNull(), eq(event), any())).thenReturn(true);

        assertThat(messageService.rePublishMessage(event)).isTrue();
        assertThat(event.isFrozen()).isFalse();
        assertThat(event.getRetryCount()).isEqualTo(1);
    }
}
