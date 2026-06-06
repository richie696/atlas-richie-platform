package com.richie.component.search.integration;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.richie.component.search.model.NativeQueryWrapper;
import com.richie.component.search.model.PageResult;
import com.richie.component.search.model.SearchQueryWrapper;
import com.richie.component.search.model.StringQueryWrapper;
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
class ElasticsearchQueryIT {

    @Autowired
    private ElasticsearchService elasticsearchService;

    @Autowired
    private ElasticsearchClient elasticsearchClient;

    @Autowired
    private ElasticsearchTemplate elasticsearchTemplate;

    private String indexName;

    @BeforeEach
    void setUp() {
        indexName = "it_search_query_" + UUID.randomUUID();
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
    void search_withEqCondition_returnsMatchingDocs() {
        SearchableDoc doc1 = new SearchableDoc(UUID.randomUUID().toString(), "wave crest", "active", 80);
        SearchableDoc doc2 = new SearchableDoc(UUID.randomUUID().toString(), "ocean wave", "inactive", 60);
        SearchableDoc doc3 = new SearchableDoc(UUID.randomUUID().toString(), "river flow", "active", 70);
        elasticsearchService.saveBatch(indexName, List.of(doc1, doc2, doc3));
        refreshIndex();

        PageResult<SearchableDoc> result = elasticsearchService.search(
                SearchQueryWrapper.create(SearchableDoc.class, indexName)
                        .eq(SearchableDoc::getStatus, "active")
        );

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotal()).isEqualTo(2);
    }

    @Test
    void search_withInCondition_returnsMatchingDocs() {
        SearchableDoc doc1 = new SearchableDoc(UUID.randomUUID().toString(), "wave crest", "active", 80);
        SearchableDoc doc2 = new SearchableDoc(UUID.randomUUID().toString(), "ocean wave", "inactive", 60);
        SearchableDoc doc3 = new SearchableDoc(UUID.randomUUID().toString(), "river flow", "pending", 70);
        elasticsearchService.saveBatch(indexName, List.of(doc1, doc2, doc3));
        refreshIndex();

        PageResult<SearchableDoc> result = elasticsearchService.search(
                SearchQueryWrapper.create(SearchableDoc.class, indexName)
                        .in(SearchableDoc::getStatus, List.of("active", "inactive"))
        );

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotal()).isEqualTo(2);
    }

    @Test
    void search_withBetweenCondition_returnsMatchingDocs() {
        SearchableDoc doc1 = new SearchableDoc(UUID.randomUUID().toString(), "wave crest", "active", 80);
        SearchableDoc doc2 = new SearchableDoc(UUID.randomUUID().toString(), "ocean wave", "active", 60);
        SearchableDoc doc3 = new SearchableDoc(UUID.randomUUID().toString(), "river flow", "active", 40);
        elasticsearchService.saveBatch(indexName, List.of(doc1, doc2, doc3));
        refreshIndex();

        PageResult<SearchableDoc> result = elasticsearchService.search(
                SearchQueryWrapper.create(SearchableDoc.class, indexName)
                        .between(SearchableDoc::getScore, 50, 100)
        );

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotal()).isEqualTo(2);
    }

    @Test
    void search_withLikeCondition_returnsMatchingDocs() {
        SearchableDoc doc1 = new SearchableDoc(UUID.randomUUID().toString(), "wave crest", "active", 80);
        SearchableDoc doc2 = new SearchableDoc(UUID.randomUUID().toString(), "ocean tide", "active", 60);
        SearchableDoc doc3 = new SearchableDoc(UUID.randomUUID().toString(), "river flow", "active", 70);
        elasticsearchService.saveBatch(indexName, List.of(doc1, doc2, doc3));
        refreshIndex();

        PageResult<SearchableDoc> result = elasticsearchService.search(
                SearchQueryWrapper.create(SearchableDoc.class, indexName)
                        .like(SearchableDoc::getTitle, "wave")
        );

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("wave crest");
    }

    @Test
    void search_withExistsCondition_returnsMatchingDocs() {
        SearchableDoc doc1 = new SearchableDoc(UUID.randomUUID().toString(), "wave crest", "active", 80);
        SearchableDoc doc2 = new SearchableDoc(UUID.randomUUID().toString(), "river flow", null, 70);
        elasticsearchService.saveBatch(indexName, List.of(doc1, doc2));
        refreshIndex();

        PageResult<SearchableDoc> result = elasticsearchService.search(
                SearchQueryWrapper.create(SearchableDoc.class, indexName)
                        .exists(SearchableDoc::getStatus)
        );

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("wave crest");
    }

    @Test
    void search_withPagination_returnsCorrectPage() {
        for (int i = 0; i < 5; i++) {
            elasticsearchService.save(indexName,
                    new SearchableDoc(UUID.randomUUID().toString(), "doc-" + i, "active", 50 + i));
        }
        refreshIndex();

        PageResult<SearchableDoc> result = elasticsearchService.search(
                SearchQueryWrapper.create(SearchableDoc.class, indexName)
                        .eq(SearchableDoc::getStatus, "active")
                        .page(0, 2)
        );

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotal()).isEqualTo(5);
    }

    @Test
    void search_withSorting_returnsSortedResults() {
        SearchableDoc doc1 = new SearchableDoc(UUID.randomUUID().toString(), "wave crest", "active", 30);
        SearchableDoc doc2 = new SearchableDoc(UUID.randomUUID().toString(), "ocean wave", "active", 90);
        SearchableDoc doc3 = new SearchableDoc(UUID.randomUUID().toString(), "river flow", "active", 60);
        elasticsearchService.saveBatch(indexName, List.of(doc1, doc2, doc3));
        refreshIndex();

        PageResult<SearchableDoc> result = elasticsearchService.search(
                SearchQueryWrapper.create(SearchableDoc.class, indexName)
                        .eq(SearchableDoc::getStatus, "active")
                        .orderByAsc(SearchableDoc::getScore)
        );

        assertThat(result.getContent()).hasSize(3);
        assertThat(result.getContent().get(0).getScore()).isEqualTo(30);
        assertThat(result.getContent().get(1).getScore()).isEqualTo(60);
        assertThat(result.getContent().get(2).getScore()).isEqualTo(90);
    }

    @Test
    void findOne_withMatchCondition_returnsSingleEntity() {
        SearchableDoc unique = new SearchableDoc(UUID.randomUUID().toString(), "unique-wave-title", "active", 85);
        SearchableDoc other = new SearchableDoc(UUID.randomUUID().toString(), "other-title", "active", 70);
        elasticsearchService.saveBatch(indexName, List.of(unique, other));
        refreshIndex();

        SearchableDoc found = elasticsearchService.findOne(
                SearchQueryWrapper.create(SearchableDoc.class, indexName)
                        .eq(SearchableDoc::getTitle, "unique-wave-title")
        );

        assertThat(found).isNotNull();
        assertThat(found.getTitle()).isEqualTo("unique-wave-title");
    }

    @Test
    void findOne_withNoMatch_returnsNull() {
        SearchableDoc doc = new SearchableDoc(UUID.randomUUID().toString(), "some-title", "active", 80);
        elasticsearchService.save(indexName, doc);
        refreshIndex();

        SearchableDoc found = elasticsearchService.findOne(
                SearchQueryWrapper.create(SearchableDoc.class, indexName)
                        .eq(SearchableDoc::getTitle, "nonexistent-title")
        );

        assertThat(found).isNull();
    }

    @Test
    void nativeSearch_withMatchAllQuery_returnsAllDocs() {
        SearchableDoc doc1 = new SearchableDoc(UUID.randomUUID().toString(), "wave crest", "active", 80);
        SearchableDoc doc2 = new SearchableDoc(UUID.randomUUID().toString(), "ocean wave", "inactive", 60);
        elasticsearchService.saveBatch(indexName, List.of(doc1, doc2));
        refreshIndex();

        PageResult<SearchableDoc> result = elasticsearchService.nativeSearch(
                NativeQueryWrapper.create(SearchableDoc.class, indexName,
                        co.elastic.clients.elasticsearch._types.query_dsl.Query.of(q -> q.matchAll(m -> m)))
        );

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotal()).isEqualTo(2);
    }

    @Test
    void dslSearch_withStringQuery_returnsDocs() {
        SearchableDoc doc1 = new SearchableDoc(UUID.randomUUID().toString(), "wave crest", "active", 80);
        SearchableDoc doc2 = new SearchableDoc(UUID.randomUUID().toString(), "ocean wave", "active", 60);
        elasticsearchService.saveBatch(indexName, List.of(doc1, doc2));
        refreshIndex();

        PageResult<SearchableDoc> result = elasticsearchService.dslSearch(
                StringQueryWrapper.create(SearchableDoc.class, indexName,
                        "{\"match\":{\"title\":\"wave\"}}")
        );

        assertThat(result.getContent()).hasSize(2);
    }

    @org.springframework.data.elasticsearch.annotations.Document(indexName = "it_search_query_default")
    static class SearchableDoc {
        @Id
        private String id;

        @Field(type = FieldType.Text)
        private String title;

        @Field(type = FieldType.Keyword)
        private String status;

        @Field(type = FieldType.Integer)
        private Integer score;

        SearchableDoc() {
        }

        SearchableDoc(String id, String title, String status, Integer score) {
            this.id = id;
            this.title = title;
            this.status = status;
            this.score = score;
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

        Integer getScore() {
            return score;
        }

        void setScore(Integer score) {
            this.score = score;
        }
    }
}
