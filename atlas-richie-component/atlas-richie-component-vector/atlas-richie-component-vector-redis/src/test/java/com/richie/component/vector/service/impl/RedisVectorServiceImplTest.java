package com.richie.component.vector.service.impl;

import com.richie.component.vector.config.VectorProperties;
import com.richie.component.vector.model.VectorDocument;
import com.richie.component.vector.model.VectorSearchResult;
import org.json.JSONArray;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;
import redis.clients.jedis.search.FTCreateParams;
import redis.clients.jedis.search.Query;
import redis.clients.jedis.search.SearchResult;
import redis.clients.jedis.json.Path2;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RedisVectorServiceImplTest {

    @Mock
    private RedisVectorStore redisVectorStore;

    @Mock
    private EmbeddingModel embeddingModel;

    @Mock
    private RedisClient jedisPooled;

    private RedisVectorServiceImpl vectorService;

    @BeforeEach
    void setUp() {
        when(redisVectorStore.getNativeClient()).thenReturn(Optional.of(jedisPooled));
        vectorService = new RedisVectorServiceImpl(redisVectorStore, embeddingModel);
    }

    // ==================== afterPropertiesSet / checkRedisStackAvailability ====================

    @Test
    void afterPropertiesSet_whenRediSearchAvailable_shouldNotThrow() {
        when(jedisPooled.ftList()).thenReturn(Set.of("idx1", "idx2"));
        assertDoesNotThrow(() -> vectorService.afterPropertiesSet());
    }

    @Test
    void afterPropertiesSet_whenNotRedisVectorStore_shouldReturnEarly() {
        VectorStore nonRedisStore = mock(VectorStore.class);
        RedisVectorServiceImpl otherService = new RedisVectorServiceImpl(nonRedisStore, embeddingModel);
        assertDoesNotThrow(otherService::afterPropertiesSet);
    }

    @Test
    void afterPropertiesSet_whenNativeClientEmpty_shouldThrow() {
        when(redisVectorStore.getNativeClient()).thenReturn(Optional.empty());
        RedisVectorServiceImpl otherService = new RedisVectorServiceImpl(redisVectorStore, embeddingModel);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                otherService::afterPropertiesSet);
        assertTrue(ex.getMessage().contains("无法获取Jedis客户端"));
    }

    @Test
    void afterPropertiesSet_whenRediSearchNotAvailable_shouldThrow() {
        when(jedisPooled.ftList()).thenThrow(new RuntimeException("ERR unknown command"));
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> vectorService.afterPropertiesSet());
        assertTrue(ex.getMessage().contains("RediSearch模块未检测到"));
    }

    // ==================== searchByVector ====================

    @Test
    void searchByVector_whenNotRedisVectorStore_shouldThrow() {
        VectorStore nonRedisStore = mock(VectorStore.class);
        RedisVectorServiceImpl otherService = new RedisVectorServiceImpl(nonRedisStore, embeddingModel);
        assertThrows(UnsupportedOperationException.class,
                () -> otherService.searchByVector("idx", new float[]{0.1f, 0.2f}, 10));
    }

    @Test
    void searchByVector_whenNativeClientEmpty_shouldThrow() {
        when(redisVectorStore.getNativeClient()).thenReturn(Optional.empty());
        assertThrows(IllegalStateException.class,
                () -> vectorService.searchByVector("idx", new float[]{0.1f, 0.2f}, 10));
    }

    @Test
    void searchByVector_shouldReturnKnnResultsWithDistanceConvertedToSimilarity() {
        redis.clients.jedis.search.Document doc1 = mock(redis.clients.jedis.search.Document.class);
        when(doc1.getId()).thenReturn("doc:1");
        when(doc1.getString("score")).thenReturn("0.25");
        when(doc1.getString("content")).thenReturn("hello world");
        when(doc1.hasProperty("score")).thenReturn(true);
        when(doc1.hasProperty("content")).thenReturn(true);

        SearchResult searchResult = mock(SearchResult.class);
        when(searchResult.getDocuments()).thenReturn(List.of(doc1));
        when(jedisPooled.ftSearch(eq("test-index"), any(Query.class))).thenReturn(searchResult);

        float[] queryVector = new float[]{0.1f, 0.2f, 0.3f, 0.4f};
        List<VectorSearchResult> results = vectorService.searchByVector("test-index", queryVector, 10);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getId()).isEqualTo("doc:1");
        assertThat(results.getFirst().getContent()).isEqualTo("hello world");
        assertThat(results.getFirst().getScore()).isEqualTo(0.75);
    }

    @Test
    void searchByVector_withInvalidScore_shouldTreatAsZeroDistance() {
        redis.clients.jedis.search.Document doc = mock(redis.clients.jedis.search.Document.class);
        when(doc.getId()).thenReturn("doc:x");
        when(doc.getString("score")).thenReturn("not-a-number");
        when(doc.hasProperty("score")).thenReturn(true);
        when(doc.hasProperty("content")).thenReturn(false);

        SearchResult searchResult = mock(SearchResult.class);
        when(searchResult.getDocuments()).thenReturn(List.of(doc));
        when(jedisPooled.ftSearch(eq("idx"), any(Query.class))).thenReturn(searchResult);

        List<VectorSearchResult> results = vectorService.searchByVector("idx", new float[]{0.1f}, 5);

        assertThat(results.getFirst().getScore()).isEqualTo(1.0);
    }

    @Test
    void searchByVector_whenFtSearchThrows_shouldWrapInRuntimeException() {
        when(jedisPooled.ftSearch(anyString(), any(Query.class)))
                .thenThrow(new RuntimeException("FT.SEARCH failed"));
        assertThrows(RuntimeException.class,
                () -> vectorService.searchByVector("idx", new float[]{0.1f}, 5));
    }

    // ==================== createIndex ====================

    @Test
    void createIndex_withHnswAndCosineMetric_shouldCreateSuccessfully() {
        when(jedisPooled.ftCreate(anyString(), any(FTCreateParams.class), isA(Iterable.class)))
                .thenReturn("OK");

        VectorProperties.IndexConfig config = new VectorProperties.IndexConfig();
        config.setDimension(1536);
        config.setMetric("cosine");
        config.setIndexType("hnsw");

        assertDoesNotThrow(() -> vectorService.createIndex("my-index", config));
        verify(jedisPooled).ftCreate(eq("my-index"), any(FTCreateParams.class), isA(Iterable.class));
    }

    @Test
    void createIndex_withEuclideanMetric_shouldUseL2DistanceMetric() {
        when(jedisPooled.ftCreate(anyString(), any(FTCreateParams.class), isA(Iterable.class))).thenReturn("OK");

        VectorProperties.IndexConfig config = new VectorProperties.IndexConfig();
        config.setMetric("euclidean");
        config.setIndexType("hnsw");

        assertDoesNotThrow(() -> vectorService.createIndex("euclid-index", config));
        verify(jedisPooled).ftCreate(eq("euclid-index"), any(FTCreateParams.class), isA(Iterable.class));
    }

    @Test
    void createIndex_withDotProductMetric_shouldUseIPDistanceMetric() {
        when(jedisPooled.ftCreate(anyString(), any(FTCreateParams.class), isA(Iterable.class))).thenReturn("OK");

        VectorProperties.IndexConfig config = new VectorProperties.IndexConfig();
        config.setMetric("dot");
        config.setIndexType("hnsw");

        assertDoesNotThrow(() -> vectorService.createIndex("dot-index", config));
        verify(jedisPooled).ftCreate(eq("dot-index"), any(FTCreateParams.class), isA(Iterable.class));
    }

    @Test
    void createIndex_withFlatIndexType_shouldUseFlatAlgorithm() {
        when(jedisPooled.ftCreate(anyString(), any(FTCreateParams.class), isA(Iterable.class))).thenReturn("OK");

        VectorProperties.IndexConfig config = new VectorProperties.IndexConfig();
        config.setIndexType("flat");

        assertDoesNotThrow(() -> vectorService.createIndex("flat-index", config));
        verify(jedisPooled).ftCreate(eq("flat-index"), any(FTCreateParams.class), isA(Iterable.class));
    }

    @Test
    void createIndex_withHnswParams_shouldPassMAndEfConstruction() {
        when(jedisPooled.ftCreate(anyString(), any(FTCreateParams.class), isA(Iterable.class))).thenReturn("OK");

        VectorProperties.IndexConfig config = new VectorProperties.IndexConfig();
        config.setIndexType("hnsw");
        config.setIndexParams(Map.of("M", 16, "efConstruction", 200, "efRuntime", 100));

        assertDoesNotThrow(() -> vectorService.createIndex("hnsw-params", config));
        verify(jedisPooled).ftCreate(eq("hnsw-params"), any(FTCreateParams.class), isA(Iterable.class));
    }

    @Test
    void createIndex_withAdditionalFields_shouldIncludeNumericAndTagFields() {
        when(jedisPooled.ftCreate(anyString(), any(FTCreateParams.class), isA(Iterable.class))).thenReturn("OK");

        VectorProperties.IndexConfig config = new VectorProperties.IndexConfig();
        config.setAdditionalFields(Map.of(
                "age", 25,
                "category", "tech"
        ));

        assertDoesNotThrow(() -> vectorService.createIndex("with-fields", config));
        verify(jedisPooled).ftCreate(eq("with-fields"), any(FTCreateParams.class), isA(Iterable.class));
    }

    @Test
    void createIndex_whenResponseNotOk_shouldThrow() {
        when(jedisPooled.ftCreate(anyString(), any(FTCreateParams.class), isA(Iterable.class))).thenReturn("ERR");

        VectorProperties.IndexConfig config = new VectorProperties.IndexConfig();

        assertThrows(RuntimeException.class,
                () -> vectorService.createIndex("bad-index", config));
    }

    @Test
    void createIndex_whenIndexAlreadyExists_shouldWarnAndNotThrow() {
        when(jedisPooled.ftCreate(anyString(), any(FTCreateParams.class), isA(Iterable.class)))
                .thenThrow(new RuntimeException("Index already exists"));

        VectorProperties.IndexConfig config = new VectorProperties.IndexConfig();

        assertDoesNotThrow(() -> vectorService.createIndex("existing-index", config));
    }

    @Test
    void createIndex_whenNotRedisVectorStore_shouldThrow() {
        VectorStore nonRedisStore = mock(VectorStore.class);
        RedisVectorServiceImpl otherService = new RedisVectorServiceImpl(nonRedisStore, embeddingModel);

        assertThrows(UnsupportedOperationException.class,
                () -> otherService.createIndex("idx", new VectorProperties.IndexConfig()));
    }

    // ==================== deleteIndex ====================

    @Test
    void deleteIndex_shouldCallFtDropIndex() {
        when(jedisPooled.ftDropIndex("del-index")).thenReturn("OK");

        assertDoesNotThrow(() -> vectorService.deleteIndex("del-index"));
        verify(jedisPooled).ftDropIndex("del-index");
    }

    @Test
    void deleteIndex_whenNotRedisVectorStore_shouldThrow() {
        VectorStore nonRedisStore = mock(VectorStore.class);
        RedisVectorServiceImpl otherService = new RedisVectorServiceImpl(nonRedisStore, embeddingModel);

        assertThrows(UnsupportedOperationException.class,
                () -> otherService.deleteIndex("idx"));
    }

    @Test
    void deleteIndex_whenNativeClientEmpty_shouldThrow() {
        when(redisVectorStore.getNativeClient()).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class,
                () -> vectorService.deleteIndex("idx"));
    }

    @Test
    void deleteIndex_whenFtDropIndexThrows_shouldWrapInRuntimeException() {
        when(jedisPooled.ftDropIndex(anyString()))
                .thenThrow(new RuntimeException("FT.DROPINDEX failed"));

        assertThrows(RuntimeException.class,
                () -> vectorService.deleteIndex("idx"));
    }

    // ==================== indexExists ====================

    @Test
    void indexExists_whenIndexInList_shouldReturnTrue() {
        when(jedisPooled.ftList()).thenReturn(Set.of("idx1", "target-index", "idx3"));

        boolean exists = vectorService.indexExists("target-index");

        assertTrue(exists);
    }

    @Test
    void indexExists_whenIndexNotInList_shouldReturnFalse() {
        when(jedisPooled.ftList()).thenReturn(Set.of("idx1", "idx2"));

        boolean exists = vectorService.indexExists("nonexistent");

        assertFalse(exists);
    }

    @Test
    void indexExists_whenNotRedisVectorStore_shouldReturnFalse() {
        VectorStore nonRedisStore = mock(VectorStore.class);
        RedisVectorServiceImpl otherService = new RedisVectorServiceImpl(nonRedisStore, embeddingModel);

        assertFalse(otherService.indexExists("any-index"));
    }

    @Test
    void indexExists_whenNativeClientEmpty_shouldReturnFalse() {
        when(redisVectorStore.getNativeClient()).thenReturn(Optional.empty());

        assertFalse(vectorService.indexExists("idx"));
    }

    @Test
    void indexExists_whenFtListThrows_shouldReturnFalse() {
        when(jedisPooled.ftList()).thenThrow(new RuntimeException("FT.LIST failed"));

        boolean exists = vectorService.indexExists("idx");

        assertFalse(exists);
    }

    // ==================== getIndexConfig ====================

    @Test
    void getIndexConfig_shouldReturnConfigWithNumDocs() {
        when(jedisPooled.ftInfo("test-index"))
                .thenReturn(Map.of("num_docs", 42L));

        VectorProperties.IndexConfig config = vectorService.getIndexConfig("test-index");

        assertNotNull(config);
        assertEquals("test-index", config.getName());
        assertThat(config.getAdditionalFields()).containsEntry("numDocs", 42L);
    }

    @Test
    void getIndexConfig_whenNotRedisVectorStore_shouldThrow() {
        VectorStore nonRedisStore = mock(VectorStore.class);
        RedisVectorServiceImpl otherService = new RedisVectorServiceImpl(nonRedisStore, embeddingModel);

        assertThrows(UnsupportedOperationException.class,
                () -> otherService.getIndexConfig("idx"));
    }

    @Test
    void getIndexConfig_whenNativeClientEmpty_shouldThrow() {
        when(redisVectorStore.getNativeClient()).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class,
                () -> vectorService.getIndexConfig("idx"));
    }

    @Test
    void getIndexConfig_whenFtInfoThrows_shouldWrapInRuntimeException() {
        when(jedisPooled.ftInfo(anyString()))
                .thenThrow(new RuntimeException("FT.INFO failed"));

        assertThrows(RuntimeException.class,
                () -> vectorService.getIndexConfig("idx"));
    }

    // ==================== countDocuments ====================

    @Test
    void countDocuments_whenNumDocsIsLong_shouldReturnLongValue() {
        when(jedisPooled.ftInfo("idx")).thenReturn(Map.of("num_docs", 123L));

        long count = vectorService.countDocuments("idx");

        assertEquals(123L, count);
    }

    @Test
    void countDocuments_whenNumDocsIsInteger_shouldConvertToLong() {
        when(jedisPooled.ftInfo("idx")).thenReturn(Map.of("num_docs", 456));

        long count = vectorService.countDocuments("idx");

        assertEquals(456L, count);
    }

    @Test
    void countDocuments_whenNumDocsMissing_shouldReturnZero() {
        when(jedisPooled.ftInfo("idx")).thenReturn(Map.of());

        long count = vectorService.countDocuments("idx");

        assertEquals(0L, count);
    }

    @Test
    void countDocuments_whenNotRedisVectorStore_shouldThrow() {
        VectorStore nonRedisStore = mock(VectorStore.class);
        RedisVectorServiceImpl otherService = new RedisVectorServiceImpl(nonRedisStore, embeddingModel);

        assertThrows(UnsupportedOperationException.class,
                () -> otherService.countDocuments("idx"));
    }

    @Test
    void countDocuments_whenNativeClientEmpty_shouldThrow() {
        when(redisVectorStore.getNativeClient()).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class,
                () -> vectorService.countDocuments("idx"));
    }

    @Test
    void countDocuments_whenFtInfoThrows_shouldReturnZero() {
        when(jedisPooled.ftInfo(anyString())).thenThrow(new RuntimeException("FT.INFO failed"));

        long count = vectorService.countDocuments("idx");

        assertEquals(0L, count);
    }

    // ==================== listDocumentsHandler / parseJsonToDocument ====================

    @Test
    void listDocumentsHandler_withEmptyScan_shouldReturnEmptyList() {
        ScanResult<String> scanResult = mock(ScanResult.class);
        when(scanResult.getResult()).thenReturn(List.of());
        when(scanResult.isCompleteIteration()).thenReturn(true);
        when(jedisPooled.scan(anyString(), any(ScanParams.class)))
                .thenReturn(scanResult);

        List<VectorDocument> docs = vectorService.listDocuments("empty-index", 0, 10);

        assertThat(docs).isEmpty();
    }

    @Test
    void listDocumentsHandler_withPagination_shouldSliceCorrectly() {
        String prefix = "paged:";
        ScanResult<String> scanResult = mock(ScanResult.class);
        List<String> allKeys = java.util.stream.IntStream.range(0, 10)
                .mapToObj(i -> prefix + "doc" + i)
                .toList();
        when(scanResult.getResult()).thenReturn(allKeys);
        when(scanResult.isCompleteIteration()).thenReturn(true);
        when(jedisPooled.scan(anyString(), any(ScanParams.class)))
                .thenReturn(scanResult);

        // jsonMGet must return 3 results matching the batch size (doc5, doc6, doc7)
        List<JSONArray> jsonResults = new ArrayList<>();
        jsonResults.add(new JSONArray());
        jsonResults.add(new JSONArray());
        jsonResults.add(new JSONArray());
        doReturn(jsonResults).when(jedisPooled).jsonMGet(any(Path2.class), any(String[].class));

        List<VectorDocument> docs = vectorService.listDocuments("paged", 5, 3);

        // Slice should return 3 docs with IDs doc5, doc6, doc7
        assertThat(docs).hasSize(3);
        assertThat(docs.get(0).getId()).isEqualTo("doc5");
        assertThat(docs.get(1).getId()).isEqualTo("doc6");
        assertThat(docs.get(2).getId()).isEqualTo("doc7");
    }

    @Test
    void listDocumentsHandler_withNullJsonResult_shouldSkipNullEntries() {
        String prefix = "partial:";
        ScanResult<String> scanResult = mock(ScanResult.class);
        when(scanResult.getResult()).thenReturn(List.of(prefix + "a", prefix + "b", prefix + "c"));
        when(scanResult.isCompleteIteration()).thenReturn(true);
        when(jedisPooled.scan(anyString(), any(ScanParams.class)))
                .thenReturn(scanResult);

        // jsonMGet returns null for middle entry
        List<JSONArray> jsonResults = new ArrayList<>();
        jsonResults.add(null); // null item - should be skipped
        jsonResults.add(null);
        jsonResults.add(null);
        doReturn(jsonResults).when(jedisPooled).jsonMGet(any(Path2.class), any(String[].class));

        List<VectorDocument> docs = vectorService.listDocuments("partial", 0, 10);

        // All null entries should be skipped
        assertThat(docs).isEmpty();
    }

    @Test
    void listDocumentsHandler_withEmptyJsonArray_shouldReturnEmpty() {
        String prefix = "empty:";
        ScanResult<String> scanResult = mock(ScanResult.class);
        when(scanResult.getResult()).thenReturn(List.of(prefix + "doc1"));
        when(scanResult.isCompleteIteration()).thenReturn(true);
        when(jedisPooled.scan(anyString(), any(ScanParams.class)))
                .thenReturn(scanResult);

        // Empty JSONArray (no elements)
        JSONArray emptyArray = new JSONArray();
        doReturn(List.of(emptyArray)).when(jedisPooled).jsonMGet(any(Path2.class), any(String[].class));

        List<VectorDocument> docs = vectorService.listDocuments("empty", 0, 10);

        // Empty array means no content extracted, but ID should still be set
        assertThat(docs).hasSize(1);
        assertThat(docs.getFirst().getId()).isEqualTo("doc1");
        assertThat(docs.getFirst().getContent()).isNull();
    }

    @Test
    void listDocumentsHandler_whenNotRedisVectorStore_shouldThrow() {
        VectorStore nonRedisStore = mock(VectorStore.class);
        RedisVectorServiceImpl otherService = new RedisVectorServiceImpl(nonRedisStore, embeddingModel);

        assertThrows(UnsupportedOperationException.class,
                () -> otherService.listDocuments("idx", 0, 10));
    }

    @Test
    void listDocumentsHandler_whenNativeClientEmpty_shouldThrow() {
        when(redisVectorStore.getNativeClient()).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class,
                () -> vectorService.listDocuments("idx", 0, 10));
    }

    @Test
    void listDocumentsHandler_whenScanThrows_shouldWrapInRuntimeException() {
        when(jedisPooled.scan(anyString(), any(ScanParams.class)))
                .thenThrow(new RuntimeException("SCAN failed"));

        assertThrows(RuntimeException.class,
                () -> vectorService.listDocuments("idx", 0, 10));
    }

    @Test
    void listDocumentsHandler_withOffsetBeyondSize_shouldReturnEmpty() {
        String prefix = "small:";
        ScanResult<String> scanResult = mock(ScanResult.class);
        when(scanResult.getResult()).thenReturn(List.of(prefix + "a", prefix + "b"));
        when(scanResult.isCompleteIteration()).thenReturn(true);
        when(jedisPooled.scan(anyString(), any(ScanParams.class)))
                .thenReturn(scanResult);

        List<VectorDocument> docs = vectorService.listDocuments("small", 10, 5);

        assertThat(docs).isEmpty();
    }

    // ==================== listDocuments (public final, delegates to handler) ====================

    @Test
    void listDocuments_zeroLimit_shouldReturnEmpty() {
        assertThat(vectorService.listDocuments("idx", 0, 0)).isEmpty();
    }

    @Test
    void listDocuments_capsLimitAtMaxListLimit() {
        String prefix = "caps:";
        ScanResult<String> scanResult = mock(ScanResult.class);
        when(scanResult.getResult()).thenReturn(List.of(prefix + "a"));
        when(scanResult.isCompleteIteration()).thenReturn(true);
        when(jedisPooled.scan(anyString(), any(ScanParams.class)))
                .thenReturn(scanResult);
        // Return a non-empty list so we can verify cap works
        doReturn(List.of(new JSONArray())).when(jedisPooled).jsonMGet(any(Path2.class), any(String[].class));

        List<VectorDocument> docs = vectorService.listDocuments("caps", 0, 5000);

        // Should still return result (limit capped but query executed)
        assertThat(docs).isNotEmpty();
    }
}
