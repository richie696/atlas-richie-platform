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
package com.richie.component.mongodb.circuitbreaker;

import com.richie.component.mongodb.config.MongodbAutoConfiguration;
import com.richie.component.mongodb.circuitbreaker.MongodbCircuitBreakerProperties;
import com.richie.component.mongodb.circuitbreaker.MongodbSentinelAutoConfiguration;
import com.richie.component.mongodb.circuitbreaker.MongodbSentinelAspect;
import com.richie.component.mongodb.core.AuditFieldHandler;
import com.richie.component.mongodb.core.EntityIntrospector;
import com.richie.component.mongodb.core.TenantHandler;
import com.richie.component.mongodb.observability.MongodbMetricsRecorder;
import com.richie.component.mongodb.observability.MongodbSlowQueryLogger;
import com.richie.component.mongodb.observability.MongodbTracing;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;

@SpringBootConfiguration
@EnableAutoConfiguration
@Import({MongodbAutoConfiguration.class, MongodbSentinelAutoConfiguration.class})
@EnableConfigurationProperties(MongodbCircuitBreakerProperties.class)
public class CircuitBreakerTestConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MongodbTracing mongodbTracing() {
        return null;
    }

    @Bean
    @ConditionalOnMissingBean
    public MongodbMetricsRecorder mongodbMetricsRecorder() {
        return null;
    }

    @Bean
    @ConditionalOnMissingBean
    public MongodbSlowQueryLogger mongodbSlowQueryLogger() {
        return null;
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditFieldHandler auditFieldHandler() {
        return new AuditFieldHandler();
    }

    @Bean
    @ConditionalOnMissingBean
    public TenantHandler tenantHandler() {
        return new TenantHandler();
    }
}
