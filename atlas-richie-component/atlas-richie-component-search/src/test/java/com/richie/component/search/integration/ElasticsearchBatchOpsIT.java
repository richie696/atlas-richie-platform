package com.richie.component.search.integration;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.richie.component.search.model.PageResult;
import com.richie.component.search.model.SearchQueryWrapper;
import com.richie.component.search.service.ElasticsearchService;
import com.richie.component.search.support.SearchIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SearchIntegrationTest
class ElasticsearchBatchOpsIT {

    @Autowired
    private ElasticsearchService elasticsearchService;

    @Autowired
    private ElasticsearchClient elasticsearchClient;

    @Autowired
    private ElasticsearchTemplate elasticsearchTemplate;

    private String indexName;

    @BeforeEach
    void setUp() {
        indexName = "it_search_batch_" + UUID.randomUUID();
        elasticsearchService.createIndex(indexName, null);
    }

    @AfterEach
    void cleanup() {
        try {
            elasticsearchClient.indices().delete(d -> d.index(indexName));
        } catch (Exception ignored) {
        }
    }

    private void refreshIndex() {
        elasticsearchTemplate.indexOps(IndexCoordinates.of(indexName)).refresh();
    }

    @Test
    void saveBatch_savesAllDocumentsAndReturnsList() {
        BatchDoc doc1 = new BatchDoc(UUID.randomUUID().toString(), "title-a", "active");
        BatchDoc doc2 = new BatchDoc(UUID.randomUUID().toString(), "title-b", "inactive");
        BatchDoc doc3 = new BatchDoc(UUID.randomUUID().toString(), "title-c", "active");

        List<BatchDoc> saved = elasticsearchService.saveBatch(indexName, List.of(doc1, doc2, doc3));
        refreshIndex();

        assertThat(saved).hasSize(3);
        assertThat(saved).allMatch(d -> d.getId() != null);

        PageResult<BatchDoc> result = elasticsearchService.search(
                SearchQueryWrapper.create(BatchDoc.class, indexName)
        );
        assertThat(result.getTotal()).isEqualTo(3);
    }

    @Test
    void deleteBatchByDocIds_removesAllDocuments() {
        BatchDoc doc1 = new BatchDoc(UUID.randomUUID().toString(), "title-a", "active");
        BatchDoc doc2 = new BatchDoc(UUID.randomUUID().toString(), "title-b", "inactive");
        BatchDoc doc3 = new BatchDoc(UUID.randomUUID().toString(), "title-c", "active");
        elasticsearchService.saveBatch(indexName, List.of(doc1, doc2, doc3));
        refreshIndex();

        long deleted = elasticsearchService.deleteByCondition(
                SearchQueryWrapper.create(BatchDoc.class, indexName)
                        .in(BatchDoc::getStatus, List.of("active", "inactive"))
        );
        refreshIndex();

        assertThat(deleted).isEqualTo(3);

        PageResult<BatchDoc> result = elasticsearchService.search(
                SearchQueryWrapper.create(BatchDoc.class, indexName)
        );
        assertThat(result.getTotal()).isZero();
    }

    @Test
    void count_withCondition_returnsCorrectCount() {
        for (int i = 0; i < 5; i++) {
            elasticsearchService.save(indexName, new BatchDoc(UUID.randomUUID().toString(), "title-" + i, "active"));
        }
        for (int i = 0; i < 2; i++) {
            elasticsearchService.save(indexName, new BatchDoc(UUID.randomUUID().toString(), "title-" + i, "inactive"));
        }
        refreshIndex();

        long count = elasticsearchService.count(
                SearchQueryWrapper.create(BatchDoc.class, indexName)
                        .eq(BatchDoc::getStatus, "active")
        );

        assertThat(count).isEqualTo(5);
    }

    @Test
    void deleteByCondition_removesMatchingDocs() {
        for (int i = 0; i < 5; i++) {
            elasticsearchService.save(indexName, new BatchDoc(UUID.randomUUID().toString(), "title-" + i, "active"));
        }
        for (int i = 0; i < 2; i++) {
            elasticsearchService.save(indexName, new BatchDoc(UUID.randomUUID().toString(), "title-" + i, "inactive"));
        }
        refreshIndex();

        long deleted = elasticsearchService.deleteByCondition(
                SearchQueryWrapper.create(BatchDoc.class, indexName)
                        .eq(BatchDoc::getStatus, "active")
        );
        refreshIndex();

        assertThat(deleted).isEqualTo(5);

        PageResult<BatchDoc> remaining = elasticsearchService.search(
                SearchQueryWrapper.create(BatchDoc.class, indexName)
        );
        assertThat(remaining.getTotal()).isEqualTo(2);
    }

    @Test
    void count_withoutCondition_returnsTotal() {
        elasticsearchService.save(indexName, new BatchDoc(UUID.randomUUID().toString(), "title-a", "active"));
        elasticsearchService.save(indexName, new BatchDoc(UUID.randomUUID().toString(), "title-b", "inactive"));
        elasticsearchService.save(indexName, new BatchDoc(UUID.randomUUID().toString(), "title-c", "pending"));
        refreshIndex();

        long count = elasticsearchService.count(
                SearchQueryWrapper.create(BatchDoc.class, indexName)
        );

        assertThat(count).isEqualTo(3);
    }

    @org.springframework.data.elasticsearch.annotations.Document(indexName = "it_search_batch_default")
    static class BatchDoc {
        @Id
        private String id;

        @Field(type = FieldType.Text)
        private String title;

        @Field(type = FieldType.Keyword)
        private String status;

        BatchDoc() {
        }

        BatchDoc(String id, String title, String status) {
            this.id = id;
            this.title = title;
            this.status = status;
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

        String getStatus() {
            return status;
        }

        void setStatus(String status) {
            this.status = status;
        }
    }
}
