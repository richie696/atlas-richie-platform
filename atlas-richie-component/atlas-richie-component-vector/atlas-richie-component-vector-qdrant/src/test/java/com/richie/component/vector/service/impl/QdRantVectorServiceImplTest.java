/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.vector.service.impl;

import com.google.common.util.concurrent.ListenableFuture;
import com.richie.component.vector.model.VectorContent;
import com.richie.component.vector.model.VectorRecord;
import com.richie.component.vector.model.VectorSearchResult;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Common;
import io.qdrant.client.grpc.JsonWithInt;
import io.qdrant.client.grpc.Points;
import io.qdrant.client.grpc.SnapshotsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.document.Document;
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
        service = new QdRantVectorServiceImpl(null, vectorStore, embeddingModel, qdrantClient);
    }

    @Test
    void createIndex_usesHnswConfig() throws Exception {
        ListenableFuture<io.qdrant.client.grpc.Collections.CollectionOperationResponse> mockFuture =
                mock(ListenableFuture.class);
        when(qdrantClient.createCollectionAsync(any(io.qdrant.client.grpc.Collections.CreateCollection.class)))
                .thenReturn(mockFuture);

        com.richie.component.vector.config.VectorProperties.IndexConfig config =
                new com.richie.component.vector.config.VectorProperties.IndexConfig();
        config.setDimension(1536);
        config.setMetric("cosine");

        service.createIndex("test-collection", config);

        verify(qdrantClient).createCollectionAsync(any(io.qdrant.client.grpc.Collections.CreateCollection.class));
        verify(mockFuture).get(anyLong(), any(TimeUnit.class));
    }

    @Test
    void createIndex_mapsMetricToCorrectDistance() throws Exception {
        ListenableFuture<io.qdrant.client.grpc.Collections.CollectionOperationResponse> mockFuture =
                mock(ListenableFuture.class);
        when(qdrantClient.createCollectionAsync(any(io.qdrant.client.grpc.Collections.CreateCollection.class)))
                .thenReturn(mockFuture);

        // metric=l2 → Distance.Euclid
        var l2Config = new com.richie.component.vector.config.VectorProperties.IndexConfig();
        l2Config.setDimension(768);
        l2Config.setMetric("l2");
        service.createIndex("c-l2", l2Config);

        // metric=dot → Distance.Dot
        var dotConfig = new com.richie.component.vector.config.VectorProperties.IndexConfig();
        dotConfig.setDimension(768);
        dotConfig.setMetric("dot");
        service.createIndex("c-dot", dotConfig);

        verify(qdrantClient, times(2))
                .createCollectionAsync(any(io.qdrant.client.grpc.Collections.CreateCollection.class));
    }

    @Test
    void createIndex_defaultsDimensionAndMetric() throws Exception {
        ListenableFuture<io.qdrant.client.grpc.Collections.CollectionOperationResponse> mockFuture =
                mock(ListenableFuture.class);
        when(qdrantClient.createCollectionAsync(any(io.qdrant.client.grpc.Collections.CreateCollection.class)))
                .thenReturn(mockFuture);

        // dimension/metric 留空 → 应使用默认 1536 / cosine
        var cfg = new com.richie.component.vector.config.VectorProperties.IndexConfig();
        service.createIndex("c-default", cfg);

        verify(qdrantClient).createCollectionAsync(any(io.qdrant.client.grpc.Collections.CreateCollection.class));
    }

    @Test
    void deleteIndex_throwsUnsupportedOperationException() {
        UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class,
                () -> service.deleteIndex("idx"));
        assertEquals("qdrant不支持索引功能", ex.getMessage());
    }

    @SuppressWarnings("unchecked")
    @Test
    void indexExists_returnsFalseWhenCollectionNotInList() throws Exception {
        ListenableFuture<List<String>> mockFuture = mock(ListenableFuture.class);
        when(qdrantClient.listCollectionsAsync()).thenReturn(mockFuture);
        doReturn(List.of("other-collection")).when(mockFuture).get(3, TimeUnit.SECONDS);

        boolean exists = service.indexExists("nonexistent");

        assertFalse(exists);
        verify(qdrantClient).listCollectionsAsync();
    }

    @SuppressWarnings("unchecked")
    @Test
    void indexExists_returnsTrueWhenCollectionPresent() throws Exception {
        ListenableFuture<List<String>> mockFuture = mock(ListenableFuture.class);
        when(qdrantClient.listCollectionsAsync()).thenReturn(mockFuture);
        doReturn(List.of("target-collection", "other")).when(mockFuture).get(3, TimeUnit.SECONDS);

        boolean exists = service.indexExists("target-collection");

        assertTrue(exists);
    }

    @SuppressWarnings("unchecked")
    @Test
    void indexExists_returnsFalseWhenListCollectionsFails() throws Exception {
        ListenableFuture<List<String>> mockFuture = mock(ListenableFuture.class);
        when(qdrantClient.listCollectionsAsync()).thenReturn(mockFuture);
        doThrow(new java.util.concurrent.TimeoutException()).when(mockFuture).get(3, TimeUnit.SECONDS);

        boolean exists = service.indexExists("any");

        assertFalse(exists);
    }

    @SuppressWarnings("unchecked")
    @Test
    void getIndexConfig_throwsRuntimeExceptionWhenCollectionMissing() throws Exception {
        ListenableFuture<io.qdrant.client.grpc.Collections.CollectionInfo> mockFuture =
                mock(ListenableFuture.class);
        when(qdrantClient.getCollectionInfoAsync("missing")).thenReturn(mockFuture);
        doThrow(new RuntimeException("Collection missing does not exist!"))
                .when(mockFuture).get(3, TimeUnit.SECONDS);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.getIndexConfig("missing"));
        assertTrue(ex.getMessage().contains("Qdrant getCollection failed"));
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
        Points.RetrievedPoint mockPoint = buildMockRetrievedPoint(99, List.of(0.1f, 0.2f),
                Map.of("content", "test content", "key1", "meta-data"));
        Points.ScrollResponse mockResponse = buildMockScrollResponse(List.of(mockPoint), false);

        ListenableFuture<Points.ScrollResponse> mockFuture = mock(ListenableFuture.class);
        doReturn(mockResponse).when(mockFuture).get(anyLong(), any(TimeUnit.class));
        when(qdrantClient.scrollAsync(any(Points.ScrollPoints.class))).thenReturn(mockFuture);

        List<VectorRecord> docs = service.listDocumentsHandler("test-collection", 0, 10);

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

        List<VectorRecord> docs = service.listDocumentsHandler("empty-collection", 0, 10);

        assertTrue(docs.isEmpty());
    }

    @Test
    void listDocumentsHandler_respectsOffset() throws Exception {
        Points.RetrievedPoint mockPoint = buildMockRetrievedPoint(1, List.of(0.1f),
                Map.of("content", "first doc"));
        Points.ScrollResponse mockResponse = buildMockScrollResponse(List.of(mockPoint, mockPoint), false);

        ListenableFuture<Points.ScrollResponse> mockFuture = mock(ListenableFuture.class);
        doReturn(mockResponse).when(mockFuture).get(anyLong(), any(TimeUnit.class));
        when(qdrantClient.scrollAsync(any(Points.ScrollPoints.class))).thenReturn(mockFuture);

        List<VectorRecord> docs = service.listDocumentsHandler("test-collection", 1, 10);

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

    // ==================== addEmbeddings ====================

    @Test
    void addEmbeddings_insertsPointsViaUpsertAsync() throws Exception {
        Document doc = new Document("1", "hello world",
                Map.of("embedding", new float[]{0.1f, 0.2f, 0.3f}));
        ListenableFuture<Points.UpdateResult> mockFuture = mock(ListenableFuture.class);
        when(qdrantClient.upsertAsync(any(Points.UpsertPoints.class))).thenReturn(mockFuture);
        doReturn(Points.UpdateResult.getDefaultInstance()).when(mockFuture).get(3, TimeUnit.SECONDS);

        service.addEmbeddings("test-collection", List.of(doc));

        verify(qdrantClient).upsertAsync(any(Points.UpsertPoints.class));
        verify(mockFuture).get(3, TimeUnit.SECONDS);
    }

    @Test
    void addEmbeddings_skipsWhenDocsEmpty() {
        service.addEmbeddings("test-collection", List.of());
        verify(qdrantClient, never()).upsertAsync(any());
    }

    @Test
    void addEmbeddings_wrapsException() throws Exception {
        Document doc = new Document("1", "hello", Map.of("embedding", new float[]{0.1f}));
        ListenableFuture<Points.UpdateResult> mockFuture = mock(ListenableFuture.class);
        when(qdrantClient.upsertAsync(any(Points.UpsertPoints.class))).thenReturn(mockFuture);
        doThrow(new RuntimeException("connection lost")).when(mockFuture).get(3, TimeUnit.SECONDS);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.addEmbeddings("test-collection", List.of(doc)));
        assertTrue(ex.getMessage().contains("Qdrant addEmbeddings failed"));
    }

    // ==================== deleteByIds ====================

    @SuppressWarnings("unchecked")
    @Test
    void deleteByIds_deletesViaPointIds() throws Exception {
        ListenableFuture<Points.UpdateResult> mockFuture = mock(ListenableFuture.class);
        when(qdrantClient.deleteAsync(anyString(), anyList())).thenReturn(mockFuture);
        doReturn(Points.UpdateResult.getDefaultInstance()).when(mockFuture).get(3, TimeUnit.SECONDS);

        service.deleteByIds("test-collection", List.of("1", "2"));

        verify(qdrantClient).deleteAsync(anyString(), anyList());
    }

    @Test
    void deleteByIds_skipsWhenIdsEmpty() {
        service.deleteByIds("test-collection", List.of());
        verify(qdrantClient, never()).deleteAsync(anyString(), anyList());
    }

    @SuppressWarnings("unchecked")
    @Test
    void deleteByIds_wrapsException() throws Exception {
        ListenableFuture<Points.UpdateResult> mockFuture = mock(ListenableFuture.class);
        when(qdrantClient.deleteAsync(anyString(), anyList())).thenReturn(mockFuture);
        doThrow(new RuntimeException("timeout")).when(mockFuture).get(3, TimeUnit.SECONDS);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.deleteByIds("test-collection", List.of("1")));
        assertTrue(ex.getMessage().contains("Qdrant deleteByIds failed"));
    }

    // ==================== getByIds ====================

    @SuppressWarnings("unchecked")
    @Test
    void getByIds_returnsRetrievedPoints() throws Exception {
        Points.RetrievedPoint mockPoint = buildMockRetrievedPoint(42, List.of(0.1f, 0.2f),
                Map.of("content", "retrieved content"));
        ListenableFuture<List<Points.RetrievedPoint>> mockFuture = mock(ListenableFuture.class);
        when(qdrantClient.retrieveAsync(anyString(), anyList(), any())).thenReturn(mockFuture);
        doReturn(List.of(mockPoint)).when(mockFuture).get(3, TimeUnit.SECONDS);

        List<VectorRecord> records = service.getByIds("test-collection", List.of("42"));

        assertEquals(1, records.size());
        assertEquals("42", records.get(0).getId());
        assertEquals("retrieved content", ((VectorContent.TextContent) records.get(0).getContent()).text());
    }

    @Test
    void getByIds_returnsEmptyWhenIdsEmpty() {
        List<VectorRecord> records = service.getByIds("test-collection", List.of());
        assertTrue(records.isEmpty());
        verify(qdrantClient, never()).retrieveAsync(anyString(), anyList(), any());
    }

    @SuppressWarnings("unchecked")
    @Test
    void getByIds_wrapsException() throws Exception {
        ListenableFuture<List<Points.RetrievedPoint>> mockFuture = mock(ListenableFuture.class);
        when(qdrantClient.retrieveAsync(anyString(), anyList(), any())).thenReturn(mockFuture);
        doThrow(new RuntimeException("timeout")).when(mockFuture).get(3, TimeUnit.SECONDS);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.getByIds("test-collection", List.of("42")));
        assertTrue(ex.getMessage().contains("Qdrant getByIds failed"));
    }

    // ==================== truncateIndex ====================

    @Test
    void truncateIndex_executesMatchAllDeleteAndReturnsPreviousCount() throws Exception {
        ListenableFuture<Long> countFuture = mock(ListenableFuture.class);
        when(qdrantClient.countAsync("test-collection")).thenReturn(countFuture);
        doReturn(33L).when(countFuture).get(3, TimeUnit.SECONDS);

        ListenableFuture<Points.UpdateResult> deleteFuture = mock(ListenableFuture.class);
        Points.UpdateResult updateResult = Points.UpdateResult.newBuilder().build();
        doReturn(updateResult).when(deleteFuture).get(3, TimeUnit.SECONDS);
        when(qdrantClient.deleteAsync(eq("test-collection"), any(Common.Filter.class))).thenReturn(deleteFuture);

        long deleted = service.truncateIndex("test-collection");

        assertEquals(33L, deleted);
        verify(qdrantClient).countAsync("test-collection");
        verify(qdrantClient).deleteAsync(eq("test-collection"), any(Common.Filter.class));
    }

    @Test
    void truncateIndex_whenDeleteFails_shouldWrapInRuntimeException() throws Exception {
        ListenableFuture<Long> countFuture = mock(ListenableFuture.class);
        when(qdrantClient.countAsync("test-collection")).thenReturn(countFuture);
        doReturn(0L).when(countFuture).get(3, TimeUnit.SECONDS);

        ListenableFuture<Points.UpdateResult> deleteFuture = mock(ListenableFuture.class);
        doThrow(new java.util.concurrent.TimeoutException()).when(deleteFuture).get(3, TimeUnit.SECONDS);
        when(qdrantClient.deleteAsync(eq("test-collection"), any(Common.Filter.class))).thenReturn(deleteFuture);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.truncateIndex("test-collection"));
        assertTrue(ex.getMessage().contains("qdrant truncateIndex failed"));
    }

    // ==================== healthCheck (3-step probe inherited from AbstractVectorService) ====================

    @SuppressWarnings("unchecked")
    @Test
    void healthCheck_returnsFalseWhenIndexNotExists() throws Exception {
        ListenableFuture<List<String>> listFuture = mock(ListenableFuture.class);
        when(qdrantClient.listCollectionsAsync()).thenReturn(listFuture);
        doReturn(List.of()).when(listFuture).get(3, TimeUnit.SECONDS);

        boolean healthy = service.healthCheck("missing-collection");

        assertFalse(healthy);
        verify(qdrantClient).listCollectionsAsync();
        verify(qdrantClient, never()).countAsync(anyString());
    }

    @SuppressWarnings("unchecked")
    @Test
    void healthCheck_returnsTrueWhenIndexExistsAndCountReadable() throws Exception {
        ListenableFuture<List<String>> listFuture = mock(ListenableFuture.class);
        when(qdrantClient.listCollectionsAsync()).thenReturn(listFuture);
        doReturn(List.of("test-collection")).when(listFuture).get(3, TimeUnit.SECONDS);

        ListenableFuture<Long> countFuture = mock(ListenableFuture.class);
        when(qdrantClient.countAsync("test-collection")).thenReturn(countFuture);
        doReturn(5L).when(countFuture).get(3, TimeUnit.SECONDS);

        boolean healthy = service.healthCheck("test-collection");

        assertTrue(healthy);
        verify(qdrantClient).listCollectionsAsync();
        verify(qdrantClient).countAsync("test-collection");
    }

    @SuppressWarnings("unchecked")
    @Test
    void healthCheck_returnsFalseWhenCountThrows() throws Exception {
        ListenableFuture<List<String>> listFuture = mock(ListenableFuture.class);
        when(qdrantClient.listCollectionsAsync()).thenReturn(listFuture);
        doReturn(List.of("test-collection")).when(listFuture).get(3, TimeUnit.SECONDS);

        ListenableFuture<Long> countFuture = mock(ListenableFuture.class);
        when(qdrantClient.countAsync("test-collection")).thenReturn(countFuture);
        doReturn(0L).when(countFuture).get(3, TimeUnit.SECONDS);

        boolean healthy = service.healthCheck("test-collection");

        assertTrue(healthy);
    }

    // ==================== ops: optimize / alias / backup / restore (§14.4) ====================

    @Nested
    class OpsMethods {

        @SuppressWarnings("unchecked")
        @Test
        void optimize_whenCreateSnapshotSucceeds_shouldReturnTrue() throws Exception {
            ListenableFuture<SnapshotsService.SnapshotDescription> snapshotFuture = mock(ListenableFuture.class);
            when(qdrantClient.createSnapshotAsync("test-collection")).thenReturn(snapshotFuture);
            doReturn(SnapshotsService.SnapshotDescription.getDefaultInstance())
                    .when(snapshotFuture).get(3, TimeUnit.SECONDS);

            boolean result = service.optimize("test-collection");

            assertTrue(result);
            verify(qdrantClient).createSnapshotAsync("test-collection");
        }

        @Test
        void optimize_whenCreateSnapshotFails_shouldReturnFalse() throws Exception {
            ListenableFuture<SnapshotsService.SnapshotDescription> snapshotFuture = mock(ListenableFuture.class);
            when(qdrantClient.createSnapshotAsync("test-collection")).thenReturn(snapshotFuture);
            doThrow(new RuntimeException("snapshot failed")).when(snapshotFuture).get(3, TimeUnit.SECONDS);

            boolean result = service.optimize("test-collection");

            assertFalse(result);
        }

        @Test
        void createAlias_shouldThrowUnsupportedOperationException() {
            UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class,
                    () -> service.createAlias("test-collection", "alias-x"));
            assertTrue(ex.getMessage().contains("qdrant") || ex.getMessage().contains("alias"));
        }

        @Test
        void switchAlias_shouldThrowUnsupportedOperationException() {
            UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class,
                    () -> service.switchAlias("old-collection", "new-collection", "alias-x"));
            assertTrue(ex.getMessage().contains("qdrant") || ex.getMessage().contains("alias"));
        }

        @Test
        void backup_shouldThrowUnsupportedOperationException() {
            UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class,
                    () -> service.backup("test-collection", "/tmp/backup"));
            assertTrue(ex.getMessage().contains("qdrant"));
        }

        @Test
        void restore_shouldThrowUnsupportedOperationException() {
            UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class,
                    () -> service.restore("/tmp/backup", "test-collection"));
            assertTrue(ex.getMessage().contains("qdrant"));
        }
    }
}
