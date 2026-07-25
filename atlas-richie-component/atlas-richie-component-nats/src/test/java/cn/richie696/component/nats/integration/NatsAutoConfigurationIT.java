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
package cn.richie696.component.nats.integration;

import cn.richie696.component.nats.bus.NatsBus;
import cn.richie696.component.nats.bus.NatsEndpoint;
import cn.richie696.component.nats.config.NatsAutoConfiguration;
import cn.richie696.component.nats.config.NatsProperties;
import cn.richie696.component.nats.connection.NatsConnectionManager;
import cn.richie696.component.nats.strategy.DefaultNatsErrorStrategy;
import cn.richie696.component.nats.strategy.DefaultNatsHeaderExtractor;
import cn.richie696.component.nats.strategy.DefaultNatsHeaderInjector;
import cn.richie696.component.nats.strategy.JacksonNatsMessageSerializer;
import cn.richie696.component.nats.strategy.MemoryNatsIdempotentChecker;
import cn.richie696.component.nats.strategy.NatsErrorStrategy;
import cn.richie696.component.nats.strategy.NatsHeaderExtractor;
import cn.richie696.component.nats.strategy.NatsHeaderInjector;
import cn.richie696.component.nats.strategy.NatsIdempotentChecker;
import cn.richie696.component.nats.strategy.NatsMessageSerializer;
import cn.richie696.component.nats.support.NatsIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * NATS 自动配置集成测试 — 验证 Spring Boot 上下文正确装配所有 Bean。
 * <p>
 * 使用 Testcontainers 启动真实 NATS 容器，验证 {@link NatsAutoConfiguration}
 * 正确注册所有策略实现和门面 Bean。
 *
 * @author richie696
 */
@SpringBootTest(classes = NatsAutoConfiguration.class)
@EnabledIf("cn.richie696.component.nats.support.NatsIntegrationTestSupport#isEnabled")
class NatsAutoConfigurationIT {

    @DynamicPropertySource
    static void natsProperties(DynamicPropertyRegistry registry) {
        NatsIntegrationTestSupport.getInstance().registerNatsProperties(registry);
    }

    @Autowired
    private ApplicationContext context;

    @Test
    void shouldLoadNatsAutoConfiguration() {
        assertThat(context.containsBean("natsComponent")).isTrue();
    }

    @Test
    void shouldRegisterStrategyBeans() {
        assertThat(context.getBean(NatsMessageSerializer.class))
                .isInstanceOf(JacksonNatsMessageSerializer.class);
        assertThat(context.getBean(NatsHeaderInjector.class))
                .isInstanceOf(DefaultNatsHeaderInjector.class);
        assertThat(context.getBean(NatsHeaderExtractor.class))
                .isInstanceOf(DefaultNatsHeaderExtractor.class);
        assertThat(context.getBean(NatsErrorStrategy.class))
                .isInstanceOf(DefaultNatsErrorStrategy.class);
        assertThat(context.getBean(NatsIdempotentChecker.class))
                .isInstanceOf(MemoryNatsIdempotentChecker.class);
    }

    @Test
    void shouldRegisterConnectionManager() {
        NatsConnectionManager manager = context.getBean(NatsConnectionManager.class);
        assertThat(manager).isNotNull();
    }

    @Test
    void shouldRegisterFacadeBeans() {
        assertThat(context.getBean(NatsBus.class)).isNotNull();
        assertThat(context.getBean(NatsEndpoint.class)).isNotNull();
    }

    @Test
    void shouldRegisterNatsProperties() {
        NatsProperties properties = context.getBean(NatsProperties.class);
        assertThat(properties).isNotNull();
        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getServer()).startsWith("nats://");
    }
}
