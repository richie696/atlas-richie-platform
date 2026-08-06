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
package cn.richie696.component.mongodb.support;

import cn.richie696.component.mongodb.config.MongodbAutoConfiguration;
import cn.richie696.component.mongodb.listener.DefaultMongoServerListener;
import cn.richie696.component.mongodb.listener.DefaultMongoServerMonitorListener;
import cn.richie696.component.tenant.config.TenantAutoConfiguration;
import com.mongodb.event.ServerListener;
import com.mongodb.event.ServerMonitorListener;
import io.opentelemetry.api.OpenTelemetry;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@SpringBootConfiguration
@EnableAutoConfiguration(exclude = TenantAutoConfiguration.class)
@Import(MongodbAutoConfiguration.class)
public class MongodbIntegrationTestConfiguration {

    @Bean
    @ConditionalOnMissingBean(ServerListener.class)
    ServerListener mongoServerListener() {
        return new DefaultMongoServerListener();
    }

    @Bean
    @ConditionalOnMissingBean(ServerMonitorListener.class)
    ServerMonitorListener mongoServerMonitorListener() {
        return new DefaultMongoServerMonitorListener();
    }

    /**
     * 提供 noop {@link OpenTelemetry} 作为测试上下文兜底，让 {@code MongodbTracing} 的
     * {@code @Autowired OpenTelemetry} 字段能被满足。无此 bean 时 Spring 在 bean 创建阶段
     * （早于 {@code @PostConstruct} 兜底逻辑）就抛 NoSuchBeanDefinitionException，所有用此
     * 配置的 IT 都会 {@code failure threshold exceeded}。
     */
    @Bean
    @ConditionalOnMissingBean(OpenTelemetry.class)
    OpenTelemetry testOpenTelemetry() {
        return OpenTelemetry.noop();
    }
}
