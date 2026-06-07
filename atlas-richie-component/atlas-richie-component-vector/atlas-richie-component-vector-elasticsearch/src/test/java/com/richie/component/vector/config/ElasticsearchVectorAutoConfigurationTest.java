package com.richie.component.vector.config;

import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import co.elastic.clients.transport.rest5_client.low_level.Rest5ClientBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStore;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ElasticsearchVectorAutoConfigurationTest {

    private final ApplicationContextRunner mismatchRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ElasticsearchVectorAutoConfiguration.class));

    private final ApplicationContextRunner elasticsearchRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ElasticsearchVectorAutoConfiguration.class))
            .withUserConfiguration(TestVectorStoreConfig.class);

    @Test
    void restClient_notRegisteredWhenProviderIsNotElasticsearch() {
        mismatchRunner
                .withPropertyValues("platform.component.vector.provider=redis")
                .run(context -> assertThat(context).doesNotHaveBean(Rest5Client.class));
    }

    @Test
    void elasticsearchVectorStore_notRegisteredWhenProviderIsNotElasticsearch() {
        mismatchRunner
                .withPropertyValues("platform.component.vector.provider=redis")
                .run(context -> assertThat(context).doesNotHaveBean(VectorStore.class));
    }

    @Test
    void vectorStore_notRegisteredWhenProviderIsNotElasticsearch() {
        mismatchRunner
                .withPropertyValues(
                        "platform.component.vector.provider=redis",
                        "platform.component.vector.elasticsearch.cluster-url=http://localhost:9200")
                .run(context -> assertThat(context).doesNotHaveBean(VectorStore.class));
    }

    @Nested
    @DisplayName("restClient() direct call coverage")
    class RestClientDirectCallTests {

        @Test
        @DisplayName("should execute restClient method body with default config")
        void restClient_shouldExecuteWithDefaultConfig() throws Exception {
            ElasticsearchConfig config = new ElasticsearchConfig();
            config.setClusterUrl("http://es:9200");
            config.setConnectTimeout(10000);
            config.setSocketTimeout(60000);
            config.setContentCompressionEnabled(false);

            ElasticsearchVectorAutoConfiguration autoConfig = new ElasticsearchVectorAutoConfiguration();

            try (MockedConstruction<Rest5ClientBuilder> mockedBuilder =
                         mockConstruction(Rest5ClientBuilder.class, (mock, ctx) -> {
                             when(mock.setConnectionConfigCallback(any())).thenAnswer(invocation -> {
                                 org.apache.hc.client5.http.config.ConnectionConfig.Builder realBuilder =
                                         org.apache.hc.client5.http.config.ConnectionConfig.custom().setConnectTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS).setSocketTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS);
                                 Object callback = invocation.getArgument(0);
                                 try {
                                     java.lang.reflect.Method method = callback.getClass().getDeclaredMethods()[0];
                                     method.setAccessible(true);
                                     method.invoke(callback, realBuilder);
                                 } catch (Exception e) {
                                     throw new RuntimeException(e);
                                 }
                                 return mock;
                             });
                             when(mock.setCompressionEnabled(anyBoolean())).thenReturn(mock);
                             when(mock.build()).thenReturn(mock(Rest5Client.class, withSettings().lenient()));
                         })) {

                Rest5Client result = autoConfig.restClient(config);

                assertThat(result).isNotNull();
                assertThat(mockedBuilder.constructed()).hasSize(1);
            }
        }

        @Test
        @DisplayName("should execute restClient with compression enabled")
        void restClient_shouldExecuteWithCompressionEnabled() throws Exception {
            ElasticsearchConfig config = new ElasticsearchConfig();
            config.setClusterUrl("http://localhost:9200");

            ElasticsearchVectorAutoConfiguration autoConfig = new ElasticsearchVectorAutoConfiguration();

            try (MockedConstruction<Rest5ClientBuilder> mockedBuilder =
                         mockConstruction(Rest5ClientBuilder.class, (mock, ctx) -> {
                             when(mock.setConnectionConfigCallback(any())).thenAnswer(invocation -> {
                                 org.apache.hc.client5.http.config.ConnectionConfig.Builder realBuilder =
                                         org.apache.hc.client5.http.config.ConnectionConfig.custom().setConnectTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS).setSocketTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS);
                                 Object callback = invocation.getArgument(0);
                                 try {
                                     java.lang.reflect.Method method = callback.getClass().getDeclaredMethods()[0];
                                     method.setAccessible(true);
                                     method.invoke(callback, realBuilder);
                                 } catch (Exception e) {
                                     throw new RuntimeException(e);
                                 }
                                 return mock;
                             });
                             when(mock.setCompressionEnabled(anyBoolean())).thenReturn(mock);
                             when(mock.build()).thenReturn(mock(Rest5Client.class, withSettings().lenient()));
                         })) {

                Rest5Client result = autoConfig.restClient(config);

                assertThat(result).isNotNull();
            }
        }
    }

    @Nested
    @DisplayName("elasticsearchVectorStore() direct call coverage")
    class ElasticsearchVectorStoreDirectCallTests {

        @Test
        @DisplayName("should execute elasticsearchVectorStore method body")
        void elasticsearchVectorStore_shouldExecuteWithMocks() {
            Rest5Client mockRestClient = mock(Rest5Client.class);
            EmbeddingModel mockEmbeddingModel = mock(EmbeddingModel.class);
            ElasticsearchVectorStore.Builder mockBuilder = mock(ElasticsearchVectorStore.Builder.class);

            try (MockedStatic<ElasticsearchVectorStore> mockedStatic =
                         mockStatic(ElasticsearchVectorStore.class)) {
                mockedStatic.when(() -> ElasticsearchVectorStore.builder(any(), any()))
                        .thenReturn(mockBuilder);
                when(mockBuilder.build()).thenReturn(mock(ElasticsearchVectorStore.class, withSettings().lenient()));

                ElasticsearchVectorAutoConfiguration autoConfig = new ElasticsearchVectorAutoConfiguration();
                VectorStore result = autoConfig.elasticsearchVectorStore(mockRestClient, mockEmbeddingModel);

                assertThat(result).isNotNull();
                mockedStatic.verify(() -> ElasticsearchVectorStore.builder(mockRestClient, mockEmbeddingModel));
            }
        }
    }

    @Configuration
    static class TestVectorStoreConfig {
        @Bean
        EmbeddingModel embeddingModel() {
            return mock(EmbeddingModel.class);
        }

        @Bean
        VectorStore vectorStore() {
            return mock(VectorStore.class);
        }
    }
}
