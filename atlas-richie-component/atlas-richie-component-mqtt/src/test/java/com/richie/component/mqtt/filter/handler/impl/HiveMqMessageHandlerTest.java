/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.mqtt.filter.handler.impl;

import com.richie.context.common.api.SpringContextHolder;
import com.richie.component.mqtt.config.MqttClientProperties;
import com.richie.component.mqtt.enums.DatasourceTypeEnum;
import com.richie.component.mqtt.filter.datasource.DatasourceHandler;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HiveMqMessageHandlerTest {

    @Mock
    private MqttClientProperties properties;

    @Mock
    private DatasourceHandler datasourceHandler;

    @InjectMocks
    private HiveMqMessageHandler handler;

    @Test
    void isDuplicate_nullPublish_returnsTrue() {
        assertThat(handler.isDuplicate(null)).isTrue();
    }

    @Test
    void isDuplicate_withPayload_checksDatasource() {
        when(properties.getDatasource()).thenReturn(DatasourceTypeEnum.MEMORY);
        try (var holder = mockStatic(SpringContextHolder.class)) {
            holder.when(() -> SpringContextHolder.getBean("mqttMemoryStoreHandler")).thenReturn(datasourceHandler);

            Mqtt5Publish publish = mock(Mqtt5Publish.class);
            when(publish.getPayloadAsBytes()).thenReturn("payload".getBytes());
            when(datasourceHandler.isDuplicate(anyString())).thenReturn(false);

            assertThat(handler.isDuplicate(publish)).isFalse();
        }
    }

    @Test
    void saveCache_withPayload_persistsHash() {
        when(properties.getDatasource()).thenReturn(DatasourceTypeEnum.MEMORY);
        try (var holder = mockStatic(SpringContextHolder.class)) {
            holder.when(() -> SpringContextHolder.getBean("mqttMemoryStoreHandler")).thenReturn(datasourceHandler);

            Mqtt5Publish publish = mock(Mqtt5Publish.class);
            when(publish.getPayloadAsBytes()).thenReturn("payload".getBytes());

            handler.saveCache(publish, 30_000L);

            verify(datasourceHandler).saveCache(anyString(), eq(30_000L));
        }
    }

    @Test
    void saveCache_nullPublish_isNoOp() {
        handler.saveCache(null, 1000L);
        verifyNoInteractions(properties);
    }

    @Test
    void isDuplicate_emptyPayload_returnsTrue() {
        Mqtt5Publish publish = mock(Mqtt5Publish.class);
        when(publish.getPayloadAsBytes()).thenReturn(new byte[0]);
        assertThat(handler.isDuplicate(publish)).isTrue();
    }

    @Test
    void isDuplicate_whenHashFails_returnsTrue() {
        Mqtt5Publish publish = mock(Mqtt5Publish.class);
        when(publish.getPayloadAsBytes()).thenThrow(new RuntimeException("boom"));
        assertThat(handler.isDuplicate(publish)).isTrue();
    }

    @Test
    void isDuplicate_redisDatasource_usesRedisHandler() {
        when(properties.getDatasource()).thenReturn(DatasourceTypeEnum.REDIS);
        try (var holder = mockStatic(SpringContextHolder.class)) {
            holder.when(() -> SpringContextHolder.getBean("mqttRedisStoreHandler")).thenReturn(datasourceHandler);

            Mqtt5Publish publish = mock(Mqtt5Publish.class);
            when(publish.getPayloadAsBytes()).thenReturn("redis-payload".getBytes());
            when(datasourceHandler.isDuplicate(anyString())).thenReturn(true);

            assertThat(handler.isDuplicate(publish)).isTrue();
        }
    }

    @Test
    void saveCache_emptyPayload_isNoOp() {
        Mqtt5Publish publish = mock(Mqtt5Publish.class);
        when(publish.getPayloadAsBytes()).thenReturn(new byte[0]);
        handler.saveCache(publish, 1000L);
        verifyNoInteractions(properties);
    }

    @Test
    void saveCache_whenHashFails_isNoOp() {
        Mqtt5Publish publish = mock(Mqtt5Publish.class);
        when(publish.getPayloadAsBytes()).thenThrow(new RuntimeException("boom"));
        handler.saveCache(publish, 1000L);
        verifyNoInteractions(properties);
    }
}
