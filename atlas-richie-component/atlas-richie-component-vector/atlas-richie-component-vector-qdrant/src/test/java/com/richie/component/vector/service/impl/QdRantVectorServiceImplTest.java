package com.richie.component.vector.service.impl;

import com.google.common.util.concurrent.ListenableFuture;
import com.richie.component.vector.model.VectorDocument;
import com.richie.component.vector.model.VectorSearchResult;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Common;
import io.qdrant.client.grpc.JsonWithInt;
import io.qdrant.client.grpc.Points;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QdRantVectorServiceImplTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private EmbeddingModel embeddingModel;

    @Mock
    private QdrantClient qdrantClient;

    private QdRantVectorServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new QdRantVectorServiceImpl(vectorStore, embeddingModel, qdrantClient);
    }

    @Test
    void createIndex_throwsUnsupportedOperationException() {
        UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class,
                () -> service.createIndex("idx",
                        new com.richie.component.vector.config.VectorProperties.IndexConfig()));
        assertEquals("qdrant不支持索引功能", ex.getMessage());
    }

    @Test
    void deleteIndex_throwsUnsupportedOperationException() {
        UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class,
                () -> service.deleteIndex("idx"));
        assertEquals("qdrant不支持索引功能", ex.getMessage());
    }

    @Test
    void indexExists_throwsUnsupportedOperationException() {
        UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class,
                () -> service.indexExists("idx"));
        assertEquals("qdrant不支持索引功能", ex.getMessage());
    }

    @Test
    void getIndexConfig_throwsUnsupportedOperationException() {
        UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class,
                () -> service.getIndexConfig("idx"));
        assertEquals("qdrant不支持索引功能", ex.getMessage());
    }

    @Test
    void countDocuments_returnsCountFromClient() throws Exception {
        ListenableFuture<Long> mockFuture = mock(ListenableFuture.class);
        when(qdrantClient.countAsync("test-collection")).thenReturn(mockFuture);
        doReturn(42L).when(mockFuture).get(3, TimeUnit.SECONDS);

        long count = service.countDocuments("test-collection");

        assertEquals(42L, count);
        verify(qdrantClient).countAsync("test-collection");
    }

    @Test
    void countDocuments_returnsZeroOnTimeout() throws Exception {
        ListenableFuture<Long> mockFuture = mock(ListenableFuture.class);
        when(qdrantClient.countAsync("test-collection")).thenReturn(mockFuture);
        doThrow(new java.util.concurrent.TimeoutException()).when(mockFuture).get(3, TimeUnit.SECONDS);

        long count = service.countDocuments("test-collection");

        assertEquals(0L, count);
    }

    @Test
    void searchByVector_returnsResults() throws Exception {
        Points.ScoredPoint mockPoint = mock(Points.ScoredPoint.class);
        when(mockPoint.getId()).thenReturn(Common.PointId.newBuilder().setNum(1).build());
        when(mockPoint.getScore()).thenReturn(0.95f);
        when(mockPoint.hasVectors()).thenReturn(true);

        Points.VectorOutput mockVectorOutput = mock(Points.VectorOutput.class);
        when(mockVectorOutput.getDataList()).thenReturn(List.of(0.1f, 0.2f, 0.3f));

        Points.VectorsOutput mockVectorsOutput = mock(Points.VectorsOutput.class);
        when(mockVectorsOutput.hasVector()).thenReturn(true);
        when(mockVectorsOutput.getVector()).thenReturn(mockVectorOutput);
        when(mockPoint.getVectors()).thenReturn(mockVectorsOutput);

        JsonWithInt.Value contentValue = JsonWithInt.Value.newBuilder().setStringValue("test content").build();
        when(mockPoint.getPayloadMap()).thenReturn(Map.of("content", contentValue));

        ListenableFuture<List<Points.ScoredPoint>> mockFuture = mock(ListenableFuture.class);
        when(qdrantClient.searchAsync(any())).thenReturn(mockFuture);
        doReturn(List.of(mockPoint)).when(mockFuture).get(anyLong(), any(TimeUnit.class));

        float[] queryVector = new float[]{0.1f, 0.2f, 0.3f};
        List<VectorSearchResult> results = service.searchByVector("test-collection", queryVector, 10);

        assertEquals(1, results.size());
        assertEquals("1", results.get(0).getId());
        assertEquals("test content", results.get(0).getContent());
        assertEquals(0.95, results.get(0).getScore(), 0.001);
    }

    @Test
    void searchByVector_returnsEmptyListOnException() throws Exception {
        ListenableFuture<List<Points.ScoredPoint>> mockFuture = mock(ListenableFuture.class);
        when(qdrantClient.searchAsync(any())).thenReturn(mockFuture);
        doThrow(new RuntimeException("search failed")).when(mockFuture).get(anyLong(), any(TimeUnit.class));

        float[] queryVector = new float[]{0.1f, 0.2f, 0.3f};
        List<VectorSearchResult> results = service.searchByVector("test-collection", queryVector, 10);

        assertTrue(results.isEmpty());
    }

    @Test
    void searchByVector_withEmptyResult() throws Exception {
        ListenableFuture<List<Points.ScoredPoint>> mockFuture = mock(ListenableFuture.class);
        when(qdrantClient.searchAsync(any())).thenReturn(mockFuture);
        doReturn(List.of()).when(mockFuture).get(anyLong(), any(TimeUnit.class));

        float[] queryVector = new float[]{0.1f};
        List<VectorSearchResult> results = service.searchByVector("test", queryVector, 5);

        assertTrue(results.isEmpty());
    }

    @Test
    void searchByVector_withNoVectors() throws Exception {
        Points.ScoredPoint mockPoint = mock(Points.ScoredPoint.class);
        when(mockPoint.getId()).thenReturn(Common.PointId.newBuilder().setNum(2).build());
        when(mockPoint.getScore()).thenReturn(0.8f);
        when(mockPoint.hasVectors()).thenReturn(false);
        when(mockPoint.getPayloadMap()).thenReturn(Map.of());

        ListenableFuture<List<Points.ScoredPoint>> mockFuture = mock(ListenableFuture.class);
        when(qdrantClient.searchAsync(any())).thenReturn(mockFuture);
        doReturn(List.of(mockPoint)).when(mockFuture).get(anyLong(), any(TimeUnit.class));

        float[] queryVector = new float[]{0.1f};
        List<VectorSearchResult> results = service.searchByVector("test", queryVector, 5);

        assertEquals(1, results.size());
        assertNull(results.get(0).getVector());
    }

    @SuppressWarnings("unchecked")
    private Points.ScrollResponse buildMockScrollResponse(List<Points.RetrievedPoint> points, boolean hasNext) {
        Points.ScrollResponse mockResponse = mock(Points.ScrollResponse.class);
        when(mockResponse.getResultList()).thenReturn(points);
        when(mockResponse.hasNextPageOffset()).thenReturn(hasNext);
        return mockResponse;
    }

    @SuppressWarnings("unchecked")
    private Points.RetrievedPoint buildMockRetrievedPoint(long numId, List<Float> vectorData, Map<String, String> payload) {
        Points.RetrievedPoint mockPoint = mock(Points.RetrievedPoint.class);
        when(mockPoint.getId()).thenReturn(Common.PointId.newBuilder().setNum(numId).build());

        if (vectorData != null) {
            Points.VectorOutput mockVectorOutput = mock(Points.VectorOutput.class);
            when(mockVectorOutput.getDataList()).thenReturn(vectorData);

            Points.VectorsOutput mockVectorsOutput = mock(Points.VectorsOutput.class);
            when(mockVectorsOutput.hasVector()).thenReturn(true);
            when(mockVectorsOutput.getVector()).thenReturn(mockVectorOutput);
            when(mockPoint.getVectors()).thenReturn(mockVectorsOutput);
        }

        if (payload != null) {
            Map<String, JsonWithInt.Value> payloadMap = new java.util.HashMap<>();
            for (Map.Entry<String, String> e : payload.entrySet()) {
                payloadMap.put(e.getKey(), JsonWithInt.Value.newBuilder().setStringValue(e.getValue()).build());
            }
            when(mockPoint.getPayloadMap()).thenReturn(payloadMap);
        }
        return mockPoint;
    }

    @Test
    void listDocumentsHandler_returnsDocuments() throws Exception {
        Points.RetrievedPoint mockPoint = buildMockRetrievedPoint(99, List.of(0.1f, 0.2f), Map.of("key1", "meta-data"));
        Points.ScrollResponse mockResponse = buildMockScrollResponse(List.of(mockPoint), false);

        ListenableFuture<Points.ScrollResponse> mockFuture = mock(ListenableFuture.class);
        doReturn(mockResponse).when(mockFuture).get(anyLong(), any(TimeUnit.class));
        when(qdrantClient.scrollAsync(any(Points.ScrollPoints.class))).thenReturn(mockFuture);

        List<VectorDocument> docs = service.listDocumentsHandler("test-collection", 0, 10);

        assertEquals(1, docs.size());
        assertEquals("99", docs.get(0).getId());
        assertNotNull(docs.get(0).getMetadata());
    }

    @Test
    void listDocumentsHandler_emptyCollection() throws Exception {
        Points.ScrollResponse mockResponse = buildMockScrollResponse(List.of(), false);

        ListenableFuture<Points.ScrollResponse> mockFuture = mock(ListenableFuture.class);
        doReturn(mockResponse).when(mockFuture).get(anyLong(), any(TimeUnit.class));
        when(qdrantClient.scrollAsync(any(Points.ScrollPoints.class))).thenReturn(mockFuture);

        List<VectorDocument> docs = service.listDocumentsHandler("empty-collection", 0, 10);

        assertTrue(docs.isEmpty());
    }

    @Test
    void listDocumentsHandler_respectsOffset() throws Exception {
        Points.RetrievedPoint mockPoint = buildMockRetrievedPoint(1, List.of(0.1f), Map.of());
        Points.ScrollResponse mockResponse = buildMockScrollResponse(List.of(mockPoint, mockPoint), false);

        ListenableFuture<Points.ScrollResponse> mockFuture = mock(ListenableFuture.class);
        doReturn(mockResponse).when(mockFuture).get(anyLong(), any(TimeUnit.class));
        when(qdrantClient.scrollAsync(any(Points.ScrollPoints.class))).thenReturn(mockFuture);

        List<VectorDocument> docs = service.listDocumentsHandler("test-collection", 1, 10);

        assertEquals(1, docs.size());
        verify(qdrantClient, atLeastOnce()).scrollAsync(any(Points.ScrollPoints.class));
    }

    @Test
    void listDocumentsHandler_throwsRuntimeExceptionOnTimeout() throws Exception {
        ListenableFuture<Points.ScrollResponse> mockFuture = mock(ListenableFuture.class);
        when(qdrantClient.scrollAsync(any())).thenReturn(mockFuture);
        doThrow(new java.util.concurrent.TimeoutException()).when(mockFuture).get(anyLong(), any(TimeUnit.class));

        assertThrows(RuntimeException.class,
                () -> service.listDocumentsHandler("test-collection", 0, 10));
    }
}
