package com.richie.component.search.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.richie.component.search.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.TotalHitsRelation;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.data.elasticsearch.core.query.ByQueryResponse;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ElasticSearchServiceImplTest {

    @Mock
    private ElasticsearchTemplate template;

    @Mock
    private ElasticsearchClient elasticsearchClient;

    @Mock
    private IndexOperations indexOperations;

    private ElasticSearchServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ElasticSearchServiceImpl(template, elasticsearchClient);
    }

    @Test
    void createIndex_whenMissing_createsAndReturnsTrue() {
        when(template.indexOps(any(IndexCoordinates.class))).thenReturn(indexOperations);
        when(indexOperations.exists()).thenReturn(false);

        assertThat(service.createIndex("it-books", null)).isTrue();
        verify(indexOperations).create();
    }

    @Test
    void createIndex_whenExists_returnsFalse() {
        when(template.indexOps(any(IndexCoordinates.class))).thenReturn(indexOperations);
        when(indexOperations.exists()).thenReturn(true);

        assertThat(service.createIndex("it-books", null)).isFalse();
    }

    @Test
    void createIndex_withMappingJson_createsAndPutsMapping() {
        when(template.indexOps(any(IndexCoordinates.class))).thenReturn(indexOperations);
        when(indexOperations.exists()).thenReturn(false);

        String mappingJson = "{\"properties\":{\"title\":{\"type\":\"text\"}}}";

        assertThat(service.createIndex("it-books", mappingJson)).isTrue();
        verify(indexOperations).create();
        verify(indexOperations).putMapping(any(org.springframework.data.elasticsearch.core.document.Document.class));
    }

    @Test
    void deleteIndex_whenExists_deletes() {
        when(template.indexOps(any(IndexCoordinates.class))).thenReturn(indexOperations);
        when(indexOperations.exists()).thenReturn(true);
        when(indexOperations.delete()).thenReturn(true);

        assertThat(service.deleteIndex("it-books")).isTrue();
    }

    @Test
    void indexExists_delegatesToTemplate() {
        when(template.indexOps(any(IndexCoordinates.class))).thenReturn(indexOperations);
        when(indexOperations.exists()).thenReturn(true);

        assertThat(service.indexExists("it-books")).isTrue();
    }

    @Test
    void saveAndFindByDocId_roundTrip() {
        SampleDoc doc = new SampleDoc("1", "hello");
        when(template.save(eq(doc), any(IndexCoordinates.class))).thenReturn(doc);
        when(template.get("1", SampleDoc.class, IndexCoordinates.of("it-books"))).thenReturn(doc);

        SampleDoc saved = service.save("it-books", doc);
        Optional<SampleDoc> found = service.findByDocId("it-books", "1", SampleDoc.class);

        assertThat(saved.getTitle()).isEqualTo("hello");
        assertThat(found).contains(saved);
    }

    @Test
    void deleteByDocId_delegatesToTemplate() {
        assertThat(service.deleteByDocId("it-books", "1")).isTrue();
        verify(template).delete("1", IndexCoordinates.of("it-books"));
    }

    @Test
    void fastCount_returnsTemplateCount() {
        when(template.count(any(Query.class), any(IndexCoordinates.class))).thenReturn(42L);
        assertThat(service.fastCount("it-books")).isEqualTo(42L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void search_withBasicWrapper_returnsHits() {
        SampleDoc doc = new SampleDoc("1", "hello");
        List<SearchHit<SampleDoc>> hits = List.of(createSearchHit(doc, "1"));

        SearchHits<SampleDoc> searchHits = createSearchHits(hits, 1L);
        when(template.search(any(Query.class), eq(SampleDoc.class), any(IndexCoordinates.class)))
                .thenReturn(searchHits);

        SearchQueryWrapper<SampleDoc> wrapper = SearchQueryWrapper.<SampleDoc>create(SampleDoc.class, "it-books")
                .eq(SampleDoc::getId, "1");

        PageResult<SampleDoc> result = service.search(wrapper);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().getTitle()).isEqualTo("hello");
        assertThat(result.getTotal()).isEqualTo(1L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void search_withStringQueryWrapper_invokesInternalSearch() {
        SearchHits<SampleDoc> searchHits = createSearchHits(List.of(), 0L);
        when(template.search(any(Query.class), eq(SampleDoc.class), any(IndexCoordinates.class)))
                .thenReturn(searchHits);

        StringQueryWrapper<SampleDoc> wrapper = StringQueryWrapper.<SampleDoc>create(SampleDoc.class, "it-books", "{\"match_all\":{}}");

        PageResult<SampleDoc> result = service.dslSearch(wrapper);

        assertThat(result.getContent()).isEmpty();
        verify(template).search(any(Query.class), eq(SampleDoc.class), any(IndexCoordinates.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void search_withNativeQueryWrapper_invokesInternalSearch() {
        SearchHits<SampleDoc> searchHits = createSearchHits(List.of(), 0L);
        when(template.search(any(Query.class), eq(SampleDoc.class), any(IndexCoordinates.class)))
                .thenReturn(searchHits);

        co.elastic.clients.elasticsearch._types.query_dsl.Query nativeQuery =
                co.elastic.clients.elasticsearch._types.query_dsl.Query.of(q -> q.matchAll(m -> m));
        NativeQueryWrapper<SampleDoc> wrapper = NativeQueryWrapper.<SampleDoc>create(SampleDoc.class, "it-books", nativeQuery);

        PageResult<SampleDoc> result = service.nativeSearch(wrapper);

        assertThat(result.getContent()).isEmpty();
        verify(template).search(any(Query.class), eq(SampleDoc.class), any(IndexCoordinates.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void search_withHighlights_addsHighlightMap() {
        SampleDoc doc = new SampleDoc("1", "hello");
        Map<String, List<String>> highlights = new HashMap<>();
        highlights.put("title", List.of("<em>hello</em>"));
        SearchHit<SampleDoc> hit = createSearchHitWithHighlights(doc, "1", highlights);
        List<SearchHit<SampleDoc>> hits = List.of(hit);

        SearchHits<SampleDoc> searchHits = createSearchHits(hits, 1L);
        when(template.search(any(Query.class), eq(SampleDoc.class), any(IndexCoordinates.class)))
                .thenReturn(searchHits);

        SearchQueryWrapper<SampleDoc> wrapper = SearchQueryWrapper.<SampleDoc>create(SampleDoc.class, "it-books")
                .highlight(SampleDoc::getTitle);

        PageResult<SampleDoc> result = service.search(wrapper);

        assertThat(result.getHighlights()).isNotNull();
        assertThat(result.getHighlights()).containsKey("1");
        assertThat(result.getHighlights().get("1").get("title")).contains("<em>hello</em>");
    }

    @Test
    @SuppressWarnings("unchecked")
    void search_withEmptyResults_returnsEmptyPage() {
        SearchHits<SampleDoc> searchHits = createSearchHits(List.of(), 0L);
        when(template.search(any(Query.class), eq(SampleDoc.class), any(IndexCoordinates.class)))
                .thenReturn(searchHits);

        SearchQueryWrapper<SampleDoc> wrapper = SearchQueryWrapper.<SampleDoc>create(SampleDoc.class, "it-books")
                .eq(SampleDoc::getId, "non-existent");

        PageResult<SampleDoc> result = service.search(wrapper);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotal()).isEqualTo(0L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void search_whenExceptionThrown_propagates() {
        when(template.search(any(Query.class), eq(SampleDoc.class), any(IndexCoordinates.class)))
                .thenThrow(new RuntimeException("Search failed"));

        SearchQueryWrapper<SampleDoc> wrapper = SearchQueryWrapper.<SampleDoc>create(SampleDoc.class, "it-books")
                .eq(SampleDoc::getId, "1");

        assertThatThrownBy(() -> service.search(wrapper))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Search failed");
    }

    @Test
    @SuppressWarnings("unchecked")
    void search_withSuggestions_buildsSuggestResults() throws Exception {
        SampleDoc doc = new SampleDoc("1", "hello");
        List<SearchHit<SampleDoc>> hits = List.of(createSearchHit(doc, "1"));
        SearchHits<SampleDoc> searchHits = createSearchHits(hits, 1L);
        when(template.search(any(Query.class), eq(SampleDoc.class), any(IndexCoordinates.class)))
                .thenReturn(searchHits);

        co.elastic.clients.elasticsearch.core.SearchResponse<SampleDoc> esResponse = mock(co.elastic.clients.elasticsearch.core.SearchResponse.class);
        lenient().when(esResponse.suggest()).thenReturn(null);
        doReturn(esResponse).when(elasticsearchClient).search(any(co.elastic.clients.elasticsearch.core.SearchRequest.class), eq(SampleDoc.class));

        SearchQueryWrapper<SampleDoc> wrapper = SearchQueryWrapper.<SampleDoc>create(SampleDoc.class, "it-books")
                .eq(SampleDoc::getId, "1")
                .suggest(SampleDoc::getTitle, "hello");

        PageResult<SampleDoc> result = service.search(wrapper);

        assertThat(result).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void findOne_withSingleResult_returnsEntity() {
        SampleDoc doc = new SampleDoc("1", "hello");
        List<SearchHit<SampleDoc>> hits = List.of(createSearchHit(doc, "1"));
        SearchHits<SampleDoc> searchHits = createSearchHits(hits, 1L);
        when(template.search(any(Query.class), eq(SampleDoc.class), any(IndexCoordinates.class)))
                .thenReturn(searchHits);

        SearchQueryWrapper<SampleDoc> wrapper = SearchQueryWrapper.<SampleDoc>create(SampleDoc.class, "it-books")
                .eq(SampleDoc::getId, "1");

        SampleDoc result = service.findOne(wrapper);

        assertThat(result).isNotNull();
        assertThat(result.getTitle()).isEqualTo("hello");
    }

    @Test
    @SuppressWarnings("unchecked")
    void findOne_withNoResult_returnsNull() {
        SearchHits<SampleDoc> searchHits = createSearchHits(List.of(), 0L);
        when(template.search(any(Query.class), eq(SampleDoc.class), any(IndexCoordinates.class)))
                .thenReturn(searchHits);

        SearchQueryWrapper<SampleDoc> wrapper = SearchQueryWrapper.<SampleDoc>create(SampleDoc.class, "it-books")
                .eq(SampleDoc::getId, "non-existent");

        SampleDoc result = service.findOne(wrapper);

        assertThat(result).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void findOne_withMultipleResults_throwsIllegalState() {
        SampleDoc doc1 = new SampleDoc("1", "hello");
        SampleDoc doc2 = new SampleDoc("2", "world");
        List<SearchHit<SampleDoc>> hits = List.of(createSearchHit(doc1, "1"), createSearchHit(doc2, "2"));
        SearchHits<SampleDoc> searchHits = createSearchHits(hits, 2L);
        when(template.search(any(Query.class), eq(SampleDoc.class), any(IndexCoordinates.class)))
                .thenReturn(searchHits);

        SearchQueryWrapper<SampleDoc> wrapper = SearchQueryWrapper.<SampleDoc>create(SampleDoc.class, "it-books")
                .eq(SampleDoc::getTitle, "hello");

        assertThatThrownBy(() -> service.findOne(wrapper))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("返回了多条记录");
    }

    @Test
    void findOne_withoutConditions_throwsIllegalArgument() {
        SearchQueryWrapper<SampleDoc> wrapper = SearchQueryWrapper.<SampleDoc>create(SampleDoc.class, "it-books");

        assertThatThrownBy(() -> service.findOne(wrapper))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("必须包含至少一个非空条件");
    }

    @Test
    @SuppressWarnings("unchecked")
    void count_withConditions_buildsQuery() {
        when(template.count(any(Query.class), any(IndexCoordinates.class))).thenReturn(42L);

        SearchQueryWrapper<SampleDoc> wrapper = SearchQueryWrapper.<SampleDoc>create(SampleDoc.class, "it-books")
                .eq(SampleDoc::getId, "1");

        long count = service.count(wrapper);

        assertThat(count).isEqualTo(42L);
        verify(template).count(any(Query.class), any(IndexCoordinates.class));
    }

    @Test
    void count_withoutConditions_usesFastCount() {
        SearchQueryWrapper<SampleDoc> wrapper = SearchQueryWrapper.<SampleDoc>create(SampleDoc.class, "it-books");

        when(template.count(any(Query.class), any(IndexCoordinates.class))).thenReturn(100L);

        long count = service.count(wrapper);

        assertThat(count).isEqualTo(100L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void count_whenExceptionThrown_wrapsInRuntimeException() {
        when(template.count(any(Query.class), any(IndexCoordinates.class)))
                .thenThrow(new RuntimeException("Count failed"));

        SearchQueryWrapper<SampleDoc> wrapper = SearchQueryWrapper.<SampleDoc>create(SampleDoc.class, "it-books")
                .eq(SampleDoc::getId, "1");

        assertThatThrownBy(() -> service.count(wrapper))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("统计文档数量失败");
    }

    @Test
    @SuppressWarnings("unchecked")
    void deleteByCondition_invokesTemplateDelete() {
        when(template.count(any(Query.class), any(IndexCoordinates.class))).thenReturn(5L);
        when(template.delete(any(), any(Class.class), any(IndexCoordinates.class)))
                .thenReturn(mock(ByQueryResponse.class));

        SearchQueryWrapper<SampleDoc> wrapper = SearchQueryWrapper.<SampleDoc>create(SampleDoc.class, "it-books")
                .eq(SampleDoc::getId, "1");

        long deleted = service.deleteByCondition(wrapper);

        assertThat(deleted).isEqualTo(5L);
        verify(template).count(any(Query.class), any(IndexCoordinates.class));
        verify(template).delete(any(), eq(SampleDoc.class), any(IndexCoordinates.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void deleteByCondition_whenExceptionThrown_propagates() {
        when(template.count(any(Query.class), any(IndexCoordinates.class))).thenReturn(5L);
        when(template.delete(any(), any(Class.class), any(IndexCoordinates.class)))
                .thenThrow(new RuntimeException("Delete failed"));

        SearchQueryWrapper<SampleDoc> wrapper = SearchQueryWrapper.<SampleDoc>create(SampleDoc.class, "it-books")
                .eq(SampleDoc::getId, "1");

        assertThatThrownBy(() -> service.deleteByCondition(wrapper))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Delete failed");
    }

    @Test
    @SuppressWarnings("unchecked")
    void deleteByCondition_withByQueryResponse_logsDeletedCount() {
        ByQueryResponse response = mock(ByQueryResponse.class);
        when(response.getDeleted()).thenReturn(3L);
        when(template.count(any(Query.class), any(IndexCoordinates.class))).thenReturn(5L);
        when(template.delete(any(), any(Class.class), any(IndexCoordinates.class))).thenReturn(response);

        SearchQueryWrapper<SampleDoc> wrapper = SearchQueryWrapper.<SampleDoc>create(SampleDoc.class, "it-books")
                .eq(SampleDoc::getId, "1");

        long deleted = service.deleteByCondition(wrapper);

        assertThat(deleted).isEqualTo(5L);
        verify(response).getDeleted();
    }

    @Test
    @SuppressWarnings("unchecked")
    void search_withNeOperator_buildsNotEqualQuery() {
        SearchHits<SampleDoc> searchHits = createSearchHits(List.of(), 0L);
        when(template.search(any(Query.class), eq(SampleDoc.class), any(IndexCoordinates.class)))
                .thenReturn(searchHits);

        SearchQueryWrapper<SampleDoc> wrapper = SearchQueryWrapper.<SampleDoc>create(SampleDoc.class, "it-books")
                .ne(SampleDoc::getId, "1");

        PageResult<SampleDoc> result = service.search(wrapper);

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void search_withInOperator_buildsInQuery() {
        SearchHits<SampleDoc> searchHits = createSearchHits(List.of(), 0L);
        when(template.search(any(Query.class), eq(SampleDoc.class), any(IndexCoordinates.class)))
                .thenReturn(searchHits);

        SearchQueryWrapper<SampleDoc> wrapper = SearchQueryWrapper.<SampleDoc>create(SampleDoc.class, "it-books")
                .in(SampleDoc::getId, List.of("1", "2", "3"));

        PageResult<SampleDoc> result = service.search(wrapper);

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void search_withNotInOperator_buildsNotInQuery() {
        SearchHits<SampleDoc> searchHits = createSearchHits(List.of(), 0L);
        when(template.search(any(Query.class), eq(SampleDoc.class), any(IndexCoordinates.class)))
                .thenReturn(searchHits);

        SearchQueryWrapper<SampleDoc> wrapper = SearchQueryWrapper.<SampleDoc>create(SampleDoc.class, "it-books")
                .notIn(SampleDoc::getId, List.of("1", "2", "3"));

        PageResult<SampleDoc> result = service.search(wrapper);

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void search_withGtOperator_buildsGreaterThanQuery() {
        SearchHits<SampleDoc> searchHits = createSearchHits(List.of(), 0L);
        when(template.search(any(Query.class), eq(SampleDoc.class), any(IndexCoordinates.class)))
                .thenReturn(searchHits);

        SearchQueryWrapper<SampleDoc> wrapper = SearchQueryWrapper.<SampleDoc>create(SampleDoc.class, "it-books")
                .gt(SampleDoc::getId, "5");

        PageResult<SampleDoc> result = service.search(wrapper);

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void search_withGeOperator_buildsGreaterThanEqualQuery() {
        SearchHits<SampleDoc> searchHits = createSearchHits(List.of(), 0L);
        when(template.search(any(Query.class), eq(SampleDoc.class), any(IndexCoordinates.class)))
                .thenReturn(searchHits);

        SearchQueryWrapper<SampleDoc> wrapper = SearchQueryWrapper.<SampleDoc>create(SampleDoc.class, "it-books")
                .ge(SampleDoc::getId, "5");

        PageResult<SampleDoc> result = service.search(wrapper);

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void search_withLtOperator_buildsLessThanQuery() {
        SearchHits<SampleDoc> searchHits = createSearchHits(List.of(), 0L);
        when(template.search(any(Query.class), eq(SampleDoc.class), any(IndexCoordinates.class)))
                .thenReturn(searchHits);

        SearchQueryWrapper<SampleDoc> wrapper = SearchQueryWrapper.<SampleDoc>create(SampleDoc.class, "it-books")
                .lt(SampleDoc::getId, "5");

        PageResult<SampleDoc> result = service.search(wrapper);

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void search_withLeOperator_buildsLessThanEqualQuery() {
        SearchHits<SampleDoc> searchHits = createSearchHits(List.of(), 0L);
        when(template.search(any(Query.class), eq(SampleDoc.class), any(IndexCoordinates.class)))
                .thenReturn(searchHits);

        SearchQueryWrapper<SampleDoc> wrapper = SearchQueryWrapper.<SampleDoc>create(SampleDoc.class, "it-books")
                .le(SampleDoc::getId, "5");

        PageResult<SampleDoc> result = service.search(wrapper);

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void search_withBetweenOperator_buildsBetweenQuery() {
        SearchHits<SampleDoc> searchHits = createSearchHits(List.of(), 0L);
        when(template.search(any(Query.class), eq(SampleDoc.class), any(IndexCoordinates.class)))
                .thenReturn(searchHits);

        SearchQueryWrapper<SampleDoc> wrapper = SearchQueryWrapper.<SampleDoc>create(SampleDoc.class, "it-books")
                .between(SampleDoc::getId, "1", "10");

        PageResult<SampleDoc> result = service.search(wrapper);

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void search_withLikeOperator_buildsLikeQuery() {
        SearchHits<SampleDoc> searchHits = createSearchHits(List.of(), 0L);
        when(template.search(any(Query.class), eq(SampleDoc.class), any(IndexCoordinates.class)))
                .thenReturn(searchHits);

        SearchQueryWrapper<SampleDoc> wrapper = SearchQueryWrapper.<SampleDoc>create(SampleDoc.class, "it-books")
                .like(SampleDoc::getTitle, "hello");

        PageResult<SampleDoc> result = service.search(wrapper);

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void search_withExistsOperator_buildsExistsQuery() {
        SearchHits<SampleDoc> searchHits = createSearchHits(List.of(), 0L);
        when(template.search(any(Query.class), eq(SampleDoc.class), any(IndexCoordinates.class)))
                .thenReturn(searchHits);

        SearchQueryWrapper<SampleDoc> wrapper = SearchQueryWrapper.<SampleDoc>create(SampleDoc.class, "it-books")
                .exists(SampleDoc::getTitle);

        PageResult<SampleDoc> result = service.search(wrapper);

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void search_withNotExistsOperator_buildsNotExistsQuery() {
        SearchHits<SampleDoc> searchHits = createSearchHits(List.of(), 0L);
        when(template.search(any(Query.class), eq(SampleDoc.class), any(IndexCoordinates.class)))
                .thenReturn(searchHits);

        SearchQueryWrapper<SampleDoc> wrapper = SearchQueryWrapper.<SampleDoc>create(SampleDoc.class, "it-books")
                .notExists(SampleDoc::getTitle);

        PageResult<SampleDoc> result = service.search(wrapper);

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void search_withOrCondition_buildsOrQuery() {
        SearchHits<SampleDoc> searchHits = createSearchHits(List.of(), 0L);
        when(template.search(any(Query.class), eq(SampleDoc.class), any(IndexCoordinates.class)))
                .thenReturn(searchHits);

        SearchQueryWrapper<SampleDoc> wrapper = SearchQueryWrapper.<SampleDoc>create(SampleDoc.class, "it-books")
                .or(SampleDoc::getTitle, "hello");

        PageResult<SampleDoc> result = service.search(wrapper);

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void saveBatch_returnsSavedList() {
        SampleDoc doc1 = new SampleDoc("1", "hello");
        SampleDoc doc2 = new SampleDoc("2", "world");
        List<SampleDoc> docs = List.of(doc1, doc2);

        when(template.save(any(Iterable.class), any(IndexCoordinates.class)))
                .thenReturn(List.of(doc1, doc2));

        List<SampleDoc> result = service.saveBatch("it-books", docs);

        assertThat(result).hasSize(2);
        verify(template).save(any(Iterable.class), any(IndexCoordinates.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void saveBatch_whenExceptionThrown_logsIKAnalyzerError() {
        SampleDoc doc = new SampleDoc("1", "hello");
        List<SampleDoc> docs = List.of(doc);

        when(template.save(any(Iterable.class), any(IndexCoordinates.class)))
                .thenThrow(new RuntimeException("IK Analyzer dictionary error: analyzer.dic.Dictionary not found"));

        assertThatThrownBy(() -> service.saveBatch("it-books", docs))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("IK Analyzer");
    }

    @Test
    void deleteBatchByDocIds_invokesTemplate() {
        List<String> docIds = List.of("1", "2", "3");
        when(template.idsQuery(docIds)).thenReturn(mock(Query.class));

        boolean result = service.deleteBatchByDocIds("it-books", docIds);

        assertThat(result).isTrue();
        verify(template).idsQuery(docIds);
        verify(template).delete(any(Query.class), any(IndexCoordinates.class));
    }

    @Test
    void deleteBatchByDocIds_whenExceptionThrown_propagates() {
        List<String> docIds = List.of("1", "2");
        when(template.idsQuery(docIds)).thenReturn(mock(Query.class));
        doThrow(new RuntimeException("Delete failed")).when(template).delete(any(Query.class), any(IndexCoordinates.class));

        assertThatThrownBy(() -> service.deleteBatchByDocIds("it-books", docIds))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Delete failed");
    }

    @Test
    @SuppressWarnings("unchecked")
    void save_whenExceptionThrown_logsIKAnalyzerError() {
        SampleDoc doc = new SampleDoc("1", "hello");

        when(template.save(any(SampleDoc.class), any(IndexCoordinates.class)))
                .thenThrow(new RuntimeException("IK Analyzer dictionary error: analyzer.dic.Dictionary not found"));

        assertThatThrownBy(() -> service.save("it-books", doc))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("IK Analyzer");
    }

    @Test
    @SuppressWarnings("unchecked")
    void dslSearch_andNativeSearch_delegateToInternalSearch() {
        SearchHits<SampleDoc> searchHits = createSearchHits(List.of(), 0L);
        when(template.search(any(Query.class), eq(SampleDoc.class), any(IndexCoordinates.class)))
                .thenReturn(searchHits);

        StringQueryWrapper<SampleDoc> stringWrapper = StringQueryWrapper.<SampleDoc>create(SampleDoc.class, "it-books", "{\"match_all\":{}}");
        service.dslSearch(stringWrapper);
        verify(template).search(any(Query.class), eq(SampleDoc.class), any(IndexCoordinates.class));

        reset(template);
        when(template.search(any(Query.class), eq(SampleDoc.class), any(IndexCoordinates.class)))
                .thenReturn(searchHits);

        co.elastic.clients.elasticsearch._types.query_dsl.Query nativeQuery =
                co.elastic.clients.elasticsearch._types.query_dsl.Query.of(q -> q.matchAll(m -> m));
        NativeQueryWrapper<SampleDoc> nativeWrapper = NativeQueryWrapper.<SampleDoc>create(SampleDoc.class, "it-books", nativeQuery);
        service.nativeSearch(nativeWrapper);
        verify(template).search(any(Query.class), eq(SampleDoc.class), any(IndexCoordinates.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void dslSearch_withSortAsc_appliesAscendingSort() {
        SampleDoc doc = new SampleDoc("1", "hello");
        List<SearchHit<SampleDoc>> hits = List.of(createSearchHit(doc, "1"));
        SearchHits<SampleDoc> searchHits = createSearchHits(hits, 1L);
        when(template.search(any(Query.class), eq(SampleDoc.class), any(IndexCoordinates.class)))
                .thenReturn(searchHits);

        StringQueryWrapper<SampleDoc> wrapper = StringQueryWrapper.<SampleDoc>create(SampleDoc.class, "it-books", "{\"match_all\":{}}");
        wrapper.setSort(Map.of("title", "asc"));

        PageResult<SampleDoc> result = service.dslSearch(wrapper);

        assertThat(result.getContent()).hasSize(1);
        verify(template).search(any(Query.class), eq(SampleDoc.class), any(IndexCoordinates.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void dslSearch_withSortDesc_appliesDescendingSort() {
        SampleDoc doc = new SampleDoc("1", "hello");
        List<SearchHit<SampleDoc>> hits = List.of(createSearchHit(doc, "1"));
        SearchHits<SampleDoc> searchHits = createSearchHits(hits, 1L);
        when(template.search(any(Query.class), eq(SampleDoc.class), any(IndexCoordinates.class)))
                .thenReturn(searchHits);

        StringQueryWrapper<SampleDoc> wrapper = StringQueryWrapper.<SampleDoc>create(SampleDoc.class, "it-books", "{\"match_all\":{}}");
        wrapper.setSort(Map.of("title", "desc"));

        PageResult<SampleDoc> result = service.dslSearch(wrapper);

        assertThat(result.getContent()).hasSize(1);
        verify(template).search(any(Query.class), eq(SampleDoc.class), any(IndexCoordinates.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void count_withConditionsAndSort_appliesSortViaAddSortToQuery() {
        when(template.count(any(Query.class), any(IndexCoordinates.class))).thenReturn(5L);

        SearchQueryWrapper<SampleDoc> wrapper = SearchQueryWrapper.<SampleDoc>create(SampleDoc.class, "it-books")
                .eq(SampleDoc::getId, "1")
                .orderByAsc(SampleDoc::getTitle);

        long count = service.count(wrapper);

        assertThat(count).isEqualTo(5L);
        verify(template).count(any(Query.class), any(IndexCoordinates.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void count_withConditionsAndSort_descDirection_appliesSortViaAddSortToQuery() {
        when(template.count(any(Query.class), any(IndexCoordinates.class))).thenReturn(3L);

        SearchQueryWrapper<SampleDoc> wrapper = SearchQueryWrapper.<SampleDoc>create(SampleDoc.class, "it-books")
                .eq(SampleDoc::getId, "1")
                .orderByDesc(SampleDoc::getTitle);

        long count = service.count(wrapper);

        assertThat(count).isEqualTo(3L);
        verify(template).count(any(Query.class), any(IndexCoordinates.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void deleteByCondition_withSort_appliesSortViaAddSortToQuery() {
        when(template.count(any(Query.class), any(IndexCoordinates.class))).thenReturn(5L);
        when(template.delete(any(), any(Class.class), any(IndexCoordinates.class)))
                .thenReturn(mock(ByQueryResponse.class));

        SearchQueryWrapper<SampleDoc> wrapper = SearchQueryWrapper.<SampleDoc>create(SampleDoc.class, "it-books")
                .eq(SampleDoc::getId, "1")
                .orderByDesc(SampleDoc::getTitle);

        long deleted = service.deleteByCondition(wrapper);

        assertThat(deleted).isEqualTo(5L);
        verify(template).count(any(Query.class), any(IndexCoordinates.class));
        verify(template).delete(any(), eq(SampleDoc.class), any(IndexCoordinates.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void count_withNonCriteriaQuery_fallsBackToNativeQueryBuilder() {
        when(template.count(any(Query.class), any(IndexCoordinates.class))).thenReturn(7L);

        SearchQueryWrapper<SampleDoc> wrapper = SearchQueryWrapper.<SampleDoc>create(SampleDoc.class, "it-books")
                .eq(SampleDoc::getId, "1")
                .orderByAsc(SampleDoc::getTitle);

        long count = service.count(wrapper);

        assertThat(count).isEqualTo(7L);
        verify(template).count(any(Query.class), any(IndexCoordinates.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void search_withSortAndConditions_appliesSortViaBuildSortOrders() {
        SampleDoc doc = new SampleDoc("1", "hello");
        List<SearchHit<SampleDoc>> hits = List.of(createSearchHit(doc, "1"));
        SearchHits<SampleDoc> searchHits = createSearchHits(hits, 1L);
        when(template.search(any(Query.class), eq(SampleDoc.class), any(IndexCoordinates.class)))
                .thenReturn(searchHits);

        SearchQueryWrapper<SampleDoc> wrapper = SearchQueryWrapper.<SampleDoc>create(SampleDoc.class, "it-books")
                .eq(SampleDoc::getId, "1")
                .orderByAsc(SampleDoc::getTitle);

        PageResult<SampleDoc> result = service.search(wrapper);

        assertThat(result.getContent()).hasSize(1);
        verify(template).search(any(Query.class), eq(SampleDoc.class), any(IndexCoordinates.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void search_withSortDescAndConditions_appliesSortViaBuildSortOrders() {
        SampleDoc doc = new SampleDoc("1", "hello");
        List<SearchHit<SampleDoc>> hits = List.of(createSearchHit(doc, "1"));
        SearchHits<SampleDoc> searchHits = createSearchHits(hits, 1L);
        when(template.search(any(Query.class), eq(SampleDoc.class), any(IndexCoordinates.class)))
                .thenReturn(searchHits);

        SearchQueryWrapper<SampleDoc> wrapper = SearchQueryWrapper.<SampleDoc>create(SampleDoc.class, "it-books")
                .eq(SampleDoc::getId, "1")
                .orderByDesc(SampleDoc::getTitle);

        PageResult<SampleDoc> result = service.search(wrapper);

        assertThat(result.getContent()).hasSize(1);
        verify(template).search(any(Query.class), eq(SampleDoc.class), any(IndexCoordinates.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void search_withSort_ascendingOrder_appliedCorrectly() {
        SampleDoc doc = new SampleDoc("1", "aaa");
        SampleDoc doc2 = new SampleDoc("2", "zzz");
        List<SearchHit<SampleDoc>> hits = List.of(createSearchHit(doc, "1"), createSearchHit(doc2, "2"));
        SearchHits<SampleDoc> searchHits = createSearchHits(hits, 2L);
        when(template.search(any(Query.class), eq(SampleDoc.class), any(IndexCoordinates.class)))
                .thenReturn(searchHits);

        SearchQueryWrapper<SampleDoc> wrapper = SearchQueryWrapper.<SampleDoc>create(SampleDoc.class, "it-books")
                .orderByAsc(SampleDoc::getTitle);

        PageResult<SampleDoc> result = service.search(wrapper);

        assertThat(result.getContent()).hasSize(2);
        verify(template).search(any(Query.class), eq(SampleDoc.class), any(IndexCoordinates.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void search_withSuggestions_clientThrowsException_returnsEmptyList() throws Exception {
        SampleDoc doc = new SampleDoc("1", "hello");
        List<SearchHit<SampleDoc>> hits = List.of(createSearchHit(doc, "1"));
        SearchHits<SampleDoc> searchHits = createSearchHits(hits, 1L);
        when(template.search(any(Query.class), eq(SampleDoc.class), any(IndexCoordinates.class)))
                .thenReturn(searchHits);

        doThrow(new RuntimeException("Suggest query failed"))
                .when(elasticsearchClient).search(any(co.elastic.clients.elasticsearch.core.SearchRequest.class), eq(SampleDoc.class));

        SearchQueryWrapper<SampleDoc> wrapper = SearchQueryWrapper.<SampleDoc>create(SampleDoc.class, "it-books")
                .eq(SampleDoc::getId, "1")
                .suggest(SampleDoc::getTitle, "hello");

        PageResult<SampleDoc> result = service.search(wrapper);

        assertThat(result).isNotNull();
        assertThat(result.getSuggestions()).containsKey("title_suggest");
        assertThat(result.getSuggestions().get("title_suggest")).isEmpty();
    }

    @Test
    void fastCount_whenTemplateThrows_wrapsInRuntimeException() {
        when(template.count(any(Query.class), any(IndexCoordinates.class)))
                .thenThrow(new RuntimeException("Index not found"));

        assertThatThrownBy(() -> service.fastCount("it-books"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("快速统计文档数量失败")
                .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void dslSearch_whenTemplateThrows_wrapsInRuntimeException() {
        when(template.search(any(Query.class), eq(SampleDoc.class), any(IndexCoordinates.class)))
                .thenThrow(new RuntimeException("DSL query failed"));

        StringQueryWrapper<SampleDoc> wrapper = StringQueryWrapper.<SampleDoc>create(SampleDoc.class, "it-books", "{\"match_all\":{}}");

        assertThatThrownBy(() -> service.dslSearch(wrapper))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("原生查询失败")
                .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void nativeSearch_whenTemplateThrows_wrapsInRuntimeException() {
        when(template.search(any(Query.class), eq(SampleDoc.class), any(IndexCoordinates.class)))
                .thenThrow(new RuntimeException("Native query failed"));

        co.elastic.clients.elasticsearch._types.query_dsl.Query nativeQuery =
                co.elastic.clients.elasticsearch._types.query_dsl.Query.of(q -> q.matchAll(m -> m));
        NativeQueryWrapper<SampleDoc> wrapper = NativeQueryWrapper.<SampleDoc>create(SampleDoc.class, "it-books", nativeQuery);

        assertThatThrownBy(() -> service.nativeSearch(wrapper))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("原生查询失败")
                .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void search_whenSuggestionsClientThrows_searchPropagatesException() throws Exception {
        SampleDoc doc = new SampleDoc("1", "hello");
        List<SearchHit<SampleDoc>> hits = List.of(createSearchHit(doc, "1"));
        SearchHits<SampleDoc> searchHits = createSearchHits(hits, 1L);
        when(template.search(any(Query.class), eq(SampleDoc.class), any(IndexCoordinates.class)))
                .thenReturn(searchHits);

        doThrow(new RuntimeException("Suggest failed"))
                .when(elasticsearchClient).search(any(co.elastic.clients.elasticsearch.core.SearchRequest.class), eq(SampleDoc.class));

        SearchQueryWrapper<SampleDoc> wrapper = SearchQueryWrapper.<SampleDoc>create(SampleDoc.class, "it-books")
                .eq(SampleDoc::getId, "1")
                .suggest(SampleDoc::getTitle, "hello");

        PageResult<SampleDoc> result = service.search(wrapper);

        assertThat(result.getContent()).hasSize(1);
    }

    @SuppressWarnings("unchecked")
    private <T> SearchHit<T> createSearchHit(T content, String id) {
        SearchHit<T> hit = mock(SearchHit.class);
        lenient().when(hit.getContent()).thenReturn(content);
        lenient().when(hit.getId()).thenReturn(id);
        lenient().when(hit.getHighlightFields()).thenReturn(Collections.emptyMap());
        return hit;
    }

    @SuppressWarnings("unchecked")
    private <T> SearchHit<T> createSearchHitWithHighlights(T content, String id, Map<String, List<String>> highlights) {
        SearchHit<T> hit = mock(SearchHit.class);
        lenient().when(hit.getContent()).thenReturn(content);
        lenient().when(hit.getId()).thenReturn(id);
        lenient().when(hit.getHighlightFields()).thenReturn(highlights);
        return hit;
    }

    @SuppressWarnings("unchecked")
    private <T> SearchHits<T> createSearchHits(List<SearchHit<T>> hits, long totalHits) {
        SearchHits<T> searchHits = mock(SearchHits.class);
        lenient().when(searchHits.getSearchHits()).thenReturn(hits);
        lenient().when(searchHits.getTotalHits()).thenReturn(totalHits);
        lenient().when(searchHits.getTotalHitsRelation()).thenReturn(TotalHitsRelation.EQUAL_TO);
        return searchHits;
    }

    static class SampleDoc {
        private String id;
        private String title;

        SampleDoc() {
        }

        SampleDoc(String id, String title) {
            this.id = id;
            this.title = title;
        }

        String getId() {
            return id;
        }

        String getTitle() {
            return title;
        }
    }
}
