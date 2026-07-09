/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.vector.service.impl;

import com.richie.component.vector.model.VectorDocument;
import com.richie.component.vector.model.VectorQuery;
import com.richie.component.vector.model.VectorSearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VectorServiceImplTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private EmbeddingModel embeddingModel;

    private TestVectorServiceImpl vectorService;

    @BeforeEach
    void setUp() {
        vectorService = new TestVectorServiceImpl(vectorStore, embeddingModel);
    }

    @Test
    void searchByVector_shouldUseVectorSearch() {
        float[] queryVector = new float[]{0.1f, 0.2f, 0.3f};
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of());

        vectorService.searchByVector("documents", queryVector, 10);

        verify(vectorStore).similaritySearch(any(SearchRequest.class));
        verifyNoInteractions(embeddingModel);
    }

    @Test
    void searchByText_shouldDelegateToVectorStore() {
        String queryText = "test query";
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of());

        vectorService.searchByText(queryText, 10);

        verify(vectorStore).similaritySearch(any(SearchRequest.class));
        verifyNoInteractions(embeddingModel);
    }

    @Test
    void addDocuments_shouldGenerateIdBeforeWrite() {
        VectorDocument docWithoutId = new VectorDocument();
        docWithoutId.setContent("test content");
        docWithoutId.setMetadata(Map.of());

        doNothing().when(vectorStore).add(any());

        List<String> ids = vectorService.addDocuments(List.of(docWithoutId));

        assertNotNull(ids);
        assertEquals(1, ids.size());
        assertNotNull(docWithoutId.getId());
        verify(vectorStore).add(any());
    }

    @Test
    void search_withNullQuery_shouldThrowException() {
        assertThrows(IllegalArgumentException.class, () -> vectorService.search(null));
    }

    @Test
    void search_withNegativeLimit_shouldThrowException() {
        VectorQuery query = new VectorQuery();
        query.setText("test");
        query.setLimit(-1);

        assertThrows(IllegalArgumentException.class, () -> vectorService.search(query));
    }

    @Test
    void search_withInvalidMinScore_shouldThrowException() {
        VectorQuery query = new VectorQuery();
        query.setText("test");
        query.setMinScore(1.5);

        assertThrows(IllegalArgumentException.class, () -> vectorService.search(query));
    }

    @Test
    void addDocument_withNullDocument_shouldThrowException() {
        assertThrows(IllegalArgumentException.class, () -> vectorService.addDocument(null));
    }

    @Test
    void addDocument_withBlankContent_shouldThrowException() {
        VectorDocument doc = new VectorDocument();
        doc.setContent("   ");

        assertThrows(IllegalArgumentException.class, () -> vectorService.addDocument(doc));
    }

    @Test
    void addDocuments_withNullList_shouldThrowException() {
        assertThrows(IllegalArgumentException.class, () -> vectorService.addDocuments(null));
    }

    @Test
    void addDocuments_withEmptyList_shouldThrowException() {
        assertThrows(IllegalArgumentException.class, () -> vectorService.addDocuments(List.of()));
    }

    @Test
    void deleteDocument_shouldDelegateToVectorStore() {
        vectorService.deleteDocument("id-1");
        verify(vectorStore).delete("id-1");
    }

    @Test
    void search_withValidQuery_returnsResults() {
        VectorQuery query = new VectorQuery();
        query.setText("hello");
        query.setLimit(5);
        org.springframework.ai.document.Document doc = org.mockito.Mockito.mock(org.springframework.ai.document.Document.class);
        when(doc.getId()).thenReturn("d1");
        when(doc.getFormattedContent()).thenReturn("hello");
        when(doc.getText()).thenReturn("hello");
        when(doc.getScore()).thenReturn(0.9);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));
        when(embeddingModel.embed("hello")).thenReturn(new float[]{0.1f});

        List<VectorSearchResult> results = vectorService.search(query);
        assertEquals(1, results.size());
        assertEquals("d1", results.getFirst().getId());
    }

    @Test
    void listDocuments_capsLimitAtMax() {
        assertNotNull(vectorService.listDocuments("idx", 0, 5000));
    }

    @Test
    void listDocuments_zeroLimit_returnsEmpty() {
        assertTrue(vectorService.listDocuments("idx", 0, 0).isEmpty());
    }

    @Test
    void updateDocument_shouldDeleteThenAdd() {
        VectorDocument doc = new VectorDocument();
        doc.setContent("updated");
        doc.setMetadata(Map.of());
        doNothing().when(vectorStore).delete("id-1");
        doNothing().when(vectorStore).add(any());
        vectorService.updateDocument("id-1", doc);
        assertEquals("id-1", doc.getId());
        verify(vectorStore).delete("id-1");
        verify(vectorStore).add(any());
    }

    @Test
    void deleteDocuments_shouldDelegateToVectorStore() {
        vectorService.deleteDocuments(List.of("a", "b"));
        verify(vectorStore).delete(List.of("a", "b"));
    }

    @Test
    void getDocument_whenFound_returnsMappedDocument() {
        org.springframework.ai.document.Document aiDoc =
                org.mockito.Mockito.mock(org.springframework.ai.document.Document.class);
        when(aiDoc.getId()).thenReturn("doc-1");
        when(aiDoc.getFormattedContent()).thenReturn("content");
        when(aiDoc.getMetadata()).thenReturn(Map.of("type", "note"));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(aiDoc));
        assertNotNull(vectorService.getDocument("doc-1"));
    }

    @Test
    void getDocument_whenMissing_returnsNull() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        assertNull(vectorService.getDocument("missing"));
    }

    @Test
    void addDocument_withMetadata_enrichesDocument() {
        VectorDocument doc = new VectorDocument();
        doc.setContent("body");
        doc.setMetadata(Map.of());
        doNothing().when(vectorStore).add(any());
        assertNotNull(vectorService.addDocument(doc));
    }

    @Test
    void search_blankText_throwsException() {
        VectorQuery query = new VectorQuery();
        query.setText("   ");
        assertThrows(IllegalArgumentException.class, () -> vectorService.search(query));
    }

    @Test
    void search_emptyResults_returnsEmptyList() {
        VectorQuery query = new VectorQuery();
        query.setText("none");
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        assertTrue(vectorService.search(query).isEmpty());
    }

    @Test
    void search_withoutEmbeddingModel_returnsNullVector() {
        TestVectorServiceImpl noEmbed = new TestVectorServiceImpl(vectorStore, null);
        VectorQuery query = new VectorQuery();
        query.setText("hello");
        org.springframework.ai.document.Document doc =
                org.mockito.Mockito.mock(org.springframework.ai.document.Document.class);
        when(doc.getId()).thenReturn("d1");
        when(doc.getFormattedContent()).thenReturn("hello");
        when(doc.getScore()).thenReturn(0.5);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));
        assertNull(noEmbed.search(query).getFirst().getVector());
    }

    @Test
    void createIndex_throwsUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class,
                () -> vectorService.createIndex("idx", new com.richie.component.vector.config.VectorProperties.IndexConfig()));
    }

    private static class TestVectorServiceImpl extends VectorServiceImpl {
        protected TestVectorServiceImpl(VectorStore vectorStore, EmbeddingModel embeddingModel) {
            super(vectorStore, embeddingModel);
        }

        @Override
        public List<VectorSearchResult> searchByVector(String indexName, float[] vector, int limit) {
            SearchRequest request = SearchRequest.builder()
                    .query("vector-search")
                    .topK(limit)
                    .build();
            return vectorStore.similaritySearch(request).stream()
                    .map(doc -> VectorSearchResult.of(doc.getId(), doc.getFormattedContent(), 0.0, null))
                    .toList();
        }

        @Override
        protected List<VectorDocument> listDocumentsHandler(String indexName, int offset, int limit) {
            return List.of();
        }
    }
}
