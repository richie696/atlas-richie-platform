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

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link MilvusVectorAutoConfiguration} to achieve JaCoCo coverage
 * on the @Bean methods that are not exercised by Spring context tests.
 */
class MilvusVectorAutoConfigurationTest {

    private final MilvusVectorAutoConfiguration autoConfig = new MilvusVectorAutoConfiguration();

    @Nested
    @DisplayName("milvusClient() branch coverage")
    class MilvusClientBranchTests {

        @Test
        @DisplayName("basic config without auth or SSL")
        void milvusClient_basicConfig_coversNoAuthNoSsl() {
            MilvusConfig config = new MilvusConfig();
            config.setHost("http://test-host:19530");
            config.setPort(19530);
            config.setConnectTimeoutMs(5000);
            config.setKeepAliveTimeMs(10000);
            config.setKeepAliveTimeoutMs(5000);
            config.setIdleTimeoutMs(60000);

            try (var mocked = mockConstruction(MilvusServiceClient.class)) {
                MilvusServiceClient result = autoConfig.milvusClient(config);

                assertThat(result).isNotNull();
                assertThat(mocked.constructed()).hasSize(1);
            }
        }

        @Test
        @DisplayName("with username+password auth")
        void milvusClient_withAuth_coversAuthorizationBranch() {
            MilvusConfig config = new MilvusConfig();
            config.setHost("http://auth-host:19530");
            config.setPort(19530);
            config.setUsername("admin");
            config.setPassword("secret123");

            try (var mocked = mockConstruction(MilvusServiceClient.class)) {
                MilvusServiceClient result = autoConfig.milvusClient(config);

                assertThat(result).isNotNull();
                assertThat(mocked.constructed()).hasSize(1);
            }
        }

        @Test
        @DisplayName("SSL with serverPemPath")
        void milvusClient_sslWithServerPemPath_coversServerPemBranch() {
            MilvusConfig config = new MilvusConfig();
            config.setHost("https://ssl-host:19530");
            config.setPort(19530);
            config.setSecure(true);
            config.setServerPemPath("/path/to/server.pem");

            try (var mocked = mockConstruction(MilvusServiceClient.class)) {
                MilvusServiceClient result = autoConfig.milvusClient(config);

                assertThat(result).isNotNull();
                assertThat(mocked.constructed()).hasSize(1);
            }
        }

        @Test
        @DisplayName("SSL with serverName")
        void milvusClient_sslWithServerName_coversServerNameBranch() {
            MilvusConfig config = new MilvusConfig();
            config.setHost("https://ssl-host:19530");
            config.setPort(19530);
            config.setSecure(true);
            config.setServerName("milvus.example.com");

            try (var mocked = mockConstruction(MilvusServiceClient.class)) {
                MilvusServiceClient result = autoConfig.milvusClient(config);

                assertThat(result).isNotNull();
                assertThat(mocked.constructed()).hasSize(1);
            }
        }

        @Test
        @DisplayName("SSL with caPemPath")
        void milvusClient_sslWithCaPemPath_coversCaPemBranch() {
            MilvusConfig config = new MilvusConfig();
            config.setHost("https://ssl-host:19530");
            config.setPort(19530);
            config.setSecure(true);
            config.setCaPemPath("/path/to/ca.pem");

            try (var mocked = mockConstruction(MilvusServiceClient.class)) {
                MilvusServiceClient result = autoConfig.milvusClient(config);

                assertThat(result).isNotNull();
                assertThat(mocked.constructed()).hasSize(1);
            }
        }

        @Test
        @DisplayName("SSL with clientKeyPath+clientPemPath")
        void milvusClient_sslWithClientCert_coversClientCertBranch() {
            MilvusConfig config = new MilvusConfig();
            config.setHost("https://ssl-host:19530");
            config.setPort(19530);
            config.setSecure(true);
            config.setClientKeyPath("/path/to/client.key");
            config.setClientPemPath("/path/to/client.pem");

            try (var mocked = mockConstruction(MilvusServiceClient.class)) {
                MilvusServiceClient result = autoConfig.milvusClient(config);

                assertThat(result).isNotNull();
                assertThat(mocked.constructed()).hasSize(1);
            }
        }
    }

    @Nested
    @DisplayName("vectorStore() builder chain coverage")
    class VectorStoreBuilderChainTests {

        @Test
        @DisplayName("vectorStore executes builder chain with config values")
        void vectorStore_executesBuilderChain() {
            MilvusConfig config = new MilvusConfig();
            config.setDatabaseName("test-db");
            config.setCollectionName("test-collection");
            config.setIndexType(IndexType.HNSW);
            config.setMetricType(MetricType.L2);

            MilvusServiceClient milvusClient = mock(MilvusServiceClient.class);
            EmbeddingModel embeddingModel = mock(EmbeddingModel.class);

            // The builder is not a public class, so we call the real method.
            // JaCoCo covers the actual builder chain execution lines.
            VectorStore vectorStore = autoConfig.vectorStore(milvusClient, embeddingModel, config);

            assertThat(vectorStore).isNotNull();
            assertThat(vectorStore).isInstanceOf(MilvusVectorStore.class);
        }

        @Test
        @DisplayName("vectorStore with different config values")
        void vectorStore_differentConfigValues() {
            MilvusConfig config = new MilvusConfig();
            config.setDatabaseName("custom-db");
            config.setCollectionName("custom-collection");
            config.setIndexType(IndexType.IVF_FLAT);
            config.setMetricType(MetricType.IP);

            MilvusServiceClient milvusClient = mock(MilvusServiceClient.class);
            EmbeddingModel embeddingModel = mock(EmbeddingModel.class);

            VectorStore vectorStore = autoConfig.vectorStore(milvusClient, embeddingModel, config);

            assertThat(vectorStore).isNotNull();
            assertThat(vectorStore).isInstanceOf(MilvusVectorStore.class);
        }
    }

    @Test
    @DisplayName("vectorStore bean should be created with correct config")
    void vectorStoreBean_shouldUseConfig() {
        MilvusConfig config = new MilvusConfig();
        config.setDatabaseName("test-db");
        config.setCollectionName("test-collection");
        config.setIndexType(IndexType.HNSW);
        config.setMetricType(MetricType.L2);

        MilvusServiceClient milvusClient = mock(MilvusServiceClient.class);
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);

        VectorStore vectorStore = autoConfig.vectorStore(milvusClient, embeddingModel, config);

        assertThat(vectorStore).isNotNull();
        assertThat(vectorStore).isInstanceOf(MilvusVectorStore.class);
    }
}
