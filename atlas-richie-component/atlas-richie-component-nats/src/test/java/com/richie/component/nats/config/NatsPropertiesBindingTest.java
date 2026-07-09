/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.nats.config;

import com.richie.component.nats.enums.AuthType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link NatsProperties} Spring Boot ConfigurationProperties 绑定测试。
 *
 * <p>仅启用 {@link NatsProperties} 绑定，不触发 {@code NatsAutoConfiguration} 全量装配，
 * 因此可在不依赖 NATS 服务器的情况下验证属性绑定。</p>
 */
class NatsPropertiesBindingTest {

    @SpringBootTest(classes = NatsPropertiesBindingTest.BindingConfig.class)
    @TestPropertySource(properties = {
            "platform.nats.server=nats://cluster-a:4222,nats://cluster-b:4222",
            "platform.nats.enabled=false",
            "platform.nats.connection.name=custom-client",
            "platform.nats.connection.connection-timeout=10s",
            "platform.nats.reconnect.max-reconnects=5",
            "platform.nats.reconnect.reconnect-wait=3s",
            "platform.nats.auth.type=TOKEN",
            "platform.nats.auth.token=my-secret",
            "platform.nats.tracing.enabled=false",
            "platform.nats.header-propagation.enabled=false",
            "platform.nats.idempotent.enabled=true",
            "platform.nats.idempotent.datasource=redis",
            "platform.nats.idempotent.ttl=60000",
            "platform.nats.jetstream.enabled=true"
    })
    static class Binding {

        @Autowired
        private NatsProperties properties;

        @Test
        void shouldBindCustomProperties() {
            assertThat(properties.getServer())
                    .isEqualTo("nats://cluster-a:4222,nats://cluster-b:4222");
            assertThat(properties.isEnabled()).isFalse();
            assertThat(properties.getConnection().getName()).isEqualTo("custom-client");
            assertThat(properties.getConnection().getConnectionTimeout())
                    .isEqualTo(java.time.Duration.ofSeconds(10));
            assertThat(properties.getReconnect().getMaxReconnects()).isEqualTo(5);
            assertThat(properties.getReconnect().getReconnectWait())
                    .isEqualTo(java.time.Duration.ofSeconds(3));
            assertThat(properties.getAuth().getType()).isEqualTo(AuthType.TOKEN);
            assertThat(properties.getAuth().getToken()).isEqualTo("my-secret");
            assertThat(properties.getTracing().isEnabled()).isFalse();
            assertThat(properties.getHeaderPropagation().isEnabled()).isFalse();
            assertThat(properties.getIdempotent().isEnabled()).isTrue();
            assertThat(properties.getIdempotent().getDatasource()).isEqualTo("redis");
            assertThat(properties.getIdempotent().getTtl()).isEqualTo(60_000L);
            assertThat(properties.getJetstream().isEnabled()).isTrue();
        }
    }

    @SpringBootTest(classes = NatsPropertiesBindingTest.BindingConfig.class)
    static class DefaultsBinding {

        @Autowired
        private NatsProperties properties;

        @Test
        void shouldExposeSensibleDefaults() {
            // 不设置任何属性，验证 Spring 环境下默认值与代码构造的默认值一致
            assertThat(properties.isEnabled()).isTrue();
            assertThat(properties.getServer()).isEqualTo("nats://localhost:4222");
            assertThat(properties.getConnection().getName()).isEqualTo("nats-client");
            assertThat(properties.getReconnect().getMaxReconnects()).isEqualTo(-1);
        }
    }

    @Configuration
    @EnableConfigurationProperties(NatsProperties.class)
    static class BindingConfig {
    }
}
