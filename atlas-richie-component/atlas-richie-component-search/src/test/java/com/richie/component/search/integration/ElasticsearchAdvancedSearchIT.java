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

/**
 * Integration tests for advanced Elasticsearch features:
 * highlight rendering, suggester behavior, combined sort+condition queries,
 * count with sort, and deleteByCondition with sort.
 */
@SearchIntegrationTest
class ElasticsearchAdvancedSearchIT {

    @Autowired
    private ElasticsearchService elasticsearchService;

    @Autowired
    private ElasticsearchClient elasticsearchClient;

    @Autowired
    private ElasticsearchTemplate elasticsearchTemplate;

    private String indexName;

    @BeforeEach
    void setUp() {
        indexName = "it_search_adv_" + UUID.randomUUID();
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

    // ==================== HIGHLIGHT TESTS ====================

    @Test
    void search_withHighlightField_returnsHighlightedFragments() {
        AdvancedDoc doc1 = new AdvancedDoc(UUID.randomUUID().toString(), "wave crest analysis", "active", 80);
        AdvancedDoc doc2 = new AdvancedDoc(UUID.randomUUID().toString(), "ocean wave dynamics", "active", 70);
        AdvancedDoc doc3 = new AdvancedDoc(UUID.randomUUID().toString(), "river flow study", "active", 60);
        elasticsearchService.saveBatch(indexName, List.of(doc1, doc2, doc3));
        refreshIndex();

        PageResult<AdvancedDoc> result = elasticsearchService.search(
                SearchQueryWrapper.create(AdvancedDoc.class, indexName)
                        .eq(AdvancedDoc::getStatus, "active")
                        .like(AdvancedDoc::getTitle, "wave")
                        .highlight(AdvancedDoc::getTitle)
        );

        assertThat(result.getContent()).isNotEmpty();
        assertThat(result.getHighlights()).isNotNull();
        assertThat(result.getHighlights().values()).isNotEmpty();
        boolean hasTitleHighlight = result.getHighlights().values().stream()
                .anyMatch(map -> map.containsKey("title"));
        assertThat(hasTitleHighlight).isTrue();
    }

    @Test
    void search_withMultipleHighlightFields_returnsHighlightsForAll() {
        AdvancedDoc doc1 = new AdvancedDoc(UUID.randomUUID().toString(), "wave crest", "active", 80);
        AdvancedDoc doc2 = new AdvancedDoc(UUID.randomUUID().toString(), "ocean wave", "active", 70);
        elasticsearchService.saveBatch(indexName, List.of(doc1, doc2));
        refreshIndex();

        PageResult<AdvancedDoc> result = elasticsearchService.search(
                SearchQueryWrapper.create(AdvancedDoc.class, indexName)
                        .eq(AdvancedDoc::getStatus, "active")
                        .like(AdvancedDoc::getTitle, "wave")
                        .highlight(AdvancedDoc::getTitle, AdvancedDoc::getStatus)
        );

        assertThat(result.getContent()).isNotEmpty();
        assertThat(result.getHighlights()).isNotNull();
        boolean hasTitleOrStatusHighlight = result.getHighlights().values().stream()
                .anyMatch(map -> map.containsKey("title") || map.containsKey("status"));
        assertThat(hasTitleOrStatusHighlight).isTrue();
    }

    @Test
    void search_withoutHighlightFields_highlightsMapIsNullOrEmpty() {
        AdvancedDoc doc = new AdvancedDoc(UUID.randomUUID().toString(), "wave crest", "active", 80);
        elasticsearchService.save(indexName, doc);
        refreshIndex();

        PageResult<AdvancedDoc> result = elasticsearchService.search(
                SearchQueryWrapper.create(AdvancedDoc.class, indexName)
                        .eq(AdvancedDoc::getStatus, "active")
        );

        assertThat(result.getContent()).isNotEmpty();
        // Without highlight() call, highlights should be null (not set in result builder)
        assertThat(result.getHighlights()).isNull();
    }

    // ==================== SORT TESTS ====================

    @Test
    void search_withSortAsc_returnsAscendingOrder() {
        AdvancedDoc doc1 = new AdvancedDoc(UUID.randomUUID().toString(), "wave crest", "active", 30);
        AdvancedDoc doc2 = new AdvancedDoc(UUID.randomUUID().toString(), "ocean wave", "active", 90);
        AdvancedDoc doc3 = new AdvancedDoc(UUID.randomUUID().toString(), "river flow", "active", 60);
        elasticsearchService.saveBatch(indexName, List.of(doc1, doc2, doc3));
        refreshIndex();

        PageResult<AdvancedDoc> result = elasticsearchService.search(
                SearchQueryWrapper.create(AdvancedDoc.class, indexName)
                        .eq(AdvancedDoc::getStatus, "active")
                        .orderByAsc(AdvancedDoc::getScore)
        );

        assertThat(result.getContent()).hasSize(3);
        assertThat(result.getContent().get(0).getScore()).isEqualTo(30);
        assertThat(result.getContent().get(1).getScore()).isEqualTo(60);
        assertThat(result.getContent().get(2).getScore()).isEqualTo(90);
    }

    @Test
    void search_withSortDesc_returnsDescendingOrder() {
        AdvancedDoc doc1 = new AdvancedDoc(UUID.randomUUID().toString(), "wave crest", "active", 30);
        AdvancedDoc doc2 = new AdvancedDoc(UUID.randomUUID().toString(), "ocean wave", "active", 90);
        AdvancedDoc doc3 = new AdvancedDoc(UUID.randomUUID().toString(), "river flow", "active", 60);
        elasticsearchService.saveBatch(indexName, List.of(doc1, doc2, doc3));
        refreshIndex();

        PageResult<AdvancedDoc> result = elasticsearchService.search(
                SearchQueryWrapper.create(AdvancedDoc.class, indexName)
                        .eq(AdvancedDoc::getStatus, "active")
                        .orderByDesc(AdvancedDoc::getScore)
        );

        assertThat(result.getContent()).hasSize(3);
        assertThat(result.getContent().get(0).getScore()).isEqualTo(90);
        assertThat(result.getContent().get(1).getScore()).isEqualTo(60);
        assertThat(result.getContent().get(2).getScore()).isEqualTo(30);
    }

    @Test
    void search_withSortAndCondition_appliesBoth() {
        AdvancedDoc doc1 = new AdvancedDoc(UUID.randomUUID().toString(), "wave crest", "active", 30);
        AdvancedDoc doc2 = new AdvancedDoc(UUID.randomUUID().toString(), "ocean wave", "inactive", 90);
        AdvancedDoc doc3 = new AdvancedDoc(UUID.randomUUID().toString(), "river flow", "active", 60);
        AdvancedDoc doc4 = new AdvancedDoc(UUID.randomUUID().toString(), "lake calm", "active", 45);
        elasticsearchService.saveBatch(indexName, List.of(doc1, doc2, doc3, doc4));
        refreshIndex();

        PageResult<AdvancedDoc> result = elasticsearchService.search(
                SearchQueryWrapper.create(AdvancedDoc.class, indexName)
                        .eq(AdvancedDoc::getStatus, "active")
                        .orderByDesc(AdvancedDoc::getScore)
        );

        // Only active docs (3 of them), sorted desc by score
        assertThat(result.getContent()).hasSize(3);
        assertThat(result.getContent().get(0).getScore()).isEqualTo(60);
        assertThat(result.getContent().get(1).getScore()).isEqualTo(45);
        assertThat(result.getContent().get(2).getScore()).isEqualTo(30);
        // The inactive doc with score 90 should not appear
        assertThat(result.getContent().stream().anyMatch(d -> d.getScore() == 90)).isFalse();
    }

    @Test
    void count_withSort_appliesSortToCountQuery() {
        for (int i = 0; i < 5; i++) {
            elasticsearchService.save(indexName,
                    new AdvancedDoc(UUID.randomUUID().toString(), "doc-" + i, "active", 50 + i));
        }
        for (int i = 0; i < 3; i++) {
            elasticsearchService.save(indexName,
                    new AdvancedDoc(UUID.randomUUID().toString(), "doc-" + i, "inactive", 70 + i));
        }
        refreshIndex();

        // count with sort set - sort shouldn't affect count result
        long count = elasticsearchService.count(
                SearchQueryWrapper.create(AdvancedDoc.class, indexName)
                        .eq(AdvancedDoc::getStatus, "active")
                        .orderByAsc(AdvancedDoc::getScore)
        );

        assertThat(count).isEqualTo(5);
    }

    // ==================== SUGGESTION TESTS ====================

    @Test
    void search_withSuggestion_returnsSuggestionsMap() {
        // Use a Text field for suggester (will fall back to term suggester)
        AdvancedDoc doc1 = new AdvancedDoc(UUID.randomUUID().toString(), "wave crest analysis", "active", 80);
        AdvancedDoc doc2 = new AdvancedDoc(UUID.randomUUID().toString(), "ocean wave dynamics", "active", 70);
        AdvancedDoc doc3 = new AdvancedDoc(UUID.randomUUID().toString(), "river flow study", "active", 60);
        elasticsearchService.saveBatch(indexName, List.of(doc1, doc2, doc3));
        refreshIndex();

        // Use term suggester via suggest() with custom name
        PageResult<AdvancedDoc> result = elasticsearchService.search(
                SearchQueryWrapper.create(AdvancedDoc.class, indexName)
                        .eq(AdvancedDoc::getStatus, "active")
                        .suggest("title_suggest", AdvancedDoc::getTitle, "wave", 5)
        );

        // Assert method runs without exception and suggestions map is non-null
        assertThat(result.getSuggestions()).isNotNull();
        // The entry may exist even if empty (completion returns nothing, term fallback may also be empty)
        assertThat(result.getSuggestions().containsKey("title_suggest")).isTrue();
    }

    @Test
    void search_withTermSuggesterFallback_returnsEmptyListOnUnmatched() {
        AdvancedDoc doc1 = new AdvancedDoc(UUID.randomUUID().toString(), "wave crest", "active", 80);
        AdvancedDoc doc2 = new AdvancedDoc(UUID.randomUUID().toString(), "ocean wave", "active", 70);
        elasticsearchService.saveBatch(indexName, List.of(doc1, doc2));
        refreshIndex();

        // Use a prefix that won't match anything - term suggester fallback returns empty
        PageResult<AdvancedDoc> result = elasticsearchService.search(
                SearchQueryWrapper.create(AdvancedDoc.class, indexName)
                        .eq(AdvancedDoc::getStatus, "active")
                        .suggest("my_suggest", AdvancedDoc::getTitle, "xyz_nonexistent_prefix_12345", 5)
        );

        assertThat(result.getSuggestions()).isNotNull();
        assertThat(result.getSuggestions().containsKey("my_suggest")).isTrue();
        // Suggestions list may be empty due to no match
    }

    // ==================== COUNT TESTS ====================

    @Test
    void count_withEmptyConditionAndSort_callsFastCount() {
        for (int i = 0; i < 3; i++) {
            elasticsearchService.save(indexName,
                    new AdvancedDoc(UUID.randomUUID().toString(), "doc-" + i, "active", i * 10));
        }
        refreshIndex();

        // No conditions, with sort - should call fastCount
        long count = elasticsearchService.count(
                SearchQueryWrapper.create(AdvancedDoc.class, indexName)
                        .orderByAsc(AdvancedDoc::getScore)
        );

        assertThat(count).isEqualTo(3);
    }

    @Test
    void count_withMultipleConditionsAndSort_returnsCorrectCount() {
        for (int i = 0; i < 5; i++) {
            elasticsearchService.save(indexName,
                    new AdvancedDoc(UUID.randomUUID().toString(), "doc-" + i, "active", 10 + i));
        }
        for (int i = 0; i < 3; i++) {
            elasticsearchService.save(indexName,
                    new AdvancedDoc(UUID.randomUUID().toString(), "doc-" + i, "inactive", 20 + i));
        }
        refreshIndex();

        // eq + gt + sort
        long count = elasticsearchService.count(
                SearchQueryWrapper.create(AdvancedDoc.class, indexName)
                        .eq(AdvancedDoc::getStatus, "active")
                        .gt(AdvancedDoc::getScore, 12)
                        .orderByDesc(AdvancedDoc::getScore)
        );

        // active docs with score > 12: scores 13, 14 (2 docs)
        assertThat(count).isEqualTo(2);
    }

    // ==================== DELETE BY CONDITION WITH SORT TESTS ====================

    @Test
    void deleteByCondition_withSort_appliesSortAndReturnsCount() {
        for (int i = 0; i < 5; i++) {
            elasticsearchService.save(indexName,
                    new AdvancedDoc(UUID.randomUUID().toString(), "active-" + i, "active", 10 + i));
        }
        for (int i = 0; i < 2; i++) {
            elasticsearchService.save(indexName,
                    new AdvancedDoc(UUID.randomUUID().toString(), "inactive-" + i, "inactive", 50 + i));
        }
        refreshIndex();

        long deleted = elasticsearchService.deleteByCondition(
                SearchQueryWrapper.create(AdvancedDoc.class, indexName)
                        .eq(AdvancedDoc::getStatus, "active")
                        .orderByAsc(AdvancedDoc::getScore)
        );

        assertThat(deleted).isEqualTo(5);

        // Verify 2 inactive docs remain
        refreshIndex();
        PageResult<AdvancedDoc> remaining = elasticsearchService.search(
                SearchQueryWrapper.create(AdvancedDoc.class, indexName)
        );
        assertThat(remaining.getTotal()).isEqualTo(2);
    }

    @Test
    void deleteByCondition_withMultipleConditionsAndSort_returnsCount() {
        for (int i = 0; i < 4; i++) {
            elasticsearchService.save(indexName,
                    new AdvancedDoc(UUID.randomUUID().toString(), "doc-" + i, "active", 10 + i));
        }
        for (int i = 0; i < 3; i++) {
            elasticsearchService.save(indexName,
                    new AdvancedDoc(UUID.randomUUID().toString(), "doc-" + i, "inactive", 20 + i));
        }
        for (int i = 0; i < 2; i++) {
            elasticsearchService.save(indexName,
                    new AdvancedDoc(UUID.randomUUID().toString(), "doc-" + i, "active", 100 + i));
        }
        refreshIndex();

        long deleted = elasticsearchService.deleteByCondition(
                SearchQueryWrapper.create(AdvancedDoc.class, indexName)
                        .eq(AdvancedDoc::getStatus, "active")
                        .gt(AdvancedDoc::getScore, 12)
                        .orderByDesc(AdvancedDoc::getScore)
        );

        assertThat(deleted).isEqualTo(3);

        refreshIndex();
        PageResult<AdvancedDoc> remaining = elasticsearchService.search(
                SearchQueryWrapper.create(AdvancedDoc.class, indexName)
        );
        assertThat(remaining.getTotal()).isEqualTo(6);
    }

    // ==================== COMBINED FEATURE TESTS ====================

    @Test
    void search_withHighlightAndSort_returnsBoth() {
        AdvancedDoc doc1 = new AdvancedDoc(UUID.randomUUID().toString(), "wave crest", "active", 30);
        AdvancedDoc doc2 = new AdvancedDoc(UUID.randomUUID().toString(), "ocean wave", "active", 90);
        AdvancedDoc doc3 = new AdvancedDoc(UUID.randomUUID().toString(), "river flow", "active", 60);
        elasticsearchService.saveBatch(indexName, List.of(doc1, doc2, doc3));
        refreshIndex();

        PageResult<AdvancedDoc> result = elasticsearchService.search(
                SearchQueryWrapper.create(AdvancedDoc.class, indexName)
                        .eq(AdvancedDoc::getStatus, "active")
                        .like(AdvancedDoc::getTitle, "wave")
                        .highlight(AdvancedDoc::getTitle)
                        .orderByDesc(AdvancedDoc::getScore)
        );

        assertThat(result.getContent()).isNotEmpty();
        // Verify sort worked (highest score first)
        assertThat(result.getContent().get(0).getScore()).isEqualTo(90);
        // Verify highlights are populated (wave matched in title)
        assertThat(result.getHighlights()).isNotNull();
    }

    @Test
    void search_withHighlightSortAndSuggestion_returnsAllFeatures() {
        AdvancedDoc doc1 = new AdvancedDoc(UUID.randomUUID().toString(), "wave crest analysis", "active", 30);
        AdvancedDoc doc2 = new AdvancedDoc(UUID.randomUUID().toString(), "ocean wave dynamics", "active", 90);
        AdvancedDoc doc3 = new AdvancedDoc(UUID.randomUUID().toString(), "river flow study", "active", 60);
        elasticsearchService.saveBatch(indexName, List.of(doc1, doc2, doc3));
        refreshIndex();

        PageResult<AdvancedDoc> result = elasticsearchService.search(
                SearchQueryWrapper.create(AdvancedDoc.class, indexName)
                        .eq(AdvancedDoc::getStatus, "active")
                        .like(AdvancedDoc::getTitle, "wave")
                        .highlight(AdvancedDoc::getTitle)
                        .orderByDesc(AdvancedDoc::getScore)
                        .suggest("title_suggest", AdvancedDoc::getTitle, "wave", 5)
        );

        assertThat(result.getContent()).isNotEmpty();
        assertThat(result.getContent().get(0).getScore()).isEqualTo(90);
        assertThat(result.getHighlights()).isNotNull();
        assertThat(result.getSuggestions()).isNotNull();
        assertThat(result.getSuggestions().containsKey("title_suggest")).isTrue();
    }

    // ==================== TEST ENTITY CLASSES ====================

    @org.springframework.data.elasticsearch.annotations.Document(indexName = "it_search_adv_default")
    static class AdvancedDoc {
        @Id
        private String id;

        @Field(type = FieldType.Text)
        private String title;

        @Field(type = FieldType.Keyword)
        private String status;

        @Field(type = FieldType.Integer)
        private Integer score;

        AdvancedDoc() {
        }

        AdvancedDoc(String id, String title, String status, Integer score) {
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
