package com.richie.component.vector.config;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QdrantVectorAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(QdrantVectorAutoConfiguration.class);

    @Test
    void beans_shouldNotBeCreated_whenProviderIsNotQdrant() {
        contextRunner
                .withPropertyValues("platform.component.vector.provider=mongodb")
                .run(context -> {
                    assertFalse(context.containsBean("qdrantVectorStore"));
                    assertFalse(context.containsBean("qdrantClient"));
                });
    }

    @Test
    void beans_shouldNotBeCreated_whenProviderPropertyIsRedis() {
        contextRunner
                .withPropertyValues("platform.component.vector.provider=redis")
                .run(context -> {
                    assertFalse(context.containsBean("qdrantVectorStore"));
                    assertFalse(context.containsBean("qdrantClient"));
                });
    }

    @Test
    void qdrantClient_instantiatesWithConfigValues() {
        QdRantConfig config = new QdRantConfig();
        config.setHost("test-host");
        config.setPort(6334);
        config.setUseTransportLayerSecurity(true);

        QdrantClient client = new QdrantClient(
                QdrantGrpcClient.newBuilder("test-host", 6334, true).build()
        );

        QdrantVectorAutoConfiguration autoConfig = new QdrantVectorAutoConfiguration();
        QdrantClient result = autoConfig.qdrantClient(config);

        assertNotNull(result);
    }

    @Test
    void qdrantVectorStore_buildsWithCollectionNameAndSchemaFlag() {
        QdrantClient mockClient = mock(QdrantClient.class);
        EmbeddingModel mockEmbedding = mock(EmbeddingModel.class);
        QdRantConfig config = new QdRantConfig();
        config.setCollection("test-collection");
        config.setInitializeSchema(true);

        QdrantVectorAutoConfiguration autoConfig = new QdrantVectorAutoConfiguration();
        VectorStore result = autoConfig.qdrantVectorStore(mockEmbedding, config, mockClient);

        assertNotNull(result);
    }

    @Test
    void qdrantVectorStore_buildsWithSchemaInitializationDisabled() {
        QdrantClient mockClient = mock(QdrantClient.class);
        EmbeddingModel mockEmbedding = mock(EmbeddingModel.class);
        QdRantConfig config = new QdRantConfig();
        config.setCollection("another-index");
        config.setInitializeSchema(false);

        QdrantVectorAutoConfiguration autoConfig = new QdrantVectorAutoConfiguration();
        VectorStore result = autoConfig.qdrantVectorStore(mockEmbedding, config, mockClient);

        assertNotNull(result);
    }
}
