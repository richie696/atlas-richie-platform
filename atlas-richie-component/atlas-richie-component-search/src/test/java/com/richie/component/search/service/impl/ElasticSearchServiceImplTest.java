package com.richie.component.search.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
