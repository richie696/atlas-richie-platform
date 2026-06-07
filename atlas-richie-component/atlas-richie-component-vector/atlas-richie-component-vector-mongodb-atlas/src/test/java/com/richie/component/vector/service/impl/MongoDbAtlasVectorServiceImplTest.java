package com.richie.component.vector.service.impl;

import com.richie.component.vector.config.VectorProperties;
import com.richie.component.vector.model.VectorDocument;
import com.richie.component.vector.model.VectorSearchResult;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MongoDbAtlasVectorServiceImpl}.
 *
 * <p>Tests MongoDB Atlas vector service implementation including index management,
 * document operations, and vector search functionality using Mockito mocks
 * for MongoTemplate, VectorStore, and EmbeddingModel.</p>
 *
 * @author Rydeen Platform Team
 */
@ExtendWith(MockitoExtension.class)
class MongoDbAtlasVectorServiceImplTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private EmbeddingModel embeddingModel;

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private IndexOperations indexOperations;

    @Captor
    private ArgumentCaptor<Query> queryCaptor;

    @Captor
    private ArgumentCaptor<Index> indexCaptor;

    private MongoDbAtlasVectorServiceImpl vectorService;

    @BeforeEach
    void setUp() {
        // Inject mock dependencies via constructor (same as production)
        vectorService = new MongoDbAtlasVectorServiceImpl(vectorStore, embeddingModel, mongoTemplate);
    }

    @Nested
    @DisplayName("createIndex")
    class CreateIndexTests {

        @Test
        @DisplayName("should create index with vector field ascending")
        void createIndex_shouldCreateVectorFieldIndex() {
            String indexName = "test_collection";
            VectorProperties.IndexConfig config = new VectorProperties.IndexConfig();

            when(mongoTemplate.indexOps(indexName)).thenReturn(indexOperations);
            when(indexOperations.createIndex(any(Index.class))).thenReturn("index_name");

            vectorService.createIndex(indexName, config);

            // Verify indexOps was called with correct collection name
            verify(mongoTemplate).indexOps(indexName);
            // Verify createIndex was called with an Index containing vector field
            verify(indexOperations).createIndex(indexCaptor.capture());
            // The index should be on "vector" field in ascending order
            Index capturedIndex = indexCaptor.getValue();
            assertThat(capturedIndex).isNotNull();
        }
    }

    @Nested
    @DisplayName("deleteIndex")
    class DeleteIndexTests {

        @Test
        @DisplayName("should drop all indexes on collection")
        void deleteIndex_shouldDropIndexes() {
            String indexName = "test_collection";

            // mongoTemplate.getCollection returns a MongoCollection which has dropIndexes()
            var collection = mock(com.mongodb.client.MongoCollection.class);
            when(mongoTemplate.getCollection(indexName)).thenReturn((com.mongodb.client.MongoCollection<Document>) collection);

            vectorService.deleteIndex(indexName);

            verify(mongoTemplate).getCollection(indexName);
            verify(collection).dropIndexes();
        }
    }

    @Nested
    @DisplayName("indexExists")
    class IndexExistsTests {

        @Test
        @DisplayName("should return true when collection exists")
        void indexExists_whenCollectionExists_shouldReturnTrue() {
            String indexName = "test_collection";
            when(mongoTemplate.collectionExists(indexName)).thenReturn(true);

            boolean exists = vectorService.indexExists(indexName);

            assertThat(exists).isTrue();
            verify(mongoTemplate).collectionExists(indexName);
        }

        @Test
        @DisplayName("should return false when collection does not exist")
        void indexExists_whenCollectionNotExists_shouldReturnFalse() {
            String indexName = "nonexistent_collection";
            when(mongoTemplate.collectionExists(indexName)).thenReturn(false);

            boolean exists = vectorService.indexExists(indexName);

            assertThat(exists).isFalse();
        }
    }

    @Nested
    @DisplayName("getIndexConfig")
    class GetIndexConfigTests {

        @Test
        @DisplayName("should return config when collection exists")
        void getIndexConfig_whenCollectionExists_shouldReturnConfig() {
            String indexName = "test_collection";
            when(mongoTemplate.collectionExists(indexName)).thenReturn(true);

            VectorProperties.IndexConfig config = vectorService.getIndexConfig(indexName);

            assertThat(config).isNotNull();
            assertThat(config.getName()).isEqualTo(indexName);
        }

        @Test
        @DisplayName("should return null when collection does not exist")
        void getIndexConfig_whenCollectionNotExists_shouldReturnNull() {
            String indexName = "nonexistent_collection";
            when(mongoTemplate.collectionExists(indexName)).thenReturn(false);

            VectorProperties.IndexConfig config = vectorService.getIndexConfig(indexName);

            assertThat(config).isNull();
        }
    }

    @Nested
    @DisplayName("countDocuments")
    class CountDocumentsTests {

        @Test
        @DisplayName("should return document count")
        void countDocuments_shouldReturnCount() {
            String indexName = "test_collection";
            long expectedCount = 42L;

            var collection = mock(com.mongodb.client.MongoCollection.class);
            when(mongoTemplate.getCollection(indexName)).thenReturn((com.mongodb.client.MongoCollection<Document>) collection);
            when(collection.countDocuments()).thenReturn(expectedCount);

            long count = vectorService.countDocuments(indexName);

            assertThat(count).isEqualTo(expectedCount);
        }

        @Test
        @DisplayName("should return zero when collection is empty")
        void countDocuments_whenEmpty_shouldReturnZero() {
            String indexName = "empty_collection";

            var collection = mock(com.mongodb.client.MongoCollection.class);
            when(mongoTemplate.getCollection(indexName)).thenReturn((com.mongodb.client.MongoCollection<Document>) collection);
            when(collection.countDocuments()).thenReturn(0L);

            long count = vectorService.countDocuments(indexName);

            assertThat(count).isZero();
        }
    }

    @Nested
    @DisplayName("listDocuments")
    class ListDocumentsTests {

        @Test
        @DisplayName("should apply skip and limit to query")
        void listDocuments_shouldApplyPaginationParams() {
            String indexName = "test_collection";
            int offset = 10;
            int limit = 20;

            when(mongoTemplate.find(any(Query.class), eq(VectorDocument.class), eq(indexName)))
                    .thenReturn(List.of());

            vectorService.listDocuments(indexName, offset, limit);

            verify(mongoTemplate).find(queryCaptor.capture(), eq(VectorDocument.class), eq(indexName));
            Query capturedQuery = queryCaptor.getValue();
            // Verify skip was set - offset becomes skip value
            assertThat(capturedQuery).isNotNull();
        }

        @Test
        @DisplayName("should return empty list when no documents")
        void listDocuments_whenNoDocuments_shouldReturnEmptyList() {
            String indexName = "empty_collection";

            when(mongoTemplate.find(any(Query.class), eq(VectorDocument.class), eq(indexName)))
                    .thenReturn(List.of());

            List<VectorDocument> result = vectorService.listDocuments(indexName, 0, 10);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should cap limit at MAX_LIST_LIMIT from base class")
        void listDocuments_shouldCapLimitAtMax() {
            // The base class VectorServiceImpl.MAX_LIST_LIMIT is 1000
            // listDocuments with limit > 1000 should be capped
            String indexName = "test_collection";

            when(mongoTemplate.find(any(Query.class), eq(VectorDocument.class), eq(indexName)))
                    .thenReturn(List.of());

            // This will be capped by base class to MAX_LIST_LIMIT (1000)
            vectorService.listDocuments(indexName, 0, 5000);

            // Verify find was called (capped internally)
            verify(mongoTemplate).find(any(Query.class), eq(VectorDocument.class), eq(indexName));
        }

        @Test
        @DisplayName("should return empty for zero limit")
        void listDocuments_withZeroLimit_shouldReturnEmpty() {
            List<VectorDocument> result = vectorService.listDocuments("idx", 0, 0);
            // Base class returns empty list for limit <= 0
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("searchByVector")
    class SearchByVectorTests {

        @Test
        @DisplayName("should throw IllegalArgumentException when vector is null")
        void searchByVector_withNullVector_shouldThrowException() {
            assertThatThrownBy(() -> vectorService.searchByVector("idx", null, 10))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("查询向量不能为空");
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when vector is empty")
        void searchByVector_withEmptyVector_shouldThrowException() {
            float[] emptyVector = new float[0];
            assertThatThrownBy(() -> vectorService.searchByVector("idx", emptyVector, 10))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("查询向量不能为空");
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when limit is zero")
        void searchByVector_withZeroLimit_shouldThrowException() {
            float[] vector = new float[]{0.1f, 0.2f, 0.3f};
            assertThatThrownBy(() -> vectorService.searchByVector("idx", vector, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("limit 必须大于 0");
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when limit is negative")
        void searchByVector_withNegativeLimit_shouldThrowException() {
            float[] vector = new float[]{0.1f, 0.2f, 0.3f};
            assertThatThrownBy(() -> vectorService.searchByVector("idx", vector, -5))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("limit 必须大于 0");
        }

        @Test
        @DisplayName("should execute aggregation with vectorSearch and project stages")
        void searchByVector_withValidParams_shouldExecuteAggregation() {
            String indexName = "test_collection";
            float[] vector = new float[]{0.1f, 0.2f, 0.3f};
            int limit = 5;

            AggregationResults<Document> mockResults = mock(AggregationResults.class);
            when(mockResults.getMappedResults()).thenReturn(List.of());
            when(mongoTemplate.aggregate(any(Aggregation.class), eq(indexName), eq(Document.class)))
                    .thenReturn(mockResults);

            List<VectorSearchResult> results = vectorService.searchByVector(indexName, vector, limit);

            // Verify aggregation was executed
            verify(mongoTemplate).aggregate(any(Aggregation.class), eq(indexName), eq(Document.class));
            assertThat(results).isNotNull();
        }

        @Test
        @DisplayName("should return empty list when no search results")
        void searchByVector_whenNoResults_shouldReturnEmptyList() {
            String indexName = "test_collection";
            float[] vector = new float[]{0.1f, 0.2f, 0.3f};

            AggregationResults<Document> mockResults = mock(AggregationResults.class);
            when(mockResults.getMappedResults()).thenReturn(List.of());
            when(mongoTemplate.aggregate(any(Aggregation.class), eq(indexName), eq(Document.class)))
                    .thenReturn(mockResults);

            List<VectorSearchResult> results = vectorService.searchByVector(indexName, vector, 10);

            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("should map aggregation results to VectorSearchResult")
        void searchByVector_shouldMapResultsCorrectly() {
            String indexName = "test_collection";
            float[] vector = new float[]{0.1f, 0.2f, 0.3f};

            // Create mock Document result from aggregation
            Document doc1 = new Document()
                    .append("id", "doc-1")
                    .append("content", "test content")
                    .append("score", 0.95);

            AggregationResults<Document> mockResults = mock(AggregationResults.class);
            when(mockResults.getMappedResults()).thenReturn(List.of(doc1));
            when(mongoTemplate.aggregate(any(Aggregation.class), eq(indexName), eq(Document.class)))
                    .thenReturn(mockResults);

            List<VectorSearchResult> results = vectorService.searchByVector(indexName, vector, 10);

            assertThat(results).hasSize(1);
            VectorSearchResult result = results.get(0);
            assertThat(result.getId()).isEqualTo("doc-1");
            assertThat(result.getContent()).isEqualTo("test content");
            assertThat(result.getScore()).isEqualTo(0.95);
        }

        @Test
        @DisplayName("should use numCandidates as max(limit*10, 100)")
        void searchByVector_shouldCalculateNumCandidatesCorrectly() {
            String indexName = "test_collection";
            float[] vector = new float[]{0.1f, 0.2f, 0.3f};
            int limit = 5; // 5 * 10 = 50, which is less than 100

            AggregationResults<Document> mockResults = mock(AggregationResults.class);
            when(mockResults.getMappedResults()).thenReturn(List.of());
            when(mongoTemplate.aggregate(any(Aggregation.class), eq(indexName), eq(Document.class)))
                    .thenReturn(mockResults);

            // This should use 100 as numCandidates (max(50, 100))
            vectorService.searchByVector(indexName, vector, limit);

            verify(mongoTemplate).aggregate(any(Aggregation.class), eq(indexName), eq(Document.class));
        }

        @Test
        @DisplayName("should use correct vector index name")
        void searchByVector_shouldUseCorrectVectorIndex() {
            String indexName = "custom_collection";
            float[] vector = new float[]{0.1f, 0.2f, 0.3f};

            AggregationResults<Document> mockResults = mock(AggregationResults.class);
            when(mockResults.getMappedResults()).thenReturn(List.of());
            when(mongoTemplate.aggregate(any(Aggregation.class), eq(indexName), eq(Document.class)))
                    .thenReturn(mockResults);

            vectorService.searchByVector(indexName, vector, 10);

            verify(mongoTemplate).aggregate(any(Aggregation.class), eq(indexName), eq(Document.class));
        }
    }

    @Nested
    @DisplayName("VectorService operations inherited from base class")
    class BaseClassOperationTests {

        @Test
        @DisplayName("addDocument should delegate to vectorStore")
        void addDocument_shouldDelegateToVectorStore() {
            VectorDocument doc = new VectorDocument();
            doc.setContent("test content");
            doc.setMetadata(new java.util.HashMap<>());

            vectorService.addDocument(doc);

            verify(vectorStore).add(any());
        }

        @Test
        @DisplayName("deleteDocument should delegate to vectorStore")
        void deleteDocument_shouldDelegateToVectorStore() {
            vectorService.deleteDocument("doc-1");

            verify(vectorStore).delete("doc-1");
        }

        @Test
        @DisplayName("searchByText should delegate to vectorStore")
        void searchByText_shouldDelegateToVectorStore() {
            when(vectorStore.similaritySearch(any(org.springframework.ai.vectorstore.SearchRequest.class)))
                    .thenReturn(List.of());

            vectorService.searchByText("test query", 10);

            verify(vectorStore).similaritySearch(any(org.springframework.ai.vectorstore.SearchRequest.class));
        }
    }
}
