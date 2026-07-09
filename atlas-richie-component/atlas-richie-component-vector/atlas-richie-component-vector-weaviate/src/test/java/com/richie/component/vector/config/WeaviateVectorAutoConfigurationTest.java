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
package com.richie.component.vector.config;

import io.weaviate.client.Config;
import io.weaviate.client.WeaviateClient;
import io.weaviate.client.v1.auth.exception.AuthException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WeaviateVectorAutoConfigurationTest {

    @Mock
    private EmbeddingModel embeddingModel;

    @Mock
    private WeaviateClient weaviateClient;

    @Nested
    @DisplayName("weaviateVectorStore")
    class WeaviateVectorStoreTests {

        @Test
        @DisplayName("should create store with default filterMetadataFields")
        void shouldCreateStoreWithDefaultFilterMetadataFields() {
            WeaviateConfig config = new WeaviateConfig();
            config.setObjectClass("MyClass");
            config.setConsistencyLevel(org.springframework.ai.vectorstore.weaviate.WeaviateVectorStore.ConsistentLevel.ONE);
            config.setFilterMetadataFields("country:text,year:number");

            WeaviateVectorAutoConfiguration autoConfig = new WeaviateVectorAutoConfiguration();
            VectorStore store = autoConfig.weaviateVectorStore(embeddingModel, weaviateClient, config);

            assertThat(store).isNotNull();
            assertThat(store).isInstanceOf(org.springframework.ai.vectorstore.weaviate.WeaviateVectorStore.class);
        }

        @Test
        @DisplayName("should handle empty filterMetadataFields")
        void shouldHandleEmptyFilterMetadataFields() {
            WeaviateConfig config = new WeaviateConfig();
            config.setObjectClass("EmptyClass");
            config.setFilterMetadataFields("");

            WeaviateVectorAutoConfiguration autoConfig = new WeaviateVectorAutoConfiguration();
            VectorStore store = autoConfig.weaviateVectorStore(embeddingModel, weaviateClient, config);

            assertThat(store).isNotNull();
        }

        @Test
        @DisplayName("should handle null filterMetadataFields")
        void shouldHandleNullFilterMetadataFields() {
            WeaviateConfig config = new WeaviateConfig();
            config.setObjectClass("NullClass");
            config.setFilterMetadataFields(null);

            WeaviateVectorAutoConfiguration autoConfig = new WeaviateVectorAutoConfiguration();
            VectorStore store = autoConfig.weaviateVectorStore(embeddingModel, weaviateClient, config);

            assertThat(store).isNotNull();
        }

        @Test
        @DisplayName("should handle blank filterMetadataFields")
        void shouldHandleBlankFilterMetadataFields() {
            WeaviateConfig config = new WeaviateConfig();
            config.setObjectClass("BlankClass");
            config.setFilterMetadataFields("   ");

            WeaviateVectorAutoConfiguration autoConfig = new WeaviateVectorAutoConfiguration();
            VectorStore store = autoConfig.weaviateVectorStore(embeddingModel, weaviateClient, config);

            assertThat(store).isNotNull();
        }

        @Test
        @DisplayName("should filter out unknown field types")
        void shouldFilterOutUnknownFieldTypes() {
            WeaviateConfig config = new WeaviateConfig();
            config.setObjectClass("MixedClass");
            config.setFilterMetadataFields("country:text,year:number,unknown:boolean");

            WeaviateVectorAutoConfiguration autoConfig = new WeaviateVectorAutoConfiguration();
            VectorStore store = autoConfig.weaviateVectorStore(embeddingModel, weaviateClient, config);

            assertThat(store).isNotNull();
        }

        @Test
        @DisplayName("should handle malformed field pairs")
        void shouldHandleMalformedFieldPairs() {
            WeaviateConfig config = new WeaviateConfig();
            config.setObjectClass("MalformedClass");
            config.setFilterMetadataFields("valid:text,invalid,another:text");

            WeaviateVectorAutoConfiguration autoConfig = new WeaviateVectorAutoConfiguration();
            VectorStore store = autoConfig.weaviateVectorStore(embeddingModel, weaviateClient, config);

            assertThat(store).isNotNull();
        }
    }

    @Nested
    @DisplayName("weaviateClient")
    class WeaviateClientTests {

        @Test
        @DisplayName("should create client without API key")
        void shouldCreateClientWithoutApiKey() {
            WeaviateConfig config = new WeaviateConfig();
            config.setScheme("http");
            config.setHost("localhost:8080");
            config.setApiKey(null);

            WeaviateVectorAutoConfiguration autoConfig = new WeaviateVectorAutoConfiguration();
            WeaviateClient client = autoConfig.weaviateClient(config);

            assertThat(client).isNotNull();
        }

        @Test
        @DisplayName("should create client with blank API key")
        void shouldCreateClientWithBlankApiKey() {
            WeaviateConfig config = new WeaviateConfig();
            config.setScheme("http");
            config.setHost("localhost:8080");
            config.setApiKey("   ");

            WeaviateVectorAutoConfiguration autoConfig = new WeaviateVectorAutoConfiguration();
            WeaviateClient client = autoConfig.weaviateClient(config);

            assertThat(client).isNotNull();
        }
    }
}
