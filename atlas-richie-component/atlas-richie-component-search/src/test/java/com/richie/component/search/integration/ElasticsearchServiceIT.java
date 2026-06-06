package com.richie.component.search.integration;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.richie.component.search.service.ElasticsearchService;
import com.richie.component.search.support.SearchIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.annotation.Id;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SearchIntegrationTest
class ElasticsearchServiceIT {

    private static final String INDEX = "it_search_docs";

    @Autowired
    private ElasticsearchService elasticsearchService;

    @Autowired
    private ElasticsearchClient elasticsearchClient;

    @BeforeEach
    void recreateIndex() throws Exception {
        try {
            elasticsearchClient.indices().delete(d -> d.index(INDEX));
        } catch (Exception ignored) {
            // index may not exist on first run
        }
        elasticsearchClient.indices().create(c -> c.index(INDEX));
    }

    @Test
    void saveFindAndDelete_shouldWork() {
        ItDoc doc = new ItDoc(UUID.randomUUID().toString(), "wave-e");
        elasticsearchService.save(INDEX, doc);

        Optional<ItDoc> found = elasticsearchService.findByDocId(INDEX, doc.getId(), ItDoc.class);
        assertThat(found).isPresent();
        assertThat(found.get().getTitle()).isEqualTo("wave-e");

        assertThat(elasticsearchService.deleteByDocId(INDEX, doc.getId())).isTrue();
    }

    @org.springframework.data.elasticsearch.annotations.Document(indexName = "it_search_docs")
    static class ItDoc {
        @Id
        private String id;
        private String title;

        ItDoc() {
        }

        ItDoc(String id, String title) {
            this.id = id;
            this.title = title;
        }

        String getId() {
            return id;
        }

        void setId(String id) {
            this.id = id;
        }

        String getTitle() {
            return title;
        }

        void setTitle(String title) {
            this.title = title;
        }
    }
}
