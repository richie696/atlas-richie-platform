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
package cn.richie696.component.ai.support;

import cn.richie696.component.ai.config.AiModelAutoConfiguration;
import cn.richie696.component.ai.config.AiModelProperties;
import cn.richie696.component.http.core.HttpCoreProperties;
import cn.richie696.component.http.jdk.config.HttpAutoConfiguration;
import cn.richie696.component.http.jdk.config.HttpProperties;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.core.retry.RetryTemplate;

import java.util.Map;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootConfiguration
@EnableConfigurationProperties({AiModelProperties.class, HttpCoreProperties.class, HttpProperties.class})
@Import(HttpAutoConfiguration.class)
@ComponentScan(
        basePackages = "cn.richie696.component.ai",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = AiModelAutoConfiguration.class
        )
)
public class AiIntegrationTestConfiguration {

    @Bean("aiChatClients")
    Map<String, ChatClient> aiChatClients() {
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callResponseSpec = mock(ChatClient.CallResponseSpec.class);
        ChatResponse chatResponse = new ChatResponse(
                List.of(new Generation(new AssistantMessage("it-hello"))));
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.messages(anyList())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("it-hello");
        when(callResponseSpec.chatResponse()).thenReturn(chatResponse);
        return new ConcurrentHashMap<>(Map.of("it-stub", chatClient));
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
