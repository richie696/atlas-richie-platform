package com.richie.component.vector.config;

import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ElasticsearchVectorAutoConfigurationTest {

    private final ApplicationContextRunner mismatchRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ElasticsearchVectorAutoConfiguration.class));

    private final ApplicationContextRunner elasticsearchRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ElasticsearchVectorAutoConfiguration.class))
            .withUserConfiguration(TestMocksConfig.class);

    @Test
    @Disabled("""
            Spring AI ElasticsearchVectorStore.builder().build() 在构造时会对
            Rest5Client 做健康检查（restResponse.getStatusCode()），mock 客户端
            无法满足该调用。需要 Testcontainers 真实 ES 或重构 auto-config
            添加 @ConditionalOnMissingBean(VectorStore.class)。
            """)
    void restClient_registeredWhenProviderIsElasticsearch() {
        elasticsearchRunner
                .withPropertyValues(
                        "platform.component.vector.provider=elasticsearch",
                        "platform.component.vector.elasticsearch.cluster-url=http://localhost:9200")
                .run(context -> assertThat(context).hasSingleBean(Rest5Client.class));
    }

    @Test
    void restClient_absentWhenProviderMismatch() {
        mismatchRunner
                .withPropertyValues("platform.component.vector.provider=redis")
                .run(context -> assertThat(context).doesNotHaveBean(Rest5Client.class));
    }

    @Configuration
    static class TestMocksConfig {
        @Bean
        EmbeddingModel embeddingModel() {
            return mock(EmbeddingModel.class);
        }
    }
}
