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
package com.richie.component.nats.config;

import com.richie.component.nats.enums.AuthType;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link NatsProperties} 单元测试。
 *
 * <p>验证 ConfigurationProperties 绑定：默认值、自定义 property 注入、内部配置类的级联绑定。</p>
 */
class NatsPropertiesTest {

    @Test
    void defaults_shouldHaveSensibleOutOfBoxValues() {
        NatsProperties props = new NatsProperties();

        // ===== 顶层默认值 =====
        assertThat(props.isEnabled()).isTrue();
        assertThat(props.getServer()).isEqualTo("nats://localhost:4222");

        // ===== Connection =====
        assertThat(props.getConnection().getName()).isEqualTo("nats-client");
        assertThat(props.getConnection().getConnectionTimeout()).isEqualTo(java.time.Duration.ofSeconds(5));
        assertThat(props.getConnection().getDrainTimeout()).isEqualTo(java.time.Duration.ofSeconds(30));
        assertThat(props.getConnection().isNoEcho()).isFalse();
        assertThat(props.getConnection().isNoRandomize()).isFalse();
        assertThat(props.getConnection().getInboxPrefix()).isEqualTo("_INBOX");
        assertThat(props.getConnection().isSupportUtf8Subjects()).isFalse();

        // ===== Reconnect =====
        assertThat(props.getReconnect().isEnabled()).isTrue();
        assertThat(props.getReconnect().getMaxReconnects()).isEqualTo(-1);
        assertThat(props.getReconnect().getReconnectWait()).isEqualTo(java.time.Duration.ofSeconds(2));
        assertThat(props.getReconnect().getJitter()).isEqualTo(java.time.Duration.ofMillis(100));
        assertThat(props.getReconnect().getJitterTls()).isEqualTo(java.time.Duration.ofSeconds(1));
        assertThat(props.getReconnect().getBufferSize()).isEqualTo(8_388_608L);
        assertThat(props.getReconnect().isRetryOnFailedConnect()).isFalse();

        // ===== Ping =====
        assertThat(props.getPing().getInterval()).isEqualTo(java.time.Duration.ofSeconds(20));
        assertThat(props.getPing().getMaxOutstanding()).isEqualTo(2);

        // ===== Auth =====
        assertThat(props.getAuth().getType()).isEqualTo(AuthType.NONE);

        // ===== Protocol =====
        assertThat(props.getProtocol().isVerbose()).isFalse();
        assertThat(props.getProtocol().isPedantic()).isFalse();
        assertThat(props.getProtocol().isNoHeaders()).isFalse();
        assertThat(props.getProtocol().isNoResponders()).isFalse();
        assertThat(props.getProtocol().getMaxControlLine()).isEqualTo(4096);

        // ===== Request =====
        assertThat(props.getRequest().isOldStyle()).isFalse();
        assertThat(props.getRequest().getCleanupInterval()).isEqualTo(java.time.Duration.ofSeconds(5));
        assertThat(props.getRequest().getDefaultTimeout()).isEqualTo(java.time.Duration.ofSeconds(5));

        // ===== Queue =====
        assertThat(props.getQueue().getMaxOutgoingMessages()).isEqualTo(-1);
        assertThat(props.getQueue().isDiscardWhenFull()).isFalse();

        // ===== TLS =====
        assertThat(props.getTls().isEnabled()).isFalse();
        assertThat(props.getTls().isOpentls()).isFalse();
        assertThat(props.getTls().getKeystorePath()).isNull();
        assertThat(props.getTls().getKeystorePassword()).isNull();
        assertThat(props.getTls().getTruststorePath()).isNull();
        assertThat(props.getTls().getTruststorePassword()).isNull();

        // ===== Tracing =====
        assertThat(props.getTracing().isEnabled()).isTrue();

        // ===== HeaderPropagation =====
        assertThat(props.getHeaderPropagation().isEnabled()).isTrue();
        assertThat(props.getHeaderPropagation().getHeaders())
                .containsExactlyInAnyOrder(
                        "x-tenant-id",
                        "x-rd-request-timezone",
                        "x-rd-request-language",
                        "x-rd-canary-tag");

        // ===== Idempotent =====
        assertThat(props.getIdempotent().isEnabled()).isFalse();
        assertThat(props.getIdempotent().getDatasource()).isEqualTo("memory");
        assertThat(props.getIdempotent().getTtl()).isEqualTo(120_000L);

        // ===== Error =====
        assertThat(props.getError().getMaxRetries()).isEqualTo(3);
        assertThat(props.getError().getRetryDelay()).isEqualTo(java.time.Duration.ofSeconds(1));

        // ===== JetStream =====
        assertThat(props.getJetstream().isEnabled()).isFalse();
        assertThat(props.getJetstream().isAutoProvision()).isTrue();
        assertThat(props.getJetstream().getStreams()).isEmpty();
    }

    @Test
    void setters_shouldRoundTripAllProperties() {
        NatsProperties props = new NatsProperties();
        props.setEnabled(false);
        props.setServer("nats://nats.example.com:4222,nats://nats-bk.example.com:4222");

        assertThat(props.isEnabled()).isFalse();
        assertThat(props.getServer())
                .isEqualTo("nats://nats.example.com:4222,nats://nats-bk.example.com:4222");

        // 修改 AuthType
        props.getAuth().setType(AuthType.TOKEN);
        props.getAuth().setToken("secret-token");
        assertThat(props.getAuth().getType()).isEqualTo(AuthType.TOKEN);
        assertThat(props.getAuth().getToken()).isEqualTo("secret-token");

        // 修改 Reconnect
        props.getReconnect().setMaxReconnects(10);
        assertThat(props.getReconnect().getMaxReconnects()).isEqualTo(10);

        // 修改 Idempotent
        props.getIdempotent().setEnabled(true);
        props.getIdempotent().setDatasource("redis");
        props.getIdempotent().setTtl(60_000L);
        assertThat(props.getIdempotent().isEnabled()).isTrue();
        assertThat(props.getIdempotent().getDatasource()).isEqualTo("redis");
        assertThat(props.getIdempotent().getTtl()).isEqualTo(60_000L);
    }

    @Test
    void streamDefinition_shouldAcceptConsumerDefinitions() {
        NatsProperties.StreamDefinition stream = new NatsProperties.StreamDefinition();
        stream.setName("ORDERS");
        stream.setSubjects(java.util.List.of("orders.>"));
        stream.setStorageType("memory");
        stream.setRetention("workqueue");

        NatsProperties.ConsumerDefinition consumer = new NatsProperties.ConsumerDefinition();
        consumer.setName("order-processor");
        consumer.setFilterSubject("orders.created");
        consumer.setAckPolicy("explicit");
        consumer.setMaxDeliver(5);

        stream.setConsumers(java.util.List.of(consumer));

        assertThat(stream.getName()).isEqualTo("ORDERS");
        assertThat(stream.getSubjects()).containsExactly("orders.>");
        assertThat(stream.getConsumers()).hasSize(1);
        assertThat(stream.getConsumers().get(0).getName()).isEqualTo("order-processor");
        assertThat(stream.getConsumers().get(0).getFilterSubject()).isEqualTo("orders.created");
        assertThat(stream.getConsumers().get(0).getMaxDeliver()).isEqualTo(5);
    }
}
