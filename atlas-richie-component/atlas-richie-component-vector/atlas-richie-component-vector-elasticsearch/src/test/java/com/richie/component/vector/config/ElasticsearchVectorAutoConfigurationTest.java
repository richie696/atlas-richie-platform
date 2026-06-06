package com.richie.component.vector.config;

import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ElasticsearchVectorAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ElasticsearchVectorAutoConfiguration.class));

    @Test
    void restClient_registeredWhenProviderIsElasticsearch() {
        contextRunner
                .withPropertyValues(
                        "platform.component.vector.provider=elasticsearch",
                        "platform.component.vector.elasticsearch.cluster-url=http://localhost:9200")
                .run(context -> assertThat(context).hasSingleBean(Rest5Client.class));
    }

    @Test
    void restClient_absentWhenProviderMismatch() {
        contextRunner
                .withPropertyValues("platform.component.vector.provider=redis")
                .run(context -> assertThat(context).doesNotHaveBean(Rest5Client.class));
    }
}
