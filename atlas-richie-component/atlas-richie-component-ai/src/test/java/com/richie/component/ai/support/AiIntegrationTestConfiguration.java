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
package com.richie.component.ai.support;

import com.richie.component.ai.config.AiModelAutoConfiguration;
import com.richie.component.ai.config.AiModelProperties;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.retry.RetryTemplate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@SpringBootConfiguration
@EnableConfigurationProperties(AiModelProperties.class)
@ComponentScan(
        basePackages = "com.richie.component.ai",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = AiModelAutoConfiguration.class
        )
)
public class AiIntegrationTestConfiguration {

    @Bean("aiChatClients")
    Map<String, ChatClient> aiChatClients() {
        return new ConcurrentHashMap<>();
    }

    @Bean
    RetryTemplate aiRetryTemplate() {
        return new RetryTemplate();
    }

    @Bean
    ObservationRegistry observationRegistry() {
        return ObservationRegistry.create();
    }
}
