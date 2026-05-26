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
    void searchByText_shouldUseEmbeddingModel() {
        String queryText = "test query";
        float[] embeddedVector = new float[]{0.1f, 0.2f, 0.3f};
        when(embeddingModel.embed(eq(queryText))).thenReturn(embeddedVector);
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of());

        vectorService.searchByText(queryText, 10);

        verify(embeddingModel).embed(eq(queryText));
        verify(vectorStore).similaritySearch(any(SearchRequest.class));
    }

    @Test
    void addDocuments_shouldGenerateIdBeforeWrite() {
        VectorDocument docWithoutId = new VectorDocument();
        docWithoutId.setContent("test content");

        doReturn(List.of("generated-id")).when(vectorStore).add(any());

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
