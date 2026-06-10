package com.richie.component.messaging.filter.handler.impl;

import com.richie.component.messaging.enums.DatasourceTypeEnum;
import com.richie.component.messaging.event.MessageEvent;
import com.richie.component.messaging.filter.datasource.impl.MemoryDatasourceHandlerImpl;
import com.richie.component.messaging.config.MessagingProperties;
import com.richie.context.common.api.SpringContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageHandlerServiceImplTest {

    @Mock
    private MessagingProperties properties;
    @Mock
    private ApplicationContext applicationContext;

    @InjectMocks
    private MessageHandlerServiceImpl handlerService;

    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(SpringContextHolder.class, "applicationContext", null);
    }

    @Test
    void saveCache_delegatesToConfiguredHandler() {
        MemoryDatasourceHandlerImpl memoryHandler = new MemoryDatasourceHandlerImpl();
        ReflectionTestUtils.setField(SpringContextHolder.class, "applicationContext", applicationContext);
        when(properties.getDatasource()).thenReturn(DatasourceTypeEnum.MEMORY);
        when(applicationContext.getBean(DatasourceTypeEnum.MEMORY.getServiceName())).thenReturn(memoryHandler);

        Message<MessageEvent> message = MessageBuilder.withPayload(new MessageEvent("orders", "body")).build();
        assertThat(handlerService.saveCache(message, 30_000L)).isTrue();
        assertThat(handlerService.saveCache(message, 30_000L)).isFalse();
    }

    @Test
    void clearCache_delegatesToConfiguredHandler() {
        MemoryDatasourceHandlerImpl memoryHandler = new MemoryDatasourceHandlerImpl();
        ReflectionTestUtils.setField(SpringContextHolder.class, "applicationContext", applicationContext);
        when(properties.getDatasource()).thenReturn(DatasourceTypeEnum.MEMORY);
        when(applicationContext.getBean(DatasourceTypeEnum.MEMORY.getServiceName())).thenReturn(memoryHandler);

        Message<MessageEvent> message = MessageBuilder.withPayload(new MessageEvent("orders", "body")).build();
        handlerService.saveCache(message, 30_000L);
        handlerService.clearCache(message);

        assertThat(memoryHandler.isDuplicate(message)).isFalse();
    }
}
