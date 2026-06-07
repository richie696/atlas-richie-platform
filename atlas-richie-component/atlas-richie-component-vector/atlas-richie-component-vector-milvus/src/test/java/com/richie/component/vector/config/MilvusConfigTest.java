package com.richie.component.vector.config;

import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MilvusConfigTest {

    @Test
    @DisplayName("should have correct default values")
    void defaults_shouldBeCorrect() {
        MilvusConfig config = new MilvusConfig();

        assertThat(config.getHost()).isEqualTo("localhost");
        assertThat(config.getPort()).isEqualTo(19530);
        assertThat(config.getDatabaseName()).isEqualTo("default");
        assertThat(config.getCollectionName()).isEqualTo("documents");
        assertThat(config.getEmbeddingDimension()).isEqualTo(1536);
        assertThat(config.getIndexType()).isEqualTo(IndexType.IVF_FLAT);
        assertThat(config.getMetricType()).isEqualTo(MetricType.COSINE);
        assertThat(config.getConnectTimeoutMs()).isEqualTo(10000);
        assertThat(config.getKeepAliveTimeMs()).isEqualTo(30000);
        assertThat(config.getKeepAliveTimeoutMs()).isEqualTo(10000);
        assertThat(config.isKeepAliveWithoutCalls()).isTrue();
        assertThat(config.getIdleTimeoutMs()).isEqualTo(300000);
        assertThat(config.isSecure()).isFalse();
    }

    @Test
    @DisplayName("should set and get host")
    void host_shouldBeSettable() {
        MilvusConfig config = new MilvusConfig();
        config.setHost("192.168.1.100");
        assertThat(config.getHost()).isEqualTo("192.168.1.100");
    }

    @Test
    @DisplayName("should set and get port")
    void port_shouldBeSettable() {
        MilvusConfig config = new MilvusConfig();
        config.setPort(19531);
        assertThat(config.getPort()).isEqualTo(19531);
    }

    @Test
    @DisplayName("should set and get username and password")
    void auth_shouldBeSettable() {
        MilvusConfig config = new MilvusConfig();
        config.setUsername("admin");
        config.setPassword("secret");
        assertThat(config.getUsername()).isEqualTo("admin");
        assertThat(config.getPassword()).isEqualTo("secret");
    }

    @Test
    @DisplayName("should set and get databaseName")
    void databaseName_shouldBeSettable() {
        MilvusConfig config = new MilvusConfig();
        config.setDatabaseName("custom-db");
        assertThat(config.getDatabaseName()).isEqualTo("custom-db");
    }

    @Test
    @DisplayName("should set and get collectionName")
    void collectionName_shouldBeSettable() {
        MilvusConfig config = new MilvusConfig();
        config.setCollectionName("my-collection");
        assertThat(config.getCollectionName()).isEqualTo("my-collection");
    }

    @Test
    @DisplayName("should set and get embeddingDimension")
    void embeddingDimension_shouldBeSettable() {
        MilvusConfig config = new MilvusConfig();
        config.setEmbeddingDimension(2048);
        assertThat(config.getEmbeddingDimension()).isEqualTo(2048);
    }

    @Test
    @DisplayName("should set and get indexType")
    void indexType_shouldBeSettable() {
        MilvusConfig config = new MilvusConfig();
        config.setIndexType(IndexType.HNSW);
        assertThat(config.getIndexType()).isEqualTo(IndexType.HNSW);
    }

    @Test
    @DisplayName("should set and get metricType")
    void metricType_shouldBeSettable() {
        MilvusConfig config = new MilvusConfig();
        config.setMetricType(MetricType.L2);
        assertThat(config.getMetricType()).isEqualTo(MetricType.L2);
    }

    @Test
    @DisplayName("should set and get timeout values")
    void timeouts_shouldBeSettable() {
        MilvusConfig config = new MilvusConfig();
        config.setConnectTimeoutMs(20000);
        config.setKeepAliveTimeMs(60000);
        config.setKeepAliveTimeoutMs(20000);
        config.setIdleTimeoutMs(600000);

        assertThat(config.getConnectTimeoutMs()).isEqualTo(20000);
        assertThat(config.getKeepAliveTimeMs()).isEqualTo(60000);
        assertThat(config.getKeepAliveTimeoutMs()).isEqualTo(20000);
        assertThat(config.getIdleTimeoutMs()).isEqualTo(600000);
    }

    @Test
    @DisplayName("should set and get SSL fields")
    void sslFields_shouldBeSettable() {
        MilvusConfig config = new MilvusConfig();
        config.setSecure(true);
        config.setServerPemPath("/path/to/server.pem");
        config.setServerName("milvus.example.com");
        config.setCaPemPath("/path/to/ca.pem");
        config.setClientKeyPath("/path/to/client.key");
        config.setClientPemPath("/path/to/client.pem");

        assertThat(config.isSecure()).isTrue();
        assertThat(config.getServerPemPath()).isEqualTo("/path/to/server.pem");
        assertThat(config.getServerName()).isEqualTo("milvus.example.com");
        assertThat(config.getCaPemPath()).isEqualTo("/path/to/ca.pem");
        assertThat(config.getClientKeyPath()).isEqualTo("/path/to/client.key");
        assertThat(config.getClientPemPath()).isEqualTo("/path/to/client.pem");
    }
}
