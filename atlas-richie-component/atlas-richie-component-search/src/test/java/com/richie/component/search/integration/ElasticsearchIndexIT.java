package com.richie.component.search.integration;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.richie.component.search.service.ElasticsearchService;
import com.richie.component.search.support.SearchIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SearchIntegrationTest
class ElasticsearchIndexIT {

    @Autowired
    private ElasticsearchService elasticsearchService;

    @Autowired
    private ElasticsearchClient elasticsearchClient;

    private String indexName;

    @BeforeEach
    void setUp() {
        indexName = "it_search_idx_" + UUID.randomUUID();
    }

    @AfterEach
    void cleanup() {
        try {
            elasticsearchClient.indices().delete(d -> d.index(indexName));
        } catch (Exception ignored) {
        }
    }

    @Test
    void createIndex_withMapping_createsAndReturnsTrue() {
        String mappingJson = """
                {
                  "properties": {
                    "title": { "type": "text" },
                    "status": { "type": "keyword" }
                  }
                }
                """;
        boolean created = elasticsearchService.createIndex(indexName, mappingJson);
        assertThat(created).isTrue();
        assertThat(elasticsearchService.indexExists(indexName)).isTrue();
    }

    @Test
    void createIndex_withoutMapping_createsAndReturnsTrue() {
        boolean created = elasticsearchService.createIndex(indexName, null);
        assertThat(created).isTrue();
        assertThat(elasticsearchService.indexExists(indexName)).isTrue();
    }

    @Test
    void createIndex_alreadyExists_returnsFalse() {
        boolean first = elasticsearchService.createIndex(indexName, null);
        assertThat(first).isTrue();

        boolean second = elasticsearchService.createIndex(indexName, null);
        assertThat(second).isFalse();
    }

    @Test
    void deleteIndex_existing_returnsTrue() {
        elasticsearchService.createIndex(indexName, null);
        boolean deleted = elasticsearchService.deleteIndex(indexName);
        assertThat(deleted).isTrue();
        assertThat(elasticsearchService.indexExists(indexName)).isFalse();
    }

    @Test
    void deleteIndex_nonExistent_returnsFalse() {
        boolean deleted = elasticsearchService.deleteIndex(indexName);
        assertThat(deleted).isFalse();
    }

    @Test
    void indexExists_existing_returnsTrue() {
        elasticsearchService.createIndex(indexName, null);
        assertThat(elasticsearchService.indexExists(indexName)).isTrue();
    }

    @Test
    void indexExists_nonExistent_returnsFalse() {
        assertThat(elasticsearchService.indexExists(indexName)).isFalse();
    }
}
