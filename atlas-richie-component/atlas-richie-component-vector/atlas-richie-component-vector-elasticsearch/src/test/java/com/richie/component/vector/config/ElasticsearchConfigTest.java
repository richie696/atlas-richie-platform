package com.richie.component.vector.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ElasticsearchConfig}.
 * Verifies that @ConfigurationProperties binding works correctly for all fields.
 */
class ElasticsearchConfigTest {

    @Test
    void defaults_shouldHaveSensibleValues() {
        ElasticsearchConfig config = new ElasticsearchConfig();

        assertThat(config.getClusterUrl()).isEqualTo("http://localhost:9200");
        assertThat(config.getConnectTimeout()).isEqualTo(5000);
        assertThat(config.getSocketTimeout()).isEqualTo(30000);
        assertThat(config.isContentCompressionEnabled()).isTrue();
    }

    @Test
    void setters_shouldUpdateValues() {
        ElasticsearchConfig config = new ElasticsearchConfig();

        config.setClusterUrl("http://es:9200");
        config.setConnectTimeout(10000);
        config.setSocketTimeout(60000);
        config.setContentCompressionEnabled(false);

        assertThat(config.getClusterUrl()).isEqualTo("http://es:9200");
        assertThat(config.getConnectTimeout()).isEqualTo(10000);
        assertThat(config.getSocketTimeout()).isEqualTo(60000);
        assertThat(config.isContentCompressionEnabled()).isFalse();
    }

    @Test
    void toString_shouldContainAllFields() {
        ElasticsearchConfig config = new ElasticsearchConfig();
        config.setClusterUrl("http://es:9200");
        config.setConnectTimeout(10000);
        config.setSocketTimeout(60000);
        config.setContentCompressionEnabled(false);

        String str = config.toString();

        assertThat(str).contains("clusterUrl");
        assertThat(str).contains("connectTimeout");
        assertThat(str).contains("socketTimeout");
        assertThat(str).contains("contentCompressionEnabled");
    }
}
