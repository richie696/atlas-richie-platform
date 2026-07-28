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
package cn.richie696.component.chunking.config;

import cn.richie696.component.chunking.ChunkingService;
import cn.richie696.component.chunking.DefaultChunkingService;
import cn.richie696.component.chunking.model.ChunkingRule;
import cn.richie696.component.chunking.model.ChunkingResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

class ChunkingAutoConfigurationIT {

    @Nested
    @SpringBootTest(classes = ChunkingAutoConfigurationIT.TestConfig.class,
            webEnvironment = SpringBootTest.WebEnvironment.NONE)
    @TestPropertySource(properties = {
            "platform.component.document-chunking.enabled=true"
    })
    @DisplayName("When the chunking component is enabled")
    class WhenEnabled {

        @Autowired(required = false)
        private ChunkingService chunkingService;

        @Autowired
        private ChunkingProperties chunkingProperties;

        @Test
        @DisplayName("Spring auto-registers ChunkingService as DefaultChunkingService")
        void shouldRegisterDefaultChunkingService() {
            assertThat(chunkingService).isNotNull();
            assertThat(chunkingService).isInstanceOf(DefaultChunkingService.class);
        }

        @Test
        @DisplayName("ChunkingProperties binds nested Streaming/Recursive sub-properties")
        void shouldBindChunkingProperties() {
            assertThat(chunkingProperties).isNotNull();
            assertThat(chunkingProperties.isEnabled()).isTrue();
            assertThat(chunkingProperties.getStreaming()).isNotNull();
            assertThat(chunkingProperties.getRecursive()).isNotNull();
        }

        @Test
        @DisplayName("the registered bean can chunk content end-to-end")
        void shouldChunkViaInjectedBean() {
            ChunkingResult result = chunkingService.chunk(
                    "hello world this is a sample text",
                    ChunkingRule.recursiveDefaults(8, 0));

            assertThat(result).isNotNull();
            assertThat(result.chunks()).isNotEmpty();
            assertThat(result.diagnostics()).isNotNull();
        }
    }

    @Nested
    @SpringBootTest(classes = ChunkingAutoConfigurationIT.TestConfig.class,
            webEnvironment = SpringBootTest.WebEnvironment.NONE)
    @TestPropertySource(properties = {
            "platform.component.document-chunking.enabled=false"
    })
    @DisplayName("When the chunking component is disabled")
    class WhenDisabled {

        @Autowired(required = false)
        private ChunkingService chunkingService;

        @Test
        @DisplayName("Spring does not register a ChunkingService bean")
        void shouldNotRegisterChunkingService() {
            assertThat(chunkingService).isNull();
        }
    }

    @Nested
    @SpringBootTest(classes = ChunkingAutoConfigurationIT.CustomBeanTestConfig.class,
            webEnvironment = SpringBootTest.WebEnvironment.NONE)
    @TestPropertySource(properties = {
            "platform.component.document-chunking.enabled=true"
    })
    @DisplayName("When the caller supplies a custom ChunkingService bean")
    class WhenCustomBeanPresent {

        @Autowired
        private ChunkingService chunkingService;

        @Test
        @DisplayName("the user-defined bean wins over @ConditionalOnMissingBean default")
        void shouldRespectCustomBean() {
            assertThat(chunkingService).isNotNull();
            assertThat(chunkingService).isInstanceOf(CustomChunkingService.class);
        }
    }

    @SpringBootConfiguration
    @Import(ChunkingAutoConfiguration.class)
    static class TestConfig {
    }

    @SpringBootConfiguration
    @Import({CustomBeanConfig.class, ChunkingAutoConfiguration.class})
    static class CustomBeanTestConfig {
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomBeanConfig {
        @Bean
        ChunkingService customChunkingService() {
            return new CustomChunkingService();
        }
    }

    static class CustomChunkingService implements ChunkingService {
        @Override
        public ChunkingResult chunk(String content, ChunkingRule rule) {
            return new ChunkingResult(java.util.List.of());
        }
    }
}