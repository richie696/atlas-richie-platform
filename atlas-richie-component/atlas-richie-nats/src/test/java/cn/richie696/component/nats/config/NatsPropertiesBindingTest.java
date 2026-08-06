/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.richie696.component.nats.config;

import cn.richie696.component.nats.enums.AuthType;
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

    /**
     * 自定义属性绑定测试套件:通过 {@code @TestPropertySource} 注入完整自定义值,
     * 验证 Spring Boot ConfigurationProperties 绑定路径覆盖全字段,包括 JetStream
     * stream/consumer/backoff/nak-delay 等复合配置。
     */
    @SpringBootTest(classes = NatsPropertiesBindingTest.BindingConfig.class)
    @TestPropertySource(properties = {
            "platform.component.nats.server=nats://cluster-a:4222,nats://cluster-b:4222",
            "platform.component.nats.enabled=false",
            "platform.component.nats.connection.name=custom-client",
            "platform.component.nats.connection.connection-timeout=10s",
            "platform.component.nats.reconnect.max-reconnects=5",
            "platform.component.nats.reconnect.reconnect-wait=3s",
            "platform.component.nats.auth.type=TOKEN",
            "platform.component.nats.auth.token=my-secret",
            "platform.component.nats.tracing.enabled=false",
            "platform.component.nats.header-propagation.enabled=false",
            "platform.component.nats.idempotent.enabled=true",
            "platform.component.nats.idempotent.datasource=redis",
            "platform.component.nats.idempotent.ttl=60000",
            "platform.component.nats.jetstream.enabled=true",
            "platform.component.nats.jetstream.dlq.enabled=true",
            "platform.component.nats.jetstream.dlq.advisory-stream-name=CUSTOM_ADVISORY",
            "platform.component.nats.jetstream.streams[0].name=AGENT_TASKS",
            "platform.component.nats.jetstream.streams[0].consumers[0].name=agent-worker",
            "platform.component.nats.jetstream.streams[0].consumers[0].nak-delay=30s",
            "platform.component.nats.jetstream.streams[0].consumers[0].backoff[0]=1m",
            "platform.component.nats.jetstream.streams[0].consumers[0].backoff[1]=5m"
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
            assertThat(properties.getJetstream().getDlq().isEnabled()).isTrue();
            assertThat(properties.getJetstream().getDlq().getAdvisoryStreamName()).isEqualTo("CUSTOM_ADVISORY");
            var agentConsumer = properties.getJetstream().getStreams().getFirst().getConsumers().getFirst();
            assertThat(agentConsumer.getNakDelay()).isEqualTo(java.time.Duration.ofSeconds(30));
            assertThat(agentConsumer.getBackoff())
                    .containsExactly(java.time.Duration.ofMinutes(1), java.time.Duration.ofMinutes(5));
        }
    }

    /**
     * 默认值绑定测试套件:不注入任何自定义属性,
     * 验证 Spring 环境下 {@link NatsProperties} 默认值与代码构造默认值一致,
     * 即 {@code @ConfigurationProperties} 注册不会覆盖代码内 {@code this.xxx = ...} 默认值。
     */
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

    /**
     * 极简 Spring 配置:仅启用 {@link NatsProperties} 的 {@code @ConfigurationProperties} 绑定,
     * 故意不导入 {@code NatsAutoConfiguration},以验证属性绑定本身正确,
     * 又避免触发完整 NATS Bean 装配对测试环境的副作用。
     */
    @Configuration
    @EnableConfigurationProperties(NatsProperties.class)
    static class BindingConfig {
    }
}
